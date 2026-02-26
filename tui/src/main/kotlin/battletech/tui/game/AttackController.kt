package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.CombatUnit
import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameState
import battletech.tui.input.InputAction

public class AttackController(
    private val actionQueryService: ActionQueryService,
) {
    public fun enter(unit: CombatUnit, phase: TurnPhase, gameState: GameState): PhaseState.Attack {
        val report = actionQueryService.getAttackActions(unit, phase, gameState)
        val available = report.actions.filterIsInstance<AvailableAction>()

        return PhaseState.Attack(
            unitId = unit.id,
            attackPhase = phase,
            prompt = if (available.isEmpty()) {
                "No attacks available. Press Enter to skip."
            } else {
                "Select attack for ${unit.name}"
            },
        )
    }

    public fun handle(
        action: InputAction,
        state: PhaseState.Attack,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is InputAction.Cancel -> PhaseOutcome.Cancelled
        is InputAction.Confirm -> PhaseOutcome.Continue(state) // stub until attack selection is implemented
        else -> PhaseOutcome.Continue(state)
    }
}
