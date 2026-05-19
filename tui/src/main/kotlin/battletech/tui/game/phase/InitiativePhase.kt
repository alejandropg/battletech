package battletech.tui.game.phase

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.event.InitiativeRolled
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.mapToTuiPhase

public data object InitiativePhase : Phase {
    override val turnPhase: TurnPhase = TurnPhase.INITIATIVE

    override fun tick(app: AppState, svc: PhaseServices): Transition {
        // Drive one phase step: InitiativePhaseHandler.onEntry rolls and seeds
        // the movement sequence, then the handler reports complete so the
        // session advances to MOVEMENT.
        val events = app.session.advance()
        val flash = events.filterIsInstance<InitiativeRolled>().firstOrNull()?.let { rolled ->
            val p1 = rolled.initiative.rolls[PlayerId.PLAYER_1]!!
            val p2 = rolled.initiative.rolls[PlayerId.PLAYER_2]!!
            val loserName = if (rolled.initiative.loser == PlayerId.PLAYER_1) "P1" else "P2"
            FlashMessage("Initiative: P1 rolled $p1, P2 rolled $p2 — $loserName moves first")
        }
        return Transition(
            app = app.copy(phase = mapToTuiPhase(app.session.currentPhase)),
            flash = flash,
        )
    }

    override fun prompt(app: AppState): String = "Rolling initiative..."
}
