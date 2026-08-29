package battletech.tactical.unit

import kotlinx.serialization.Serializable

@Serializable
public data class MechModel(
    public val variant: String,
    public val name: String,
    public val tonnage: Int,
    public val walkingMP: Int,
    public val runningMP: Int,
    public val jumpMP: Int = 0,
    public val heatSink: HeatSink = HeatSink(HeatSinkType.STS, 10),
    public val armor: ArmorLayout,
    public val internalStructure: InternalStructureLayout = InternalStructureTables.forTonnage(tonnage),
    public val criticalLayout: CriticalLayout,
    public val weapons: List<Weapon>,
)
