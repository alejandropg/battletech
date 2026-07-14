package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.withUnit
import battletech.tactical.session.AttackImpulseCommand
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.Impulse
import battletech.tactical.session.Initiative
import battletech.tactical.session.PhaseHandler
import battletech.tactical.session.PhaseOutcome
import battletech.tactical.session.RuleRejection
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnState
import battletech.tactical.session.calculateAttackOrder
import battletech.tactical.unit.GYRO_DESTROYED_AT
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.basePsrModifier
import battletech.tactical.unit.gyroCritCount

/**
 * Base for attack phases driven by an alternating impulse sequence
 * (weapon fire, physical attacks). On entry (re-)seeds [AttackProgress.sequence]
 * from initiative; the re-seed-when-complete guard lets the physical phase
 * reuse the sequence the weapon phase finished. Stateless.
 */
public abstract class ImpulseAttackPhaseHandler : PhaseHandler {

    /** Return true if [command] is the concrete command type this handler processes. */
    protected abstract fun acceptsCommand(command: GameCommand): Boolean

    final override fun activePlayer(turn: TurnState): PlayerId? =
        if (!turn.attack.inProgress) null
        else turn.attack.activePlayer

    final override fun accepts(command: GameCommand, turn: TurnState): Boolean =
        acceptsCommand(command) && turn.attack.inProgress

    final override fun isComplete(turn: TurnState): Boolean =
        turn.attack.sequence.order.isNotEmpty() && turn.attack.isComplete

