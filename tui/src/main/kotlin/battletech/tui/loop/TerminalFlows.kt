package battletech.tui.loop

import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Transforms a raw [InputEvent] flow into a [UiEvent] flow, filtering out the quit keypress.
 *
 * The upstream is consumed with [takeWhile] stopping at ctrl+c. After the upstream completes
 * (either because ctrl+c was pressed or the upstream was exhausted), [onCompletion] appends
 * a single [UiEvent.Quit] so the loop always receives an explicit quit signal.
 *
 * The ctrl+c event itself is not forwarded as [UiEvent.Input] — it is absorbed by [takeWhile].
 *
 * This function is pure and unit-testable: it does not reference the terminal or any I/O.
 */
internal fun uiInputEvents(raw: Flow<InputEvent>): Flow<UiEvent> =
    raw.takeWhile { !(it is KeyboardEvent && InputMapper.isQuit(it)) }
        .map<InputEvent, UiEvent> { UiEvent.Input(it) }
        .onCompletion { emit(UiEvent.Quit) }

/**
 * Produces a [UiEvent] flow by reading terminal input events with Mordant's coroutine support.
 *
 * The normal quit path is ctrl+c: [uiInputEvents] uses [takeWhile] so that when the user presses
 * it, the producer observes the predicate returning `false` immediately at that event's own emit
 * and stops, letting [enterRawMode]'s `use` block restore raw mode at that exact point.
 *
 * That path alone isn't sufficient, though: Mordant has no raw-mode shutdown hook for *external*
 * cancellation (e.g. an unrelated exception elsewhere cancelling the collecting coroutineScope).
 * A naive infinite-blocking `readEvent()` only observes such cancellation at its next `emit`,
 * which may never come if the terminal looks frozen and the user stops typing — leaving the
 * terminal stuck in raw mode and the JVM unable to exit. [rawModePollingFlow] polls with a short
 * timeout and checks [isActive] between polls so any cancellation, from any cause, is noticed
 * within [pollTimeout] instead of depending on the next keystroke.
 */
internal fun Terminal.terminalInputEvents(mouseTracking: MouseTracking): Flow<UiEvent> =
    uiInputEvents(rawModePollingFlow(mouseTracking)).flowOn(Dispatchers.IO)

/**
 * Enters raw mode and emits input events, polling with [pollTimeout] instead of blocking
 * indefinitely so that coroutine cancellation is observed promptly even while idle. See
 * [terminalInputEvents] for why this matters.
 */
private fun Terminal.rawModePollingFlow(
    mouseTracking: MouseTracking,
    pollTimeout: Duration = 100.milliseconds,
): Flow<InputEvent> = flow {
    enterRawMode(mouseTracking).use { rawMode ->
        while (currentCoroutineContext().isActive) {
            val event = rawMode.readEventOrNull(pollTimeout) ?: continue
            emit(event)
        }
    }
}

/**
 * Produces a [UiEvent.Resized] flow by polling [Terminal.updateSize] at [period] intervals.
 *
 * The first emission fires once at startup (resulting in a harmless extra render); subsequent
 * emissions only occur when the terminal size actually changes ([distinctUntilChanged]).
 *
 * This flow intentionally has **no** [flowOn] — it must run on the collector's (main) thread
 * so that size reads and redraws are co-located and no cross-thread synchronisation is needed.
 */
internal fun Terminal.resizeEvents(period: Duration = 200.milliseconds): Flow<UiEvent> =
    flow { while (true) { emit(updateSize()); delay(period) } }
        .distinctUntilChanged()
        .map { UiEvent.Resized(it) }
