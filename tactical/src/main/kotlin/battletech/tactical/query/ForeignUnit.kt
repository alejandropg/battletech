package battletech.tactical.query

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

/**
 * A unit the viewer does NOT own: only the fields declared on [VisibleUnit], nothing
 * else. There is no gunnery skill, piloting skill, current heat, heat sink, internal
 * structure, critical hit layout, heat generated this turn, or pilot hits field on this
 * type — that data is simply absent, so exposing it through this projection is a compile
 * error, not a discipline problem.
 */
@Serializable
public data class ForeignUnit(
    override val id: UnitId,
    override val owner: PlayerId,
    override val name: String,
    override val tonnage: Int,
    override val position: HexCoordinates,
    override val facing: HexDirection,
    override val torsoFacing: HexDirection,
    override val armor: ArmorLayout,
    override val walkingMP: Int,
    override val runningMP: Int,
    override val jumpMP: Int,
    override val weapons: List<PublicWeapon>,
    override val isProne: Boolean,
    override val isShutdown: Boolean,
    override val isDestroyed: Boolean,
    override val isPilotConscious: Boolean,
    override val movementThisTurn: MovementThisTurn,
) : VisibleUnit {
    public companion object {
        /** Builds the public-only projection of [unit]. */
        public fun from(unit: CombatUnit): ForeignUnit = ForeignUnit(
            id = unit.id,
            owner = unit.owner,
            name = unit.name,
            tonnage = unit.tonnage,
            position = unit.position,
            facing = unit.facing,
            torsoFacing = unit.torsoFacing,
            armor = unit.armor,
            walkingMP = unit.walkingMP,
            runningMP = unit.runningMP,
            jumpMP = unit.jumpMP,
            weapons = unit.weapons.map { PublicWeapon(name = it.name) },
            isProne = unit.isProne,
            isShutdown = unit.isShutdown,
            isDestroyed = unit.isDestroyed,
            isPilotConscious = unit.isPilotConscious,
            movementThisTurn = unit.movementThisTurn,
        )
    }
}
