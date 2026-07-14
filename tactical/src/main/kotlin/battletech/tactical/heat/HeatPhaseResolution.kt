package battletech.tactical.heat

import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.applyAmmoExplosionPilotHits
import battletech.tactical.attack.applyPilotHit
import battletech.tactical.attack.attemptConsciousnessRecovery
import battletech.tactical.attack.detonateAmmoBin
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.unitWaterDepth
import battletech.tactical.session.GameEvent
import battletech.tactical.session.UnitRestarted
import battletech.tactical.session.UnitShutdown
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.CritEffect
import battletech.tactical.unit.CriticalComponent
import battletech.tactical.unit.PILOT_DEATH_THRESHOLD
import battletech.tactical.unit.availableAmmoBins
import battletech.tactical.unit.critEffects

/**
 * A single unit's fixed sequence of Heat-Phase resolution steps, run against
 * its post-dissipation-fold state (see [battletech.tactical.session.HeatPhaseHandler.onEntry],
 * which folds every unit's heat first and then walks units in state order calling
 * this once each):
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
 *  5. [resolveDrowning] — cockpit flooding for a **prone** unit in depth-2+ water.
 *     A prone unit submerged in deep water takes 1 pilot hit per Heat Phase
 *     (ASSUMPTION/standard BattleTech). Placed last so all other pilot-damage
 *     sources are settled before the drowning check runs; a pilot knocked out by
 *     drowning will not attempt a recovery roll in the same phase.
 *
 * [state] is the whole post-fold [GameState] (constant across all units processed
 * this phase — passed through, not updated per unit), needed by [resolveDrowning]
 * for water-depth lookup. [wasUnconsciousBeforePhase] must be captured by the
 * caller before any of this turn's resolution steps mutate consciousness, so
 * [resolveConsciousnessRecovery] can tell "was already unconscious coming into
 * this phase" apart from "knocked out by life support just now".
 */
public fun resolveUnitHeatPhase(
    unit: CombatUnit,
    state: GameState,
    wasUnconsciousBeforePhase: Boolean,
    roller: DiceRoller,
): Pair<CombatUnit, List<GameEvent>> {
    val events = mutableListOf<GameEvent>()

    val (afterPower, powerEvent) = resolvePower(unit, roller)
    powerEvent?.let { events += it }

    val (afterLifeSupport, lifeSupportEvents) = resolveLifeSupportPilotHit(afterPower, roller)
    events += lifeSupportEvents

    val (afterRecovery, recoveryEvent) =
        resolveConsciousnessRecovery(afterLifeSupport, wasUnconsciousBeforePhase, roller)
    recoveryEvent?.let { events += it }

    val (afterAmmo, ammoEvents) = resolveAmmoExplosion(afterRecovery, roller)
    events += ammoEvents

    val (afterDrowning, drowningEvents) = resolveDrowning(afterAmmo, state, roller)
    events += drowningEvents

    return afterDrowning to events
}

