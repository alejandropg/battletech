package battletech.tui.game

import com.github.ajalt.mordant.input.InputEvent

public interface ActivePhase {
    public val state: PhaseState
    public fun processEvent(
        event: InputEvent,
        appState: AppState,
    ): HandleResult?
}
