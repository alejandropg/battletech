package battletech.tactical.session

import battletech.tactical.attack.physical.Knockdown
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId

/**
 * Redacts [this] event for [viewer]: the log/wire counterpart of
 * [battletech.tactical.query.projectFor] for units, and the single rule both the log
 * ([BattleSession.logFor]) and the wire (Stage 4) redact through. Three outcomes:
 *
 *  - the event unchanged (verbatim) — [revealAll], or the event carries no private
 *    data at all (most events; see the branches below),
 *  - a redacted variant with record-sheet fields stripped, or
 *  - `null`, to suppress the event outright ([HeatDissipated] when the viewer's
 *    filtered share of it is empty but the original was not).
 *
 * The rule this implements throughout (see the `VisibleUnit` KDoc): **observability,
 * not sensitivity**. A 'Mech shutting down, falling, standing, being destroyed, or
 * taking a critical hit is a fact any player at the table would see; the record-sheet
 * numbers behind it (heat, which component, ammo type, pilot-hit totals, skill-revealing
 * rolls) are not, and are withheld from a [viewer] who does not own the unit.
 *
 * **A roll is never "just a roll" when the event carrying it is conditional on the roll's
 * outcome.** Several events are emitted only on a pass (or only on a fail) against a target
 * number computed from private data, so the event's mere *existence* asserts an inequality
 * and the roll value bounds that private number. Judging such a roll in isolation ("2d6
 * reveals nothing") is the mistake; the precondition is the leak:
 *  - [PilotRecoveredConsciousness.Detailed] fires only when `roll.total >=
 *    CONSCIOUSNESS_TARGET[pilotHits]` — the roll bounds `pilotHits`.
 *  - [UnitRestarted.RollPassed] fires only when `roll.total >= shutdownAvoidTarget(heat)`,
 *    and [UnitShutdown.AvoidFailed] only when `roll.total <` it — both bound `currentHeat`.
 * All three are withheld for foreign units. By contrast [InitiativeRolled]'s dice are
 * unconditional (both players' rolls are emitted win or lose, and the target is the
 * opponent's roll, not a record-sheet number), so they stay verbatim.
 *
 * Ownership resolves via [GameState.unitById]. [viewer] `== null` means "I don't know
 * who is looking": every unit is treated as foreign, same as [battletech.tactical.query.projectFor].
 * This fails CLOSED on purpose — the opposite (fail-open) was the live bug fixed in
 * `29c7576`; do not repeat it.
 *
 * **Known limitation, accepted by design**: [AttacksResolved] is NOT redacted here.
 * [battletech.tactical.attack.AttackResult.gunnery] is technically record-sheet data, but
 * [battletech.tactical.attack.AttackResult.targetNumber] and its `modifiers` breakdown are
 * both announced at the table (observable), and `targetNumber == gunnery + sum(modifiers)`
 * — redacting `gunnery` alone would leave it derivable by subtraction, a guarantee this
 * function does not make. Hiding it fully would mean hiding the TN too, which contradicts
 * the observability rule. See `AttackResultsView` (tui) for the one concession taken: the
 * enemy attacker's breakdown drops the explicit "+N gunnery" label so the number is not
 * handed over for free, while the (unavoidable) derivation path stays open. The physical
 * side of this — [PhysicalAttacksResolved]'s [PhysicalAttackResult.targetNumber] — has no
 * analogous `modifiers` breakdown to derive anything from, so it is left alone too; its
 * embedded [Knockdown] PSR is a different, unambiguous leak and IS redacted below.
 */
