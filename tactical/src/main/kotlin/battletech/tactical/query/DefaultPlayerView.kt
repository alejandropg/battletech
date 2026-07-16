package battletech.tactical.query

import battletech.tactical.attack.torsoTwistOptions
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.movement.MovementRules
import battletech.tactical.movement.ReachabilityCalculator
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.session.TurnState
import battletech.tactical.unit.UnitId

/**
 * The one [PlayerView] implementation, built over a per-viewer [PlayerGameState].
 *
 * Because it consumes the projection rather than raw [battletech.tactical.model.GameState],
 * the authoritative host ([battletech.tactical.session.BattleSession.viewFor], via
 * `stateFor(playerId)`) and a remote client
 * ([battletech.network.client.RemoteGameSession.viewFor], over its projected snapshot) run
 * this exact code — a client's answer to "what is legal right now?" cannot drift from the
 * server's, and neither can reach a field the viewer isn't entitled to.
 */
public class DefaultPlayerView(
    override val playerId: PlayerId,
    private val state: PlayerGameState,
    private val turnState: TurnState = TurnState.NULL,
) : PlayerView {

    private val weaponTargeting = WeaponTargeting(state)
    private val physicalAttackQueries = PhysicalAttackQueries(state)

    override fun legalMovementsFor(unitId: UnitId): List<ReachabilityMap> {
        // Movement legality is only ever asked about the viewer's own unit, and MP depends on
        // its heat/destroyed legs — ownUnitById fails loud if that assumption breaks.
        val unit = (state.unitById(unitId) as OwnUnit).unit
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
        val unit = state.unitById(unitId)
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
                        // Only an attacker the viewer OWNS gets a to-hit prediction: the math
                        // needs that attacker's gunnery/heat/sensor crits. For a foreign
                        // attacker targetInfos is not merely unavailable, it is uncomputable
                        // from the projection — hence the Undisclosed branch below rather than
                        // a fabricated target number.
                        val targetInfos =
                            if (attackerUnit is OwnUnit) targetInfos(attackerId, attackerUnit.torsoFacing) else null
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

    /**
     * Builds one [DeclaredWeaponLine]. [targetInfos] is non-null exactly when [attackerUnit]
     * is the viewer's own (see the call site), which is what selects
     * [DeclaredWeaponLine.Detailed] over [DeclaredWeaponLine.Undisclosed]. The weapon NAME is
     * public on both projections ([VisibleUnit.weapons] is a [PublicWeapon] list), so the
     * observable half of the declaration survives redaction intact.
     */
    private fun declaredWeaponLine(
        attackerUnit: VisibleUnit,
        weaponIndex: Int,
        targetId: UnitId,
        targetInfos: List<TargetInfo>?,
    ): DeclaredWeaponLine {
        val weaponName = attackerUnit.weapons.getOrNull(weaponIndex)?.name ?: "Unknown"
        if (targetInfos == null) {
            return DeclaredWeaponLine.Undisclosed(weaponIndex = weaponIndex, weaponName = weaponName)
        }
        val weaponInfo = targetInfos
            .firstOrNull { it.unitId == targetId }
            ?.weapons
            ?.firstOrNull { it.weaponIndex == weaponIndex }
        return DeclaredWeaponLine.Detailed(
            weaponIndex = weaponIndex,
            weaponName = weaponName,
            targetNumber = weaponInfo?.targetDiceRoll ?: 13,
            successChance = weaponInfo?.successChance ?: 0,
            modifierLabels = weaponInfo?.modifiers ?: emptyList(),
        )
    }

    override fun resolveTargetPositions(targetIds: Set<UnitId>): Set<HexCoordinates> =
        targetIds.map { state.unitById(it).position }.toSet()
}
