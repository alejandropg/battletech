package battletech.tactical.action.movement

import battletech.tactical.action.CombatUnit
import battletech.tactical.model.GameState

public interface MovementDefinition {
    public val name: String

    public fun expand(actor: CombatUnit, gameState: GameState): List<MovementContext>
    public fun preview(context: MovementContext): MovementPreview
    public fun actionName(context: MovementContext): String
}
