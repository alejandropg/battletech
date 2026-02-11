package battletech.tactical.action.movement

import battletech.tactical.action.Unit
import battletech.tactical.model.GameState
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityCalculator

public class MoveActionDefinition : MovementDefinition {

    override val name: String = "Move"

    override fun expand(actor: Unit, gameState: GameState): List<MovementContext> {
        val contexts = mutableListOf<MovementContext>()
        if (actor.walkingMP > 0) {
            contexts.add(MovementContext(actor = actor, movementMode = MovementMode.WALK, gameState = gameState))
        }
        if (actor.runningMP > 0) {
            contexts.add(MovementContext(actor = actor, movementMode = MovementMode.RUN, gameState = gameState))
        }
        if (actor.jumpMP > 0) {
            contexts.add(MovementContext(actor = actor, movementMode = MovementMode.JUMP, gameState = gameState))
        }
        return contexts
    }

    override fun preview(context: MovementContext): MovementPreview {
        val calculator = ReachabilityCalculator(context.gameState.map, context.gameState.units)
        val reachability = calculator.calculate(context.actor, context.movementMode, HexDirection.N)
        return MovementPreview(reachability = reachability)
    }

    override fun actionName(context: MovementContext): String {
        val modeName = when (context.movementMode) {
            MovementMode.WALK -> "Walk"
            MovementMode.RUN -> "Run"
            MovementMode.JUMP -> "Jump"
        }
        return "$modeName ${context.actor.name}"
    }
}
