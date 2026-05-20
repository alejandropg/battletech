package battletech.tactical.session

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.command.GameCommand
import battletech.tactical.dice.DiceRoller
import battletech.tactical.event.HeatDissipated
import battletech.tactical.model.GameState

/**
 * System phase. On entry, applies heat dissipation across all units.
 * Completes immediately. Accepts no commands.
 */
public class HeatPhaseHandler : PhaseHandler {

    override val phase: TurnPhase = TurnPhase.HEAT

    override fun activePlayer(turn: TurnState): PlayerId? = null

    override fun accepts(command: GameCommand, turn: TurnState): Boolean = false

    override fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome = PhaseOutcome(state, turn, emptyList())

    override fun isComplete(turn: TurnState): Boolean = true

    override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        val before = state.units.associate { it.id to it.currentHeat }
        val newState = state.applyHeatDissipation()
        val after = newState.units.associate { it.id to it.currentHeat }
        return PhaseOutcome(newState, turn, listOf(HeatDissipated(before, after)))
    }
}
