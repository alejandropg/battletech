package battletech.tui.game.phase

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.model.HexDirection
import battletech.tactical.unit.UnitId

/** Pure draft state for one attacker's weapon-target assignments during declaration. */
internal data class WeaponAllocation(
    val torsoFacing: HexDirection,
    val weaponAssignments: Map<UnitId, Set<Int>> = emptyMap(),
    val primaryTargetId: UnitId? = null,
    val cursorTargetIndex: Int = 0,
    val cursorWeaponIndex: Int = 0,
) {

    /**
     * Toggle the weapon at [cursorWeaponIndex] on the target at [cursorTargetIndex].
     * Returns `this` unchanged when no toggle can be applied (no targets, weapon unavailable,
     * weapon assigned elsewhere).
     */
    fun toggle(targets: List<TargetInfo>): WeaponAllocation {
        if (targets.isEmpty() || cursorTargetIndex >= targets.size) return this

        val currentTarget = targets[cursorTargetIndex]
        val weapons = currentTarget.weapons
        if (weapons.isEmpty()) return this

        val weapon = weapons[cursorWeaponIndex]
        if (!weapon.available) return this

        val targetId = currentTarget.unitId
        val assignedElsewhere = weaponAssignments.any { (otherId, indices) ->
            otherId != targetId && weapon.weaponIndex in indices
        }
        if (assignedElsewhere) return this

        val currentAssigned = weaponAssignments[targetId] ?: emptySet()
        val newAssigned = if (weapon.weaponIndex in currentAssigned) {
            currentAssigned - weapon.weaponIndex
        } else {
            currentAssigned + weapon.weaponIndex
        }
        val newAssignments = weaponAssignments + (targetId to newAssigned)
        val newPrimaryTargetId = when {
            newAssigned.isEmpty() && targetId == primaryTargetId ->
                newAssignments.entries
                    .firstOrNull { (id, indices) -> id != targetId && indices.isNotEmpty() }
                    ?.key

            primaryTargetId == null -> targetId
            else -> primaryTargetId
        }

        return copy(weaponAssignments = newAssignments, primaryTargetId = newPrimaryTargetId)
    }

    /**
     * Twist torso to [newTorsoFacing], pruning assignments to targets no longer in
     * [newValidIds] and clamping cursors to the new [newTargets] list.
     */
    fun twist(
        newTorsoFacing: HexDirection,
        newTargets: List<TargetInfo>,
        newValidIds: Set<UnitId>,
    ): WeaponAllocation {
        val newAssignments = weaponAssignments.filterKeys { it in newValidIds }
        val newPrimary = if (primaryTargetId in newValidIds) primaryTargetId else null

        val newCursorTargetIdx = if (newTargets.isEmpty()) 0
        else cursorTargetIndex.coerceIn(0, newTargets.size - 1)
        val newCursorWeaponIdx = if (newTargets.isEmpty()) 0
        else {
            val maxWeapon = (newTargets[newCursorTargetIdx].weapons.size - 1).coerceAtLeast(0)
            cursorWeaponIndex.coerceIn(0, maxWeapon)
        }

        return copy(
            torsoFacing = newTorsoFacing,
            cursorTargetIndex = newCursorTargetIdx,
            cursorWeaponIndex = newCursorWeaponIdx,
            weaponAssignments = newAssignments,
            primaryTargetId = newPrimary,
        )
    }

    /**
     * Move the weapon cursor by [delta] positions across a flattened (target, weapon) list,
     * wrapping around. Returns `this` when there are no weapons to navigate.
     */
    fun navigate(delta: Int, targets: List<TargetInfo>): WeaponAllocation {
        if (targets.isEmpty()) return this

        data class Entry(val targetIdx: Int, val weaponIdx: Int)

        val flat = mutableListOf<Entry>()
        for ((ti, target) in targets.withIndex()) {
            for (wi in target.weapons.indices) {
                flat.add(Entry(ti, wi))
            }
        }
        if (flat.isEmpty()) return this

        val currentFlatIdx = flat.indexOfFirst {
            it.targetIdx == cursorTargetIndex && it.weaponIdx == cursorWeaponIndex
        }.let { if (it < 0) 0 else it }

        val newFlatIdx = (currentFlatIdx + delta + flat.size) % flat.size
        val newEntry = flat[newFlatIdx]
        return copy(cursorTargetIndex = newEntry.targetIdx, cursorWeaponIndex = newEntry.weaponIdx)
    }

    /**
     * Jump the target cursor to the target matching [targetId].
     * Returns `this` if [targetId] is not present in [targets].
     */
    fun clickTarget(targetId: UnitId, targets: List<TargetInfo>): WeaponAllocation {
        val idx = targets.indexOfFirst { it.unitId == targetId }
        return if (idx >= 0) copy(cursorTargetIndex = idx, cursorWeaponIndex = 0) else this
    }
}
