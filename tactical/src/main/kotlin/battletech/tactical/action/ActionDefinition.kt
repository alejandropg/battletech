package battletech.tactical.action

import battletech.tactical.model.GameState

public interface ActionDefinition {
    val phase: TurnPhase
    val name: String
    val rules: List<ActionRule>

    fun expand(actor: Unit, gameState: GameState): List<ActionContext>
    fun preview(context: ActionContext): ActionPreview
    fun successChance(context: ActionContext): Int
    fun actionName(context: ActionContext): String
}
