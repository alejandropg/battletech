package battletech.tactical.unit

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("combatUnit")
public data class CombatUnit(
    /**
     * The chassis this unit was built from — everything about it that never changes across a
     * game: name, tonnage, movement points, heat sink count, and the pristine armor/internal
     * structure/critical layout/weapons loadout. Ships on the wire as just [MechModel.variant]
     * (see [MechModelAsVariant]) and is resolved back through [MechModels] on the other side, so
     * both sides must agree on the registry — see `docs/wire-protocol.md`.
     */
    @Serializable(with = MechModelAsVariant::class)
    public val model: MechModel,
    override val id: UnitId,
    override val owner: PlayerId,
    public val gunnerySkill: Int = 4,
    public val pilotingSkill: Int = 5,
    override val position: HexCoordinates,
    override val facing: HexDirection = HexDirection.N,
    override val torsoFacing: HexDirection = facing,
    public val currentHeat: Int = 0,
    override val armor: ArmorLayout = model.armor,
    public val internalStructure: InternalStructureLayout = model.internalStructure,
    override val weapons: List<Weapon> = model.weapons,
    public val criticalLayout: CriticalLayout = model.criticalLayout,
    override val movementThisTurn: MovementThisTurn = MovementThisTurn.Stationary,
    public val heatGeneratedThisTurn: List<HeatSource> = emptyList(),
    override val isProne: Boolean = false,
    override val isShutdown: Boolean = false,
    override val isDestroyed: Boolean = false,
    public val criticalHits: Map<MechLocation, Set<Int>> = emptyMap(),
    public val pilotHits: Int = 0,
    override val isPilotConscious: Boolean = true,
) : VisibleUnit {
    /** The variant identifier, e.g. `"AS7-D"` — shorthand for [model]'s own field. */
    public val variant: String get() = model.variant
    override val name: String get() = model.name
    override val tonnage: Int get() = model.tonnage
    override val walkingMP: Int get() = model.walkingMP
    override val runningMP: Int get() = model.runningMP
    override val jumpMP: Int get() = model.jumpMP
    public val heatSink: HeatSink get() = model.heatSink

    /** The chassis's published armor allocation — [armor] is the current, possibly-damaged value. */
    override val maxArmor: ArmorLayout get() = model.armor

    /** The chassis's published internal structure — [internalStructure] is the current value. */
    public val maxInternalStructure: InternalStructureLayout get() = model.internalStructure
}
