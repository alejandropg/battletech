package battletech.tactical.action.movement

import battletech.tactical.action.CombatUnit
import battletech.tactical.model.GameState
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachabilityCalculator

public class MoveActionDefinition {

    public val name: String = "Move"

    public fun expand(actor: CombatUnit, gameState: GameState): List<MovementContext> {
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

    public fun preview(context: MovementContext): MovementPreview {
        val calculator = ReachabilityCalculator(context.gameState.map, context.gameState.units)
        val reachability = calculator.calculate(context.actor, context.movementMode)
        return MovementPreview(reachability = reachability)
    }

    public fun actionName(context: MovementContext): String {
        val modeName = when (context.movementMode) {
            MovementMode.WALK -> "Walk"
            MovementMode.RUN -> "Run"
            MovementMode.JUMP -> "Jump"
        }
        return "$modeName ${context.actor.name}"
    }
}
