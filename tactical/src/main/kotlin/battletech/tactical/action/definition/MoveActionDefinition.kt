package battletech.tactical.action.definition

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionDefinition
import battletech.tactical.action.ActionPreview
import battletech.tactical.action.ActionRule
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.model.GameState
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityCalculator

public class MoveActionDefinition : ActionDefinition {

    override val phase: TurnPhase = TurnPhase.MOVEMENT

    override val name: String = "Move"

    override val rules: List<ActionRule> = emptyList()

    override fun expand(actor: Unit, gameState: GameState): List<ActionContext> {
        val contexts = mutableListOf<ActionContext>()
        if (actor.walkingMP > 0) {
            contexts.add(ActionContext(actor = actor, movementMode = MovementMode.WALK, gameState = gameState))
        }
        if (actor.runningMP > 0) {
            contexts.add(ActionContext(actor = actor, movementMode = MovementMode.RUN, gameState = gameState))
        }
        if (actor.jumpMP > 0) {
            contexts.add(ActionContext(actor = actor, movementMode = MovementMode.JUMP, gameState = gameState))
        }
        return contexts
    }

    override fun preview(context: ActionContext): ActionPreview {
        val mode = context.movementMode ?: return ActionPreview()
        val calculator = ReachabilityCalculator(context.gameState.map, context.gameState.units)
        val reachability = calculator.calculate(context.actor, mode, HexDirection.N)
        return ActionPreview(reachability = reachability)
    }

    override fun successChance(context: ActionContext): Int = 100

    override fun actionName(context: ActionContext): String {
        val modeName = when (context.movementMode) {
            MovementMode.WALK -> "Walk"
            MovementMode.RUN -> "Run"
            MovementMode.JUMP -> "Jump"
            null -> "Move"
        }
        return "$modeName ${context.actor.name}"
    }
}
