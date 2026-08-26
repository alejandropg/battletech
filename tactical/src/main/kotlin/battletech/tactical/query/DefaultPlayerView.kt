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
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.VisibleUnit

/**
 * The one [PlayerView] implementation, built over a per-viewer [PlayerGameState] rather
 * than raw [battletech.tactical.model.GameState]. Both
 * [battletech.tactical.session.BattleSession.viewFor] and
 * [battletech.network.client.ClientGameSession.viewFor] run this exact code.
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
        val unit = state.units.byId(unitId) as CombatUnit
        val calculator = ReachabilityCalculator(state.map, state.units)
        return MovementRules.availableModes(unit).map { mode -> calculator.calculate(unit, mode) }
    }

    override fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates> =
        weaponTargeting.fireArc(attackerId, torsoFacing)

    override fun validTargets(attackerId: UnitId, torsoFacing: HexDirection): Set<UnitId> =
        weaponTargeting.validTargets(attackerId, torsoFacing)

    override fun targetInfos(attackerId: UnitId, torsoFacing: HexDirection, primaryTargetId: UnitId?): List<TargetInfo> =
        weaponTargeting.targetInfos(attackerId, torsoFacing, primaryTargetId)

    override fun physicalAttackOptions(attackerId: UnitId): List<PhysicalAttackOption> =
        physicalAttackQueries.physicalAttackOptions(attackerId)

    override fun legalTorsoFacings(unitId: UnitId): Set<HexDirection> {
        val unit = state.units.byId(unitId)
        return torsoTwistOptions(unit.facing)
    }

    override fun declaredWeaponAttacks(): List<DeclaredWeaponAttack> {
        val committedByAttacker = turnState.attack.weaponDeclarations.groupBy { it.attackerId }

        return buildList {
            for ((_, attackerIds) in committedAttackerIdsByPlayer()) {
                attackerIds.forEach { attackerId ->
                    val attackerUnit = state.units.byId(attackerId)
                    val declarations = committedByAttacker[attackerId] ?: return@forEach
                    declarations.groupBy { it.targetId }.forEach { (targetId, decls) ->
                        val weaponIndices = decls.sortedBy { it.weaponIndex }.map { it.weaponIndex }
                        val isPrimary = decls.any { it.isPrimary }
                        val weapons = weaponIndices.map { weaponIndex ->
                            declaredWeaponLine(attackerUnit, weaponIndex, targetId, isPrimary)
                        }
                        add(DeclaredWeaponAttack(attackerId, targetId, isPrimary, weapons))
                    }
                }
            }
        }
    }

    override fun declaredAttacksByPlayer(): List<DeclaredPlayerAttacks> {
        val byAttacker = declaredWeaponAttacks().groupBy { it.attackerId }
        return committedAttackerIdsByPlayer().map { (player, attackerIds) ->
            DeclaredPlayerAttacks(
                player = player,
                attackers = attackerIds.map { attackerId -> DeclaredAttacker(attackerId, byAttacker.getValue(attackerId)) },
            )
        }
    }

    /**
     * Impulse-commit player order (falling back to [PlayerId.PLAYER_1] then
     * [PlayerId.PLAYER_2] before the sequence seeds), each mapped to the attacker ids with a
     * committed declaration this impulse that that player owns, sorted by unit id. The single
     * ordering [declaredWeaponAttacks] and [declaredAttacksByPlayer] both build on, so the two
     * can never disagree with each other — or with a caller reading either.
     */
    private fun committedAttackerIdsByPlayer(): List<Pair<PlayerId, List<UnitId>>> {
        val playerOrder: List<PlayerId> = if (turnState.attack.sequence.order.isNotEmpty()) {
            turnState.attack.sequence.order.map { it.player }.distinct()
        } else {
            listOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2)
        }
        val committedByAttacker = turnState.attack.weaponDeclarations.groupBy { it.attackerId }
        return playerOrder.map { player ->
            player to committedByAttacker.keys
                .filter { id -> state.units.byId(id).owner == player }
                .sortedBy { it.value }
        }
    }

    /**
     * Builds one [DeclaredWeaponLine]. Only an attacker the viewer OWNS gets a to-hit
     * prediction — the math needs that attacker's gunnery/heat/sensor crits, which a foreign
     * attacker's projection never carries — hence [DeclaredWeaponLine.Undisclosed] rather than
     * a fabricated number. The breakdown is computed directly from the committed declaration
     * ([WeaponTargeting.breakdownFor]) rather than looked up in [PlayerView.targetInfos], so it
     * reflects exactly what resolution will roll against even if the attacker has since
     * twisted its target out of the current firing arc. The weapon NAME is public on both
     * projections ([VisibleUnit.weapons] is a [battletech.tactical.unit.WeaponView] list), so
     * the observable half of the declaration survives redaction intact.
     */
    private fun declaredWeaponLine(
        attackerUnit: VisibleUnit,
        weaponIndex: Int,
        targetId: UnitId,
        isPrimary: Boolean,
    ): DeclaredWeaponLine {
        val weaponName = attackerUnit.weapons.getOrNull(weaponIndex)?.name ?: "Unknown"
        val toHit = (attackerUnit as? CombatUnit)
            ?.let { weaponTargeting.breakdownFor(attackerUnit.id, targetId, weaponIndex, isPrimary) }
        return if (toHit != null) {
            DeclaredWeaponLine.Detailed(weaponIndex = weaponIndex, weaponName = weaponName, toHit = toHit)
        } else {
            DeclaredWeaponLine.Undisclosed(weaponIndex = weaponIndex, weaponName = weaponName)
        }
    }

    override fun resolveTargetPositions(targetIds: Set<UnitId>): Set<HexCoordinates> =
        targetIds.map { state.units.byId(it).position }.toSet()
}
