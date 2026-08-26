package battletech.tactical.attack.weapon

import battletech.tactical.unit.UnitId

public data class TargetInfo(
    val unitId: UnitId,
    val unitName: String,
    val weapons: List<WeaponTargetInfo>,
)

/**
 * The line for [weaponIndex] against [targetId], or null when this attacker currently has no
 * line for that pair (e.g. the target dropped out of the firing arc since a declaration was
 * made). Shared by [battletech.tactical.query.DefaultPlayerView] and the TUI's draft rendering
 * so the two can't disagree about what "no line" means.
 */
public fun List<TargetInfo>.weaponAt(targetId: UnitId, weaponIndex: Int): WeaponTargetInfo? =
    firstOrNull { it.unitId == targetId }?.weapons?.firstOrNull { it.weaponIndex == weaponIndex }
