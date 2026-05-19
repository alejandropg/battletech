package battletech.tui.game.phase

import battletech.tactical.action.TurnPhase
import battletech.tactical.event.TurnEnded
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.mapToTuiPhase
import com.github.ajalt.mordant.input.InputEvent

public data object EndPhase : Phase {
    override val turnPhase: TurnPhase = TurnPhase.END

    override fun handle(event: InputEvent, app: AppState, svc: PhaseServices): Transition? = null

    override fun tick(app: AppState, svc: PhaseServices): Transition {
        val events = app.session.advance()
        val flash = events.filterIsInstance<TurnEnded>().firstOrNull()?.let { FlashMessage("Turn complete") }
        return Transition(
            app.copy(phase = mapToTuiPhase(app.session.currentPhase)),
            flash,
        )
    }

    override fun prompt(app: AppState): String = "End phase..."
}
