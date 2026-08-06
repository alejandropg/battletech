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
 * The consequences themselves are owned by `docs/rules/armor-damage.md` §8. Two decisions
 * this code makes on top of them:
 *
 * A leg destruction schedules **no** fall when both legs are now destroyed (the unit is
 * already queued for elimination by the session's destruction sweep) or when the unit is
 * already prone. Falls are applied last, in unit-id order, via [forcedFall].
 *
 * **Ammo in destroyed locations** is handled by exclusion rather than detonation
 * ([CombatUnit.availableAmmoBins] / [CombatUnit.consumeOneRoundFromAvailableBin]): bins
 * whose location IS = 0 are invisible to ammo-consumption and availability checks. This
 * avoids a cascading-explosion loop bounded only by ammo count, since detonation adds IS
 * damage that can destroy further locations. Bins already emptied by a crit-triggered
 * detonation earlier in the same volley are shots=0 and filtered out anyway.
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

    for (unit in after.units.all.sortedBy { it.id.value }) {
        val beforeUnit = before.units.byId(unit.id)
        // Use the most-current version of this unit (modified by previous iterations if needed).
        var updatedUnit = state.units.byId(unit.id)

        val newlyDestroyed = MechLocation.entries.filter { location ->
            beforeUnit.internalStructure.isIntact(location) &&
                !updatedUnit.internalStructure.isIntact(location)
        }

        if (newlyDestroyed.isEmpty()) continue

        var needsFall = false

        for (location in newlyDestroyed) {
            updatedUnit = updatedUnit.disableWeaponsIn(location)

            // Side-torso cascade: destroy the same-side arm if still intact.
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
                // Schedule a fall unless both legs are now destroyed (the unit is being
                // eliminated anyway by the destruction sweep).
                MechLocation.LEFT_LEG, MechLocation.RIGHT_LEG -> {
                    if (updatedUnit.destroyedLegCount() < 2) {
                        needsFall = true
                    }
                }
                else -> { /* no additional cascade for other locations */ }
            }
        }

        state = state.copy(units = state.units.withUnit(updatedUnit))

        if (needsFall && !updatedUnit.isProne) {
            fallPendingUnitIds.add(unit.id)
        }
    }

    // Falls run last, in unit-id order, so dice consumption is deterministic. Canonical
    // dice order per unit: fall location 2d6 + facing 1d6 + consciousness check 2d6.
    for (unitId in fallPendingUnitIds) {
        val unit = state.units.byId(unitId)
        if (unit.isProne) continue  // already prone (e.g. from gyro fall earlier this pass)
        val (injured, fallEvents) = forcedFall(unit, roller)
        state = state.copy(units = state.units.withUnit(injured))
        events += fallEvents
    }

    return state to events
}
