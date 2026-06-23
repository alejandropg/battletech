package battletech.tactical.session

import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.applyPilotHit
import battletech.tactical.attack.attemptConsciousnessRecovery
import battletech.tactical.attack.detonateAmmoBin
import battletech.tactical.dice.DiceRoller
import battletech.tactical.heat.HeatScale
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.CriticalComponent
import battletech.tactical.unit.CritEffect
import battletech.tactical.unit.PILOT_DEATH_THRESHOLD
import battletech.tactical.unit.critEffects

/**
 * System phase. On entry, folds each unit's heat generated this turn into its
 * standing heat and dissipates ([GameState.applyHeatPhase]), then walks the
 * units in state order rolling, per unit, a fixed sequence of per-unit effects —
 * each gated so it only consumes dice when the unit's state actually warrants it,
 * keeping untouched fixtures dice-free:
 *
 *  1. [resolvePower] — shutdown/restart avoidance (heat-keyed).
 *  2. [resolveLifeSupportPilotHit] — Stage 7 life-support pilot damage
 *     (`docs/rules/armor-damage.md` §3 Life Support), evaluated against *this
 *     turn's* post-fold heat. Ordered before consciousness recovery because a
 *     pilot knocked out by life support this turn should not also attempt a
 *     recovery roll in the same Heat Phase (the recovery in step 3 is reserved
 *     for pilots who were *already* unconscious coming into this phase).
 *  3. [resolveConsciousnessRecovery] — Stage 7 recovery attempt for a pilot who
 *     was already unconscious before this phase ran.
 *  4. [resolveAmmoExplosion] — heat-driven ammo cook-off (last, since it can
 *     itself wound the pilot in a later stage and we want LS/recovery settled
 *     first against this turn's heat).
 *
 * Completes immediately. Accepts no commands.
 */
public class HeatPhaseHandler : PhaseHandler {

    override val phase: TurnPhase = TurnPhase.HEAT

    override fun activePlayer(turn: TurnState): PlayerId? = null

    override fun accepts(command: GameCommand, turn: TurnState): Boolean = false

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome = PhaseOutcome(state, turn, emptyList())

    override fun isComplete(turn: TurnState): Boolean = true

    override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val before = state.units.associate { it.id to it.currentHeat }
        val folded = state.applyHeatPhase()
        val after = folded.units.associate { it.id to it.currentHeat }
        val events = mutableListOf<GameEvent>(HeatDissipated(before, after))

        val processedUnits = folded.units.map { unit ->
            // Captured before any of this turn's resolution steps mutate consciousness,
            // so the recovery step below can tell "was already unconscious coming into
            // this phase" apart from "knocked out by life support just now".
            val wasUnconsciousBeforePhase = !unit.isPilotConscious

            var working = unit
            val (afterPower, powerEvent) = resolvePower(working, roller)
            powerEvent?.let { events += it }
            working = afterPower

            val (afterLifeSupport, lifeSupportEvents) = resolveLifeSupportPilotHit(working, roller)
            events += lifeSupportEvents
            working = afterLifeSupport

            val (afterRecovery, recoveryEvent) =
                resolveConsciousnessRecovery(working, wasUnconsciousBeforePhase, roller)
            recoveryEvent?.let { events += it }
            working = afterRecovery

            val (afterAmmo, ammoEvent) = resolveAmmoExplosion(working, roller)
            ammoEvent?.let { events += it }
            afterAmmo
        }

