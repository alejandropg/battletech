package battletech.tactical.query

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.movement.ReachabilityCalculator
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.unit.UnitId

public class DefaultPlayerView(
    override val playerId: PlayerId,
    override val state: PublicGameState,
) : PlayerView {

    private val weaponTargeting = WeaponTargeting(state)
    private val physicalAttackQueries = PhysicalAttackQueries(state)

    override fun legalMovementsFor(unitId: UnitId): List<ReachabilityMap> {
        if (state.units.none { it.id == unitId }) return emptyList()
        val unit = state.unitById(unitId)
        if (unit.isShutdown || unit.isDestroyed || !unit.isPilotConscious) return emptyList()
        val calculator = ReachabilityCalculator(state.map, state.units)
        return buildList {
            if (unit.walkingMP > 0) add(calculator.calculate(unit, MovementMode.WALK))
            if (unit.runningMP > 0) add(calculator.calculate(unit, MovementMode.RUN))
            if (unit.jumpMP > 0) add(calculator.calculate(unit, MovementMode.JUMP))
        }
    }

    override fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates> =
        weaponTargeting.fireArc(attackerId, torsoFacing)

    override fun validTargets(attackerId: UnitId, torsoFacing: HexDirection): Set<UnitId> =
        weaponTargeting.validTargets(attackerId, torsoFacing)

    override fun targetInfos(attackerId: UnitId, torsoFacing: HexDirection): List<TargetInfo> =
        weaponTargeting.targetInfos(attackerId, torsoFacing)

    override fun physicalAttackOptions(attackerId: UnitId): List<PhysicalAttackOption> =
        physicalAttackQueries.physicalAttackOptions(attackerId)

    override fun resolveTargetPositions(targetIds: Set<UnitId>): Set<HexCoordinates> =
        targetIds.map { state.unitById(it).position }.toSet()

    override fun publicUnit(unitId: UnitId): PublicUnit? {
        if (state.units.none { it.id == unitId }) return null
        val unit = state.unitById(unitId)
        return PublicUnit(
            id = unit.id,
            owner = unit.owner,
            name = unit.name,
            walkingMP = unit.walkingMP,
            runningMP = unit.runningMP,
            jumpMP = unit.jumpMP,
            armor = unit.armor,
            weapons = unit.weapons.map { PublicWeapon(name = it.name) },
        )
    }
}
