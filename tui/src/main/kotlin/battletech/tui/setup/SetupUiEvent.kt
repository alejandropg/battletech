package battletech.tui.setup

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.rendering.Size

/** Events the setup screen's loop multiplexes — mirrors `battletech.tui.loop.UiEvent`. */
internal sealed interface SetupUiEvent {
    data class Input(val event: InputEvent) : SetupUiEvent
    data class Resized(val size: Size) : SetupUiEvent
    data class FlashExpired(val generation: Long) : SetupUiEvent
    data class Lobby(val event: LobbyEvent) : SetupUiEvent
    data object Quit : SetupUiEvent
}
