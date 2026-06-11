package battletech.tactical.unit

public data class MechModel(
    val variant: String,
    val name: String,
    val tonnage: Int,
    val walkingMP: Int,
    val runningMP: Int,
    val jumpMP: Int = 0,
    val heatSink: HeatSink = HeatSink(HeatSinkType.STS, 10),
    val armor: ArmorLayout,
    val internalStructure: InternalStructureLayout = InternalStructureTables.forTonnage(tonnage),
    val weapons: List<() -> Weapon>,
)
