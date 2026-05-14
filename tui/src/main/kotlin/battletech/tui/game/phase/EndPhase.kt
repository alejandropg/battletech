package battletech.tui.game.phase

import battletech.tactical.action.TurnPhase
import battletech.tactical.model.resetTorsoFacings
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import com.github.ajalt.mordant.input.InputEvent

public data object EndPhase : Phase {
    override val turnPhase: TurnPhase = TurnPhase.END

    override fun handle(event: InputEvent, app: AppState, svc: PhaseServices): Transition? = null

    override fun tick(app: AppState, svc: PhaseServices): Transition {
        val resetGameState = app.gameState.resetTorsoFacings()
        return Transition(
            app.copy(gameState = resetGameState, phase = InitiativePhase),
            FlashMessage("Turn complete"),
        )
    }

    override fun prompt(app: AppState): String = "End phase..."
}