public fun GameEvent.redactFor(viewer: PlayerId?, state: GameState, revealAll: Boolean = false): GameEvent? {
    if (revealAll) return this

    fun owns(unitId: UnitId): Boolean = viewer != null && state.unitById(unitId).owner == viewer

    return when (this) {
        is HeatDissipated -> {
            val before = heatBefore.filterKeys(::owns)
            if (before.isEmpty() && heatBefore.isNotEmpty()) {
                null // Someone's heat dissipated, just not the viewer's — say nothing rather than lie.
            } else {
                copy(heatBefore = before, heatAfter = heatAfter.filterKeys(::owns))
            }
        }

        is CriticalHit.Detailed -> if (owns(unitId)) this else CriticalHit.Undisclosed(unitId)
        is CriticalHit.Undisclosed -> this

        is AmmoExploded.Detailed -> if (owns(unitId)) this else AmmoExploded.Undisclosed(unitId, damage)
        is AmmoExploded.Undisclosed -> this

        is UnitStoodUp.Detailed -> if (owns(unitId)) this else UnitStoodUp.Undisclosed(unitId, stoodUp)
        is UnitStoodUp.Undisclosed -> this

        is UnitShutdown.Automatic -> if (owns(unitId)) this else UnitShutdown.Undisclosed(unitId)
        is UnitShutdown.AvoidFailed -> if (owns(unitId)) this else UnitShutdown.Undisclosed(unitId)
        is UnitShutdown.Undisclosed -> this

        is UnitRestarted.Automatic -> if (owns(unitId)) this else UnitRestarted.Undisclosed(unitId)
        is UnitRestarted.RollPassed -> if (owns(unitId)) this else UnitRestarted.Undisclosed(unitId)
        is UnitRestarted.Undisclosed -> this

        is PilotHit.Fatal -> if (owns(unitId)) this else PilotHit.Undisclosed(unitId)
        is PilotHit.Checked -> if (owns(unitId)) this else PilotHit.Undisclosed(unitId)
        is PilotHit.Undisclosed -> this

        is PilotRecoveredConsciousness.Detailed -> if (owns(unitId)) this else PilotRecoveredConsciousness.Undisclosed(unitId)
        is PilotRecoveredConsciousness.Undisclosed -> this

        is PhysicalAttacksResolved -> copy(results = results.map { redactPhysicalResult(it, viewer, state) })

        // No private fields: the fact + its already-public detail (position, mode, damage,
        // location, facing, initiative rolls, declared targets/weapons, phase/turn markers,
        // the match outcome, free text) is all any player could already see.
        is UnitMoved -> this
        is AttacksResolved -> this
        is UnitFell -> this
        is AttackDeclarationsRecorded -> this
        is TorsoFacingsApplied -> this
        is PhaseChanged -> this
        is InitiativeRolled -> this
        is TurnEnded -> this
        is UnitDestroyed -> this
        is MatchEnded -> this
        is PilotKnockedUnconscious -> this
        is SessionNotice -> this
    }
}

/**
 * Redacts the [Knockdown] embedded in one [PhysicalAttackResult]: the faller (target on a
 * hit, attacker on a miss — mirrors [battletech.tactical.attack.physical.resolvePhysicalAttacks]'s
 * own `fallerId` derivation) owning the roll determines whether it survives verbatim.
 * [Knockdown.Fell]'s nested `pilotEvents` get the same recursive treatment as top-level
 * [PilotHit]/[PilotKnockedUnconscious] events, since they are copies of exactly those.
 */
private fun redactPhysicalResult(result: PhysicalAttackResult, viewer: PlayerId?, state: GameState): PhysicalAttackResult {
    fun owns(unitId: UnitId): Boolean = viewer != null && state.unitById(unitId).owner == viewer

    val fallerId = if (result is PhysicalAttackResult.Hit) result.targetId else result.attackerId
    val redacted = when (val knockdown = result.knockdown) {
        Knockdown.None -> knockdown
        is Knockdown.Resisted.Detailed -> if (owns(fallerId)) knockdown else Knockdown.Resisted.Undisclosed
        is Knockdown.Resisted.Undisclosed -> knockdown
        is Knockdown.Fell.Detailed ->
            if (owns(knockdown.unitId)) {
                knockdown
            } else {
                Knockdown.Fell.Undisclosed(
                    unitId = knockdown.unitId,
                    fall = knockdown.fall,
                    pilotEvents = knockdown.pilotEvents.mapNotNull { it.redactFor(viewer, state, revealAll = false) },
                )
            }
        is Knockdown.Fell.Undisclosed -> knockdown
    }

    return when (result) {
        is PhysicalAttackResult.Hit -> result.copy(knockdown = redacted)
        is PhysicalAttackResult.Miss -> result.copy(knockdown = redacted)
    }
}
