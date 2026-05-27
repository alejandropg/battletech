package battletech.tactical.query

import battletech.tactical.model.PlayerId
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.UnitId

public data class PublicWeapon(val name: String)

public data class PublicUnit(
    val id: UnitId,
    val owner: PlayerId,
    val name: String,
    val walkingMP: Int,
    val runningMP: Int,
    val jumpMP: Int,
    val armor: ArmorLayout,
    val weapons: List<PublicWeapon>,
)
