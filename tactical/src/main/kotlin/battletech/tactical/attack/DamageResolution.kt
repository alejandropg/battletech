package battletech.tactical.attack

import battletech.tactical.unit.CombatUnit

/** Result of resolving damage at one or more locations via transfer. */
public data class DamageResolution(
    val unit: CombatUnit,
    val steps: List<LocationDamage>,
)
