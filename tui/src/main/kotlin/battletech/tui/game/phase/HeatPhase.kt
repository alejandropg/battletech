package battletech.tui.game.phase

import battletech.tactical.action.TurnPhase
import battletech.tactical.event.HeatDissipated
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.mapToTuiPhase
import com.github.ajalt.mordant.input.InputEvent

public data object HeatPhase : Phase {
    override val turnPhase: TurnPhase = TurnPhase.HEAT

    override fun handle(event: InputEvent, app: AppState, svc: PhaseServices): Transition? = null

    override fun tick(app: AppState, svc: PhaseServices): Transition {
        // Capture unit names BEFORE advance() mutates state (names don't
        // change but using a pre-image keeps the message construction symmetric
        // with the heat-before map carried by the event).
        val unitNames = app.gameState.units.associate { it.id to it.name }
        val events = app.session.advance()
        val flash = events.filterIsInstance<HeatDissipated>().firstOrNull()?.let { ev ->
            val details = ev.heatBefore
                .filterValues { it > 0 }
                .map { (id, before) -> "${unitNames[id] ?: id.value}: $before→${ev.heatAfter[id] ?: 0}" }
                .joinToString(", ")
                .ifEmpty { "No heat to dissipate" }
            FlashMessage("Heat: $details")
        }
        return Transition(
            app.copy(phase = mapToTuiPhase(app.session.currentPhase)),
            flash,
        )
    }

    override fun prompt(app: AppState): String = "Heat phase..."
}
