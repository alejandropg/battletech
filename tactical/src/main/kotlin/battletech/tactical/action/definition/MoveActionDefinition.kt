package battletech.tactical.action.definition

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionDefinition
import battletech.tactical.action.ActionPreview
import battletech.tactical.action.ActionRule
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.model.GameState

public class MoveActionDefinition : ActionDefinition {

    override val phase: TurnPhase = TurnPhase.MOVEMENT

    override val name: String = "Move"

    override val rules: List<ActionRule> = emptyList()

    override fun expand(actor: Unit, gameState: GameState): List<ActionContext> =
        listOf(ActionContext(actor = actor, gameState = gameState))

    override fun preview(context: ActionContext): ActionPreview = ActionPreview()

    override fun successChance(context: ActionContext): Int = 100

    override fun actionName(context: ActionContext): String = "Move ${context.actor.name}"
}