/** Shutdown (for an operational unit) or startup (for a shut-down unit). */
private fun resolvePower(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, GameEvent?> {
    val heat = unit.currentHeat
    return if (unit.isShutdown) {
        when {
            HeatScale.isAutoShutdown(heat) -> unit to null // pinned down, no restart
            HeatScale.shutdownAvoidTarget(heat) == null ->
                unit.copy(isShutdown = false) to UnitRestarted.Automatic(unit.id)
            else -> {
                val target = HeatScale.shutdownAvoidTarget(heat)!!
                val roll = roller.roll2d6()
                if (roll.total >= target) {
                    unit.copy(isShutdown = false) to UnitRestarted.RollPassed(unit.id, roll)
                } else {
                    unit to null // restart failed; still down
                }
            }
        }
    } else {
        when {
            HeatScale.isAutoShutdown(heat) ->
                unit.copy(isShutdown = true) to UnitShutdown.Automatic(unit.id)
            else -> {
                val target = HeatScale.shutdownAvoidTarget(heat) ?: return unit to null
                val roll = roller.roll2d6()
                if (roll.total >= target) {
                    unit to null // avoided
                } else {
                    unit.copy(isShutdown = true) to UnitShutdown.AvoidFailed(unit.id, roll)
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
 *    resolution in [battletech.tactical.session.HeatPhaseHandler.onEntry]), so
 *    "this turn's heat" is exactly the standing heat we're looking at here.
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
 * Heat Phase ([wasUnconsciousBeforePhase], captured by the caller before any of this
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
 * Cockpit flooding: a **prone** unit standing in depth-2+ water drowns. The pilot
 * takes 1 hit per Heat Phase while the unit remains prone and submerged
 * (`docs/missing-rules.md` §Water & Depth — ASSUMPTION/standard BattleTech).
 *
 * Only targets prone (`isProne`) units whose pilot is still alive (`pilotHits <
 * [PILOT_DEATH_THRESHOLD]`). Destroyed units are already swept by
 * [battletech.tactical.session.BattleSession.runDestructionSweep] before the Heat
 * Phase begins, so they are skipped via the `isDestroyed` guard.
 *
 * Placed last in the Heat Phase sequence so all earlier pilot-damage sources
 * (life support, ammo cook-off) are settled first; a pilot knocked unconscious by
 * drowning does not also attempt a recovery roll this same phase.
 *
 * **Canonical dice order** (when drowning applies):
 *  1. Consciousness check 2d6 (from [applyPilotHit])
 */
private fun resolveDrowning(
    unit: CombatUnit,
    gameState: GameState,
    roller: DiceRoller,
): Pair<CombatUnit, List<GameEvent>> {
    if (unit.isDestroyed) return unit to emptyList()
    if (!unit.isProne) return unit to emptyList()
    val depth = unitWaterDepth(unit, gameState)
    if (depth < 2) return unit to emptyList()
    return applyPilotHit(unit, roller)
}

/**
 * Ammo explosion: on a failed avoidance roll the ammo bin with the greatest
 * potential damage (`shots × damagePerShot`) cooks off into the center torso
 * (`docs/rules/armor-damage.md` §3), via the shared [detonateAmmoBin] helper —
 * unlike a critical-hit detonation (which hits the bin's own location), heat
 * cook-off always routes into [HitLocation.CENTER_TORSO]. An ammo explosion also
 * inflicts 2 pilot hits on the unit (each running a consciousness check 2d6 via
 * [applyPilotHit], immediately after the `AmmoExploded` event).
 *
 * **Canonical dice order** (when explosion occurs):
 *  1. Avoidance 2d6
 *  2. (no detonation dice — damage is fixed from bin contents)
 *  3. Consciousness check 2d6 (pilot hit 1)
 *  4. Consciousness check 2d6 (pilot hit 2)
 */
private fun resolveAmmoExplosion(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, List<GameEvent>> {
    val target = HeatScale.ammoExplosionAvoidTarget(unit.currentHeat) ?: return unit to emptyList()
    val roll = roller.roll2d6()
    if (roll.total >= target) return unit to emptyList()

    // Only consider bins in locations whose IS is still positive; destroyed-location
    // bins are inaccessible (feed mechanism gone) and should not cook off.
    val worst = unit.availableAmmoBins()
        .filter { (_, _, bin) -> bin.shots > 0 }
        .maxByOrNull { (_, _, bin) -> bin.shots * bin.type.damagePerShot }
        ?: return unit to emptyList() // nothing to explode

    val (location, slotIndex, bin) = worst
    val (afterExplosion, ammoEvent) = detonateAmmoBin(unit, location, slotIndex, bin, damageLocation = HitLocation.CENTER_TORSO)
    if (ammoEvent == null) return unit to emptyList()

    val allEvents = mutableListOf<GameEvent>(ammoEvent)
    // Ammo explosion inflicts 2 pilot hits; each runs a consciousness check.
    val (working, hitEvents) = applyAmmoExplosionPilotHits(afterExplosion, roller)
    allEvents += hitEvents
    return working to allEvents
}
