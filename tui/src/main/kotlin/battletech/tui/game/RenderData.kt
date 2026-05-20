package battletech.tui.game

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.RuleResult
import battletech.tactical.action.UnitId
import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.attack.weapon.LineOfSightRule
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.movement.MovementMode
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.attack.TargetInfo
import battletech.tui.game.phase.AttackPhase
import battletech.tui.hex.HexHighlight

public data class RenderData(
    val hexHighlights: Map<HexCoordinates, HexHighlight> = emptyMap(),
    val reachableFacings: Map<HexCoordinates, Set<HexDirection>> = emptyMap(),
    val facingSelection: FacingSelection? = null,
    val torsoFacings: Map<HexCoordinates, HexDirection> = emptyMap(),
    val validTargetPositions: Set<HexCoordinates> = emptySet(),
) {
    public companion object {
        public val EMPTY: RenderData = RenderData()
    }
}

internal fun losHighlights(
    attacker: CombatUnit,
    validTargetIds: Set<UnitId>,
    gameState: GameState,
): Map<HexCoordinates, HexHighlight> =
    validTargetIds.flatMap { targetId ->
        val target = gameState.unitById(targetId) ?: return@flatMap emptyList()
        losLine(attacker, target, gameState)
    }.associateWith { HexHighlight.LINE_OF_SIGHT }

internal fun selectedLosHighlights(
    attacker: CombatUnit,
    declaring: AttackPhase.Declaring,
    targets: List<TargetInfo>,
    gameState: GameState,
): Map<HexCoordinates, HexHighlight> {
    val idx = declaring.cursorTargetIndex
    if (idx !in targets.indices) return emptyMap()
    val target = gameState.unitById(targets[idx].unitId) ?: return emptyMap()
    return losLine(attacker, target, gameState).associateWith { HexHighlight.LINE_OF_SIGHT_SELECTED }
}

private fun losLine(attacker: CombatUnit, target: CombatUnit, gameState: GameState): List<HexCoordinates> {
    val context = PhysicalAttackContext(actor = attacker, gameState = gameState, target = target)
    if (LineOfSightRule().evaluate(context) != RuleResult.Satisfied) return emptyList()
    return attacker.position.lineTo(target.position).drop(1).dropLast(1)
}

internal fun reachabilityHighlights(reachability: ReachabilityMap): Map<HexCoordinates, HexHighlight> {
    val highlight = when (reachability.mode) {
        MovementMode.WALK -> HexHighlight.REACHABLE_WALK
        MovementMode.RUN -> HexHighlight.REACHABLE_RUN
        MovementMode.JUMP -> HexHighlight.REACHABLE_JUMP
    }
    return reachability.destinations.associate { it.position to highlight }
}

internal fun pathHighlights(path: List<HexCoordinates>?): Map<HexCoordinates, HexHighlight> {
    if (path == null) return emptyMap()
    return path.dropLast(1).associateWith { HexHighlight.PATH }
}
