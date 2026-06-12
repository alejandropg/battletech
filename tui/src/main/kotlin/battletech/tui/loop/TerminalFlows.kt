package battletech.tui.loop

import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.coroutines.receiveEventsFlow
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
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
 * ### Critical design constraint: quit must terminate FROM WITHIN this flow
 *
 * Mordant's [receiveEventsFlow] performs an infinite blocking `readEvent` call in the background
 * and only observes coroutine cancellation at each `emit` point. Additionally, Mordant has no
 * raw-mode shutdown hook that is triggered by external cancellation — if the coroutine is cancelled
 * between two key presses, the terminal may be left in raw mode.
 *
 * For this reason, quit terminates the flow **from within**: [uiInputEvents] uses [takeWhile] so
 * that when the user presses ctrl+c, the producer coroutine (running on [Dispatchers.IO]) observes
 * the predicate returning `false` immediately at that event's own emit and stops. This allows
 * Mordant's `use` block to restore raw mode cleanly at the point of the quit key itself.
 *
 * **Never rely on external cancellation of this flow** (e.g. via `cancel()` on the collecting
 * coroutine scope) for clean shutdown; always route quit through the ctrl+c key.
 */
internal fun Terminal.terminalInputEvents(mouseTracking: MouseTracking): Flow<UiEvent> =
    uiInputEvents(receiveEventsFlow(mouseTracking)).flowOn(Dispatchers.IO)

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
