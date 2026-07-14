package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.MechLocation
import battletech.tactical.session.GameEvent
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.destroyedLegCount
import battletech.tactical.unit.disableWeaponsIn

/**
 * Applies location-destruction consequences after a damage + crit resolution pass,
 * comparing [before] (pre-volley snapshot) against [after] (post-volley state) for every
 * unit, in unit-id order (for deterministic dice consumption).
 *
 * For each location whose IS was positive before the volley and is now 0:
 *  - **All locations**: Disables all weapons mounted there ([CombatUnit.disableWeaponsIn]).
 *  - **LEFT_TORSO / RIGHT_TORSO**: Also zeroes the same-side arm's IS and disables its
 *    weapons, if the arm was not already destroyed. This mirrors the rules for side-torso
 *    destruction dropping the attached arm.
 *  - **LEFT_LEG / RIGHT_LEG**: Records the unit for a forced fall, processed after all
 *    weapon-disable and cascade work. No fall is triggered when both legs are now destroyed
 *    (the unit is already queued for elimination by the session's destruction sweep) or
 *    when the unit is already prone.
 *
 * **Ammo in destroyed locations** is handled by the exclusion model
 * ([CombatUnit.availableAmmoBins] / [CombatUnit.consumeOneRoundFromAvailableBin]):
 * bins whose location IS = 0 are invisible to ammo-consumption and availability checks.
 * This avoids the cascading-explosion problem that detonation would introduce (detonation
 * adds IS damage, which can destroy more locations, which would need further consequences
 * — a loop bounded only by ammo count). Bins already emptied by a crit-triggered
 * detonation earlier in the same volley are already shots=0 and thus filtered out anyway.
 *
 * Falls are applied last, in the same unit-id order, via [forcedFall] (which also applies
 * 1 pilot hit per fall). Units already prone before falls are processed are skipped.
 *
 * Called from [battletech.tactical.attack.weapon.WeaponAttackPhaseHandler] and
 * [battletech.tactical.attack.physical.PhysicalAttackPhaseHandler] after weapon/physical
 * damage, crits, and gyro-fall effects have all been applied.
 */
internal fun applyLocationDestructionConsequences(
    before: GameState,
    after: GameState,
    roller: DiceRoller,
): Pair<GameState, List<GameEvent>> {
    var state = after
    val events = mutableListOf<GameEvent>()
    val fallPendingUnitIds = mutableListOf<UnitId>()

    for (unit in after.units.sortedBy { it.id.value }) {
        val beforeUnit = before.unitById(unit.id)
        // Use the most-current version of this unit (modified by previous iterations if needed).
        var updatedUnit = state.unitById(unit.id)

        // Find all locations newly destroyed in this pass.
        val newlyDestroyed = MechLocation.entries.filter { location ->
            beforeUnit.internalStructure.isIntact(location) &&
                !updatedUnit.internalStructure.isIntact(location)
        }

        if (newlyDestroyed.isEmpty()) continue

        var needsFall = false

        for (location in newlyDestroyed) {
            // 1. Disable weapons at the destroyed location.
            updatedUnit = updatedUnit.disableWeaponsIn(location)

            // 2. Side-torso cascade: destroy the same-side arm if still intact.
            when (location) {
                MechLocation.LEFT_TORSO -> {
                    if (updatedUnit.internalStructure.isIntact(MechLocation.LEFT_ARM)) {
                        updatedUnit = updatedUnit.copy(
                            internalStructure = updatedUnit.internalStructure.with(MechLocation.LEFT_ARM, 0),
                        ).disableWeaponsIn(MechLocation.LEFT_ARM)
                    }
                }
                MechLocation.RIGHT_TORSO -> {
                    if (updatedUnit.internalStructure.isIntact(MechLocation.RIGHT_ARM)) {
                        updatedUnit = updatedUnit.copy(
                            internalStructure = updatedUnit.internalStructure.with(MechLocation.RIGHT_ARM, 0),
                        ).disableWeaponsIn(MechLocation.RIGHT_ARM)
                    }
                }
                // 3. Leg destruction — schedule a fall unless both legs are now destroyed
                //    (unit is being eliminated anyway by the destruction sweep).
                MechLocation.LEFT_LEG, MechLocation.RIGHT_LEG -> {
                    if (updatedUnit.destroyedLegCount() < 2) {
                        needsFall = true
                    }
                }
                else -> { /* no additional cascade for other locations */ }
            }
        }

        // Commit the modified unit back to state before processing the next unit.
        state = state.copy(
            units = state.units.map { if (it.id == updatedUnit.id) updatedUnit else it },
        )

        if (needsFall && !updatedUnit.isProne) {
            fallPendingUnitIds.add(unit.id)
        }
    }

    // Process falls last, in the same unit-id order, so dice consumption is deterministic.
    // Each fall also applies 1 pilot hit via forcedFall (canonical dice order per unit:
    // fall location 2d6 + facing 1d6 + consciousness check 2d6).
    for (unitId in fallPendingUnitIds) {
        val unit = state.unitById(unitId)
        if (unit.isProne) continue  // already prone (e.g. from gyro fall earlier this pass)
        val (injured, fallEvents) = forcedFall(unit, roller)
        state = state.copy(
            units = state.units.map { if (it.id == injured.id) injured else it },
        )
        events += fallEvents
    }

    return state to events
}
