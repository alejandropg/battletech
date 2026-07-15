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
 * The viewer's own unit (or any unit, at the deliberate match-over reveal — see
 * [GameState.projectFor][battletech.tactical.model.GameState]). Every [VisibleUnit]
 * member delegates to [unit], and [unit] itself — including the fields the sealed
 * interface never declares — stays reachable for the owner's own rendering (record
 * sheet, target number breakdowns, heat tracking, critical hits, ...).
 */
@Serializable
public data class OwnUnit(public val unit: CombatUnit) : VisibleUnit {
    override val id: UnitId get() = unit.id
    override val owner: PlayerId get() = unit.owner
    override val name: String get() = unit.name
    override val tonnage: Int get() = unit.tonnage
    override val position: HexCoordinates get() = unit.position
    override val facing: HexDirection get() = unit.facing
    override val torsoFacing: HexDirection get() = unit.torsoFacing
    override val armor: ArmorLayout get() = unit.armor
    override val walkingMP: Int get() = unit.walkingMP
    override val runningMP: Int get() = unit.runningMP
    override val jumpMP: Int get() = unit.jumpMP
    override val weapons: List<PublicWeapon> get() = unit.weapons.map { PublicWeapon(name = it.name) }
    override val isProne: Boolean get() = unit.isProne
    override val isShutdown: Boolean get() = unit.isShutdown
    override val isDestroyed: Boolean get() = unit.isDestroyed
    override val movementThisTurn: MovementThisTurn get() = unit.movementThisTurn
}
