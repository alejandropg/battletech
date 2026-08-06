package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.session.GameEvent
import battletech.tactical.session.UnitFell
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.basePsrModifier
import battletech.tactical.unit.pilotingSkillRoll

/**
 * Makes [unit] fall and applies the accompanying pilot hit (`docs/rules/pilot.md` §4).
 *
 * **Canonical dice order** (seeded tests must match this order):
 *  1. Fall location 2d6 (from [fall])
 *  2. Fall facing 1d6 (from [fall])
 *  3. Consciousness check 2d6 (from [applyPilotHit], unless pilot is already dead — death
 *     threshold consumes no dice)
 *
 * Returns the fallen+injured unit and a list of events:
 * `[UnitFell(unit.id, fallResult)] + pilotHitEvents`.
 *
 * All dice flow through [roller]; no raw `Random`.
 */
public fun forcedFall(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, List<GameEvent>> {
    val (fallen, fallResult) = fall(unit, roller)
    val (injured, pilotEvents) = applyPilotHit(fallen, roller)
    return injured to (listOf<GameEvent>(UnitFell(unit.id, fallResult)) + pilotEvents)
}

/**
 * Rolls a PSR for [unit] at [modifier]; on failure applies [forcedFall]. Returns the
 * updated unit and any events emitted. Returns the original unit with no events when:
 *  - the unit is already prone (no fall needed), or
 *  - the PSR passes.
 *
 * The composite modifier should include all applicable penalties (gyro, leg, etc.) — callers
 * are responsible for building it. [forcePsrOrFall] does NOT add gyro/leg modifiers itself.
 *
 * **Canonical dice order** (failure path only):
 *  1. PSR 2d6
 *  2. Fall location 2d6 + facing 1d6 + consciousness check 2d6 (from [forcedFall])
 */
public fun forcePsrOrFall(unit: CombatUnit, modifier: Int, roller: DiceRoller): Pair<CombatUnit, List<GameEvent>> {
    if (unit.isProne) return unit to emptyList()
    val psr = pilotingSkillRoll(unit, roller, modifier)
    return if (!psr.passed) {
        forcedFall(unit, roller)
    } else {
        unit to emptyList()
    }
}

/**
 * The 20-damage forced PSR (`docs/rules/pilot.md` §3), run after a volley resolves. Each
 * target unit's total damage is the sum of [LocationDamage.armorDamage] +
 * [LocationDamage.structureDamage] across all steps; the unit's current PSR modifiers
 * (gyro, leg) are folded in alongside the damage modifier.
 *
 * Units already prone when their name is reached are skipped. Processing order is
 * deterministic (sorted by [UnitId.value]) so dice consumption is reproducible.
 *
 * Called from [battletech.tactical.attack.weapon.WeaponAttackPhaseHandler] and
 * [battletech.tactical.attack.physical.PhysicalAttackPhaseHandler] after damage, crits,
 * gyro-fall effects, and location-destruction consequences have all been applied.
 *
 * **Canonical dice order per unit** (if PSR fails):
 *  1. PSR 2d6
 *  2. Fall location 2d6 + facing 1d6 + consciousness check 2d6 (from [forcedFall])
 */
public fun applyTwentyDamagePsrs(
    state: GameState,
    damageByUnit: Map<UnitId, Int>,
    roller: DiceRoller,
): Pair<GameState, List<GameEvent>> {
    var currentState = state
    val events = mutableListOf<GameEvent>()
    for ((unitId, totalDamage) in damageByUnit.entries.sortedBy { it.key.value }) {
        if (totalDamage < 20) continue
        val unit = currentState.units.byId(unitId)
        val modifier = totalDamage / 20 + unit.basePsrModifier()
        val (updated, fallEvents) = forcePsrOrFall(unit, modifier, roller)
        if (fallEvents.isNotEmpty()) {
            currentState = currentState.copy(units = currentState.units.withUnit(updated))
            events += fallEvents
        }
    }
    return currentState to events
}
