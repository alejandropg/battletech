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
 *   A [battletech.tactical.session.SessionNotice] (network join/disconnect/session-id happenings) arrives
 *   through this same branch, since it's just another [GameEvent] recorded in the game log.
 * - [Quit]: the user has requested to quit; the loop should drain and exit.
 */
internal sealed interface UiEvent {
    data class Input(val event: InputEvent) : UiEvent
    data class Resized(val size: Size) : UiEvent
    data class FlashExpired(val generation: Long) : UiEvent
    data class Session(val event: GameEvent) : UiEvent
    data object Quit : UiEvent
}