        return PhaseOutcome(folded.copy(units = processedUnits), turn, events)
    }

    /** Shutdown (for an operational unit) or startup (for a shut-down unit). */
    private fun resolvePower(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, GameEvent?> {
        val heat = unit.currentHeat
        return if (unit.isShutdown) {
            when {
                HeatScale.isAutoShutdown(heat) -> unit to null // pinned down, no restart
                HeatScale.shutdownAvoidTarget(heat) == null ->
                    unit.copy(isShutdown = false) to UnitRestarted(unit.id, null)
                else -> {
                    val target = HeatScale.shutdownAvoidTarget(heat)!!
                    val roll = roller.roll2d6()
                    if (roll.total >= target) {
                        unit.copy(isShutdown = false) to UnitRestarted(unit.id, roll)
                    } else {
                        unit to null // restart failed; still down
                    }
                }
            }
        } else {
            when {
                HeatScale.isAutoShutdown(heat) ->
                    unit.copy(isShutdown = true) to UnitShutdown(unit.id, roll = null, auto = true)
                else -> {
                    val target = HeatScale.shutdownAvoidTarget(heat) ?: return unit to null
                    val roll = roller.roll2d6()
                    if (roll.total >= target) {
                        unit to null // avoided
                    } else {
                        unit.copy(isShutdown = true) to UnitShutdown(unit.id, roll, auto = false)
                    }
                }
            }
        }
    }

    /**
     * Life-support pilot damage (`docs/rules/armor-damage.md` §3 Life Support — the
     * only doc-specified pilot-hit sources): evaluated using [CombatUnit.critEffects]
     * for [CriticalComponent.LIFE_SUPPORT], the single tier -> effect source.
     *
     *  - 0 LS crits: no effect.
     *  - 1 LS crit ([CritEffect.PilotDamageWhenHeatAtLeast]): the pilot takes 1 hit
     *    ONLY if [CombatUnit.currentHeat] reaches that threshold **this turn** —
     *    checked against [unit], which has already been through
     *    [GameState.applyHeatPhase]'s fold (the heat fold runs before any per-unit
     *    resolution in [onEntry]), so "this turn's heat" is exactly the standing
     *    heat we're looking at here.
     *  - 2+ LS crits ([CritEffect.PilotDamageEachTurn]): the pilot takes 1 hit every
     *    turn, heat irrelevant.
     *
     * Captures whether the pilot was unconscious *before* this hit so the recovery
     * step ([resolveConsciousnessRecovery]) can skip a pilot newly knocked out here —
     * a pilot doesn't get a chance to wake up in the same Heat Phase they went down.
     * Delegates the actual hit/consciousness-check mechanics to [applyPilotHit].
     */
    private fun resolveLifeSupportPilotHit(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, List<GameEvent>> {
        val effects = unit.critEffects(CriticalComponent.LIFE_SUPPORT)
        val hitWarranted = effects.any { it is CritEffect.PilotDamageEachTurn } ||
            effects.filterIsInstance<CritEffect.PilotDamageWhenHeatAtLeast>().any { unit.currentHeat >= it.heat }
        if (!hitWarranted) return unit to emptyList()
        return applyPilotHit(unit, roller)
    }

    /**
     * Consciousness recovery for a pilot who was ALREADY unconscious entering this
     * Heat Phase ([wasUnconsciousBeforePhase], captured in [onEntry] before any of this
     * turn's resolution steps ran) — Stage 7, ASSUMPTION/standard, mirrors
     * [resolvePower]'s shutdown/restart shape. Skipped entirely — no dice rolled —
     * when the pilot was conscious entering this phase (a pilot knocked out moments
     * ago by [resolveLifeSupportPilotHit] already had its one consciousness check
     * this phase, inside [applyPilotHit], and does not also get a recovery attempt),
     * is currently conscious, or is dead ([CombatUnit.pilotHits] >= [PILOT_DEATH_THRESHOLD]).
     */
    private fun resolveConsciousnessRecovery(
        unit: CombatUnit,
        wasUnconsciousBeforePhase: Boolean,
        roller: DiceRoller,
    ): Pair<CombatUnit, GameEvent?> {
        if (!wasUnconsciousBeforePhase) return unit to null
        if (unit.isPilotConscious) return unit to null
        if (unit.pilotHits >= PILOT_DEATH_THRESHOLD) return unit to null
        return attemptConsciousnessRecovery(unit, roller)
    }

    /**
     * Ammo explosion: on a failed avoidance roll the ammo bin with the greatest
     * potential damage (`shots × damagePerShot`) cooks off into the center torso
     * (`docs/rules/armor-damage.md` §3), via the shared [detonateAmmoBin] helper —
     * unlike a critical-hit detonation (which hits the bin's own location), heat
     * cook-off always routes into [HitLocation.CENTER_TORSO].
     */
    private fun resolveAmmoExplosion(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, GameEvent?> {
        val target = HeatScale.ammoExplosionAvoidTarget(unit.currentHeat) ?: return unit to null
        val roll = roller.roll2d6()
        if (roll.total >= target) return unit to null

        val worst = unit.criticalLayout.ammoBins()
            .filter { (_, _, bin) -> bin.shots > 0 }
            .maxByOrNull { (_, _, bin) -> bin.shots * bin.type.damagePerShot }
            ?: return unit to null // nothing to explode

        val (location, slotIndex, bin) = worst
        return detonateAmmoBin(unit, location, slotIndex, bin, damageLocation = HitLocation.CENTER_TORSO)
    }
}
