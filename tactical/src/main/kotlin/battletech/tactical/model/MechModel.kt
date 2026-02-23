package battletech.tactical.model

public data class MechModel(
    val variant: String,
    val name: String,
    val walkingMP: Int,
    val runningMP: Int,
    val jumpMP: Int = 0,
    val heatSinkCapacity: Int = 10,
    val weapons: List<() -> Weapon>,
)
