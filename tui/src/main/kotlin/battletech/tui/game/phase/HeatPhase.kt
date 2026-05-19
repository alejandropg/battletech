package battletech.tui.game.phase

import battletech.tactical.action.TurnPhase
import battletech.tactical.model.applyHeatDissipation
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import com.github.ajalt.mordant.input.InputEvent

public data object HeatPhase : Phase {
    override val turnPhase: TurnPhase = TurnPhase.HEAT

    override fun handle(event: InputEvent, app: AppState, svc: PhaseServices): Transition? = null

    override fun tick(app: AppState, svc: PhaseServices): Transition {
        val oldUnits = app.gameState.units
        @Suppress("DEPRECATION")
        app.session.applyMutation { g, t -> g.applyHeatDissipation() to t }
        val newUnits = app.gameState.units
        val details = oldUnits.zip(newUnits)
            .filter { (old, _) -> old.currentHeat > 0 }
            .joinToString(", ") { (old, new) -> "${old.name}: ${old.currentHeat}→${new.currentHeat}" }
            .ifEmpty { "No heat to dissipate" }
        return Transition(
            app.copy(phase = EndPhase),
            FlashMessage("Heat: $details"),
        )
    }

    override fun prompt(app: AppState): String = "Heat phase..."
}
