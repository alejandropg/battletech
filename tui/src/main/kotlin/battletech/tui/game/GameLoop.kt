package battletech.tui.game

import battletech.tui.input.InputAction
import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameState

public class GameLoop(
    public var gameState: GameState,
    public var currentPhase: TurnPhase,
) {
    public fun handleAction(action: InputAction): GameLoopResult {
        if (action is InputAction.Quit) return GameLoopResult.Quit
        return GameLoopResult.Continue(gameState)
    }

    public fun advancePhase() {
        currentPhase = nextPhase(currentPhase)
    }

    public companion object {
        public fun nextPhase(phase: TurnPhase): TurnPhase = when (phase) {
            TurnPhase.INITIATIVE -> TurnPhase.MOVEMENT
            TurnPhase.MOVEMENT -> TurnPhase.WEAPON_ATTACK
            TurnPhase.WEAPON_ATTACK -> TurnPhase.PHYSICAL_ATTACK
            TurnPhase.PHYSICAL_ATTACK -> TurnPhase.HEAT
            TurnPhase.HEAT -> TurnPhase.END
            TurnPhase.END -> TurnPhase.INITIATIVE
        }
    }
}
