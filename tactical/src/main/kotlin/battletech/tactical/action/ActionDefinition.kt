package battletech.tactical.action

import battletech.tactical.model.GameState

public interface ActionDefinition {
    public val phase: TurnPhase
    public val name: String
    public val rules: List<ActionRule>

    public fun expand(actor: Unit, gameState: GameState): List<ActionContext>
    public fun preview(context: ActionContext): ActionPreview
    public fun successChance(context: ActionContext): Int
    public fun actionName(context: ActionContext): String
}
