package battletech.tui.game

import battletech.tactical.action.TurnPhase
import battletech.tui.input.AttackAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

public class AttackPhase(
    private val manager: PhaseManager,
    override val state: PhaseState.Attack,
) : ActivePhase {

    override fun processEvent(event: InputEvent, appState: AppState): HandleResult? {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapAttackEvent(event)
            is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                ?.let { AttackAction.ClickTarget(it) }
        } ?: return null

        val outcome = manager.attackController.handle(action, state, appState.cursor, appState.gameState)
        val result = manager.fromOutcome(outcome, appState)

        // After returning to Idle from attack, refresh prompt with declaration progress
        val newPhaseState = result.appState.phase.state
        if (newPhaseState is PhaseState.Idle && isAttackPhase(result.appState.currentPhase)) {
            val ts = result.appState.turnState
            if (ts != null && !ts.allAttackImpulsesComplete) {
                return HandleResult(
                    result.appState.copy(phase = manager.idle(buildAttackPrompt(ts))),
                    result.flash,
                )
            }
        }
        return result
    }

    private fun isAttackPhase(phase: TurnPhase): Boolean =
        phase == TurnPhase.WEAPON_ATTACK || phase == TurnPhase.PHYSICAL_ATTACK

    private fun buildAttackPrompt(turnState: TurnState): String =
        attackPrompt(
            turnState,
            manager.attackController.declaredCount(),
            manager.attackController.currentImpulseUnitCount(),
        )
}
