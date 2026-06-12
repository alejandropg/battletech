package battletech.tui.loop

import battletech.tactical.session.GameEvent
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.rendering.Size

/**
 * Events the TUI loop multiplexes:
 * - [Input]: raw terminal keyboard/mouse input from the user.
 * - [Resized]: the terminal window changed size.
 * - [FlashExpired]: a timed flash message should be dismissed; [generation] identifies which one.
 * - [Session]: a game-session event (e.g. phase change, unit moved) — wired for remote-play preparation.
 * - [Quit]: the user has requested to quit; the loop should drain and exit.
 */
internal sealed interface UiEvent {
    data class Input(val event: InputEvent) : UiEvent
    data class Resized(val size: Size) : UiEvent
    data class FlashExpired(val generation: Long) : UiEvent
    data class Session(val event: GameEvent) : UiEvent
    data object Quit : UiEvent
}
