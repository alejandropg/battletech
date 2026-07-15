package battletech.tactical.query

import battletech.tactical.attack.torsoTwistOptions
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.movement.MovementRules
import battletech.tactical.movement.ReachabilityCalculator
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.session.TurnState
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId

public class DefaultPlayerView(
    override val playerId: PlayerId,
    private val state: GameState,
    private val turnState: TurnState = TurnState.NULL,
) : PlayerView {

    private val weaponTargeting = WeaponTargeting(state)
    private val physicalAttackQueries = PhysicalAttackQueries(state)

    override fun legalMovementsFor(unitId: UnitId): List<ReachabilityMap> {
        val unit = state.findUnit(unitId) ?: return emptyList()
        val calculator = ReachabilityCalculator(state.map, state.units)
        return MovementRules.availableModes(unit).map { mode -> calculator.calculate(unit, mode) }
    }

    override fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates> =
        weaponTargeting.fireArc(attackerId, torsoFacing)

    override fun validTargets(attackerId: UnitId, torsoFacing: HexDirection): Set<UnitId> =
        weaponTargeting.validTargets(attackerId, torsoFacing)

    override fun targetInfos(attackerId: UnitId, torsoFacing: HexDirection): List<TargetInfo> =
        weaponTargeting.targetInfos(attackerId, torsoFacing)

    override fun physicalAttackOptions(attackerId: UnitId): List<PhysicalAttackOption> =
        physicalAttackQueries.physicalAttackOptions(attackerId)

    override fun legalTorsoFacings(unitId: UnitId): Set<HexDirection> {
        val unit = state.findUnit(unitId) ?: return emptySet()
        return torsoTwistOptions(unit.facing)
    }

    override fun declaredWeaponAttacks(): List<DeclaredWeaponAttack> {
        val playerOrder: List<PlayerId> = if (turnState.attack.sequence.order.isNotEmpty()) {
            turnState.attack.sequence.order.map { it.player }.distinct()
        } else {
            listOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2)
        }

        val committedByAttacker = turnState.attack.weaponDeclarations.groupBy { it.attackerId }

        return buildList {
            for (player in playerOrder) {
                committedByAttacker.keys
                    .filter { id -> state.unitById(id).owner == player }
                    .sortedBy { it.value }
                    .forEach { attackerId ->
                        val attackerUnit = state.unitById(attackerId)
                        val declarations = committedByAttacker[attackerId] ?: return@forEach
                        val targetInfos = targetInfos(attackerId, attackerUnit.torsoFacing)
                        declarations.groupBy { it.targetId }.forEach { (targetId, decls) ->
                            val weaponIndices = decls.sortedBy { it.weaponIndex }.map { it.weaponIndex }
                            val isPrimary = decls.any { it.isPrimary }
                            val weapons = weaponIndices.map { weaponIndex ->
                                declaredWeaponLine(attackerUnit, weaponIndex, targetId, targetInfos)
                            }
                            add(DeclaredWeaponAttack(attackerId, targetId, isPrimary, weapons))
                        }
                    }
            }
        }
    }

    private fun declaredWeaponLine(
        attackerUnit: CombatUnit,
        weaponIndex: Int,
        targetId: UnitId,
        targetInfos: List<TargetInfo>,
    ): DeclaredWeaponLine {
        val weaponName = attackerUnit.weapons.getOrNull(weaponIndex)?.name ?: "Unknown"
        val weaponInfo = targetInfos
            .firstOrNull { it.unitId == targetId }
            ?.weapons
            ?.firstOrNull { it.weaponIndex == weaponIndex }
        return DeclaredWeaponLine(
            weaponIndex = weaponIndex,
            weaponName = weaponName,
            targetNumber = weaponInfo?.targetDiceRoll ?: 13,
            successChance = weaponInfo?.successChance ?: 0,
            modifierLabels = weaponInfo?.modifiers ?: emptyList(),
        )
    }

    override fun resolveTargetPositions(targetIds: Set<UnitId>): Set<HexCoordinates> =
        targetIds.map { state.unitById(it).position }.toSet()

    override fun publicUnit(unitId: UnitId): PublicUnit? {
        val unit = state.findUnit(unitId) ?: return null
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