    final override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        if (turn.attack.inProgress) {
            return PhaseOutcome(state, turn, emptyList())
        }
        val newTurn = turn.copy(attack = turn.attack.seed(attackOrderFor(turn.initiative, state)))
        return PhaseOutcome(state, newTurn, emptyList())
    }

    /**
     * Applies the subset of [facings] that actually differ from each unit's current torso
     * facing, appending a [TorsoFacingsApplied] event for those changes to [events].
     * Declared-but-unchanged facings are dropped silently. Returns the (possibly updated) state.
     */
    protected fun applyTorsoFacingsStep(
        state: GameState,
        facings: Map<UnitId, HexDirection>,
        events: MutableList<GameEvent>,
    ): GameState {
        val changed = facings.filter { (unitId, facing) -> state.unitById(unitId).torsoFacing != facing }
        if (changed.isEmpty()) return state
        events += TorsoFacingsApplied(changed)
        return state.applyTorsoFacings(changed)
    }

    /**
     * Gyro-crit fall effects (`docs/rules/armor-damage.md` §3), applied right after this
     * impulse's crit resolution by comparing [before] (pre-crit) against [after]
     * (post-crit) snapshots, per unit, in unit-id order:
     *
     *  - **1st gyro crit** (count 0 → 1): the unit must immediately make a PSR (+3,
     *    [gyroPsrModifier] + [legPsrModifier]) "at the end of the current phase"; on
     *    failure it falls and the pilot takes 1 hit ([forcePsrOrFall]).
     *  - **2nd gyro crit** (count crosses to [GYRO_DESTROYED_AT]): the gyro is shattered
     *    and the unit crashes to the ground instantly — an automatic fall with NO PSR,
     *    but the pilot still takes 1 hit ([forcedFall]).
     *    It is NOT eliminated (still fights prone) and can never stand again (enforced by
     *    [battletech.tactical.movement.MovementPhaseHandler] via [gyroCritCount]).
     *
     * Stage 5 applies this within the same `apply()` call rather than deferring to literal
     * end-of-phase. A unit already prone takes no further fall.
     *
     * **Canonical dice order** per falling unit:
     *  - Gyro crash (automatic): fall location 2d6 + facing 1d6 + consciousness 2d6
     *  - First gyro crit (PSR): PSR 2d6, then (on failure) fall location 2d6 + facing
     *    1d6 + consciousness 2d6
     *
     * Returns the updated state and the combined [UnitFell] + pilot-hit events.
     */
    protected fun applyGyroCritEffects(
        before: GameState,
        after: GameState,
        roller: DiceRoller,
    ): Pair<GameState, List<GameEvent>> {
        var state = after
        val events = mutableListOf<GameEvent>()
        for (afterUnit in after.units) {
            val beforeUnit = before.unitById(afterUnit.id)
            val beforeCrits = beforeUnit.gyroCritCount()
            val afterCrits = afterUnit.gyroCritCount()
            if (afterUnit.isProne) continue

            val crashes = beforeCrits < GYRO_DESTROYED_AT && afterCrits >= GYRO_DESTROYED_AT
            val tookFirstGyroCrit = beforeCrits == 0 && afterCrits == 1

            if (!crashes && !tookFirstGyroCrit) continue

            val (updatedUnit, fallEvents) = if (crashes) {
                // Gyro shattered: automatic crash, no PSR. Pilot still takes 1 hit from the fall.
                forcedFall(afterUnit, roller)
            } else {
                // First gyro crit: PSR at modifier = gyro penalty + leg penalty; fall + pilot hit on failure.
                forcePsrOrFall(afterUnit, modifier = afterUnit.basePsrModifier(), roller)
            }

            if (fallEvents.isNotEmpty()) {
                state = state.withUnit(updatedUnit)
                events += fallEvents
            }
        }
        return state to events
    }

    /**
     * Owner/friendly/destroyed triple shared by [battletech.tactical.attack.weapon.WeaponAttackPhaseHandler]
     * and [battletech.tactical.attack.physical.PhysicalAttackPhaseHandler]'s per-declaration validation:
     *
     *  1. [attackerId] is owned by [playerId] ([CommandRejection.NotYourUnit] — a per-unit ownership
     *     check, not a repeat of the session's active-player check; see [PhaseHandler.validate]).
     *  2. [targetId] is not friendly ([CommandRejection.FriendlyFire]).
     *  3. [targetId] is not already destroyed ([RuleRejection.TargetDestroyed]).
     *
     * Returns the first violation, or null if all three pass. Callers insert any phase-specific
     * checks (weapon-index bounds, tactical legality) around this — see each handler's `validate`.
     */
    protected fun validateTarget(
        attackerId: UnitId,
        targetId: UnitId,
        playerId: PlayerId,
        state: GameState,
    ): CommandRejection? {
        val attacker = state.unitById(attackerId)
        if (attacker.owner != playerId) {
            return CommandRejection.NotYourUnit(attackerId, owner = attacker.owner, attemptedBy = playerId)
        }

        val target = state.unitById(targetId)
        if (target.owner == attacker.owner) {
            return CommandRejection.FriendlyFire(targetId)
        }
        if (target.isDestroyed) {
            return CommandRejection.RuleViolation(RuleRejection.TargetDestroyed)
        }
        return null
    }

    /**
     * Validates every torso-facing entry in [cmd]: the unit exists and is owned by
     * [AttackImpulseCommand.playerId] ([CommandRejection.NotYourUnit]), and the requested facing
     * is a legal ±1 twist from the unit's leg facing ([CommandRejection.IllegalTorsoTwist]).
     * Shared by both impulse-commit handlers — the ±1 twist rule doesn't vary by phase.
     */
    protected fun validateTorsoFacings(cmd: AttackImpulseCommand, state: GameState): CommandRejection? {
        for ((unitId, newFacing) in cmd.torsoFacings) {
            val unit = state.unitById(unitId)

            if (unit.owner != cmd.playerId) {
                return CommandRejection.NotYourUnit(unitId, owner = unit.owner, attemptedBy = cmd.playerId)
            }
            if (newFacing !in torsoTwistOptions(unit.facing)) {
                return CommandRejection.IllegalTorsoTwist(unitId, newFacing)
            }
        }
        return null
    }

    /**
     * The post-volley tail shared by both impulse-commit handlers, run in this exact order
     * (dice-order-sensitive — a pure move from the two former `apply` bodies, not a behavior
     * change):
     *
     *  1. [applyGyroCritEffects] — gyro-crit falls, comparing [before] against [resolved].
     *  2. [applyLocationDestructionConsequences] — weapon disable / side-torso-arm cascade /
     *     leg-destruction falls, comparing [before] against the gyro-effects state.
     *  3. Group [hits]' damage by target (armor + structure).
     *  4. [applyTwentyDamagePsrs] — any target at ≥20 cumulative damage this volley makes a PSR.
     *
     * [before] is the state snapshot from *before* this volley's damage/crit resolution ran;
     * [resolved] is the state immediately after that resolution (before this tail's own steps).
     * [hits] is the volley's resolved hits (misses excluded — they carry no damage), already
     * narrowed to [ResolvedAttack] by the caller via `filterIsInstance`.
     */
    protected fun resolveVolleyTail(
        before: GameState,
        resolved: GameState,
        hits: List<ResolvedAttack>,
        roller: DiceRoller,
    ): Pair<GameState, List<GameEvent>> {
        val events = mutableListOf<GameEvent>()

        val (stateAfterGyro, gyroEvents) = applyGyroCritEffects(before, resolved, roller)
        var state = stateAfterGyro
        events += gyroEvents

        // Apply location-destruction consequences (weapon disable, side-torso → arm cascade,
        // leg-destruction falls) for all locations whose IS newly reached 0 during this volley
        // (including crit-blown-off limbs detected via IS comparison).
        val (stateAfterDestruction, destructionEvents) = applyLocationDestructionConsequences(before, state, roller)
        state = stateAfterDestruction
        events += destructionEvents

        // 20-damage PSR: any target that took ≥20 total damage this phase must make a PSR
        // (+1 per full 20, including gyro/leg modifiers). Fail → forced fall + pilot hit.
        val damageByUnit = hits
            .groupBy { it.targetId }
            .mapValues { (_, rs) -> rs.sumOf { r -> r.damage.sumOf { s -> s.armorDamage + s.structureDamage } } }
        val (stateAfter20dmg, twentyDmgEvents) = applyTwentyDamagePsrs(state, damageByUnit, roller)
        state = stateAfter20dmg
        events += twentyDmgEvents

        return state to events
    }
}

internal fun attackOrderFor(initiative: Initiative, state: GameState): List<Impulse> {
    val loser = initiative.loser
    val winner = initiative.winner
    // Shutdown 'Mechs can't fire, so they don't take an impulse slot.
    return calculateAttackOrder(
        loser = loser,
        loserUnitCount = state.activeUnitsOf(loser).size,
        winner = winner,
        winnerUnitCount = state.activeUnitsOf(winner).size,
    )
}
