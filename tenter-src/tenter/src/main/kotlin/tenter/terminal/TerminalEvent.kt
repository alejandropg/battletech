package tenter.terminal

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.rendering.Size
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

/** A terminal-level event: raw input, a resize, or the quit signal. */
public sealed interface TerminalEvent {
    public data class Input(val event: InputEvent) : TerminalEvent
    public data class Resized(val size: Size) : TerminalEvent
    public object Quit : TerminalEvent
}

/**
 * Transforms a raw [InputEvent] flow into a [TerminalEvent] flow, filtering out the quit
 * keypress.
 *
 * The upstream is consumed with [takeWhile] stopping when [isQuit] matches a keyboard event. A
 * successful upstream completion (either because the predicate matched or the source was
 * exhausted) appends one [TerminalEvent.Quit] so callers always receive an explicit quit signal.
 * Failures and cancellation propagate without appending a successful quit event.
 *
 * The matching event itself is not forwarded as [TerminalEvent.Input] — it is absorbed by
 * [takeWhile]. The predicate is supplied by the application; this function does not define a
 * particular quit chord.
 *
 * This function is pure and unit-testable: it does not reference the terminal or any I/O.
 */
public fun terminalEvents(raw: Flow<InputEvent>, isQuit: (KeyboardEvent) -> Boolean): Flow<TerminalEvent> =
    raw.takeWhile { !(it is KeyboardEvent && isQuit(it)) }
        .map<InputEvent, TerminalEvent> { TerminalEvent.Input(it) }
        .onCompletion { cause -> if (cause == null) emit(TerminalEvent.Quit) }

/**
 * Produces a cold [TerminalEvent] flow by reading terminal input events with Mordant's coroutine
 * support. Collecting it acquires raw mode; normal completion or cancellation releases raw mode.
 * Only one input collection should be active for a terminal at a time. Compose this flow once in
 * the application's event loop rather than collecting it separately for each consumer.
 *
 * The normal quit path is the application's predicate: [terminalEvents] uses [takeWhile] so that
 * when the user presses the matching key, the producer observes the predicate returning `false`
 * immediately at that event's own emit and stops, letting [enterRawMode]'s `use` block restore raw
 * mode at that exact point.
 *
 * That path alone isn't sufficient, though: Mordant has no raw-mode shutdown hook for *external*
 * cancellation (e.g. an unrelated exception elsewhere cancelling the collecting coroutineScope).
 * A naive infinite-blocking `readEvent()` only observes such cancellation at its next `emit`,
 * which may never come if the terminal looks frozen and the user stops typing — leaving the
 * terminal stuck in raw mode and the JVM unable to exit. [rawModePollingFlow] polls with a short
 * timeout and checks [isActive] between polls so any cancellation, from any cause, is noticed
 * within [pollTimeout] instead of depending on the next keystroke.
 *
 * Terminal rendering, mutable panel/viewport state, and terminal-size observation should be
 * confined to one application execution context. The input reader may run on [Dispatchers.IO]
 * as this implementation does; callers own the dispatcher and confinement policy and should not
 * infer that a merged flow runs on one physical OS thread.
 */
public fun Terminal.inputEvents(mouseTracking: MouseTracking, isQuit: (KeyboardEvent) -> Boolean): Flow<TerminalEvent> =
    terminalEvents(rawModePollingFlow(mouseTracking), isQuit).flowOn(Dispatchers.IO)

/**
 * Enters raw mode and emits input events, polling with [pollTimeout] instead of blocking
 * indefinitely so that coroutine cancellation is observed promptly even while idle. See
 * [inputEvents] for why this matters.
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
 * Produces a cold [TerminalEvent.Resized] flow by polling [Terminal.updateSize] at [period]
 * intervals. [period] must be finite and strictly positive.
 *
 * The first emission fires once at startup (resulting in a harmless extra render); subsequent
 * emissions only occur when the terminal size actually changes.
 *
 * ### Mordant 3.0.2: `Size` has no `equals`
 * [com.github.ajalt.mordant.rendering.Size] is a plain class, not a data class, so it inherits
 * identity equality — and [Terminal.updateSize] hands back a **fresh instance every call**.
 * Deduplicating on `Size` itself therefore never suppresses anything: every poll would emit, and
 * a naive collector would re-render several times a second forever. Worse than wasteful, that
 * repaint is indistinguishable from a real resize, so anything keyed off "the terminal resized"
 * would fire on a timer and stomp the user's manual scroll. Dedupe on the dimensions instead,
 * which do compare by value, then rebuild the [Size] for the event.
 *
 * This flow intentionally has **no** [flowOn] — it runs in the collector's context so that size
 * reads can be co-located with rendering without cross-thread synchronisation. The caller owns
 * that context; a merged flow does not promise one physical OS thread.
 */
public fun Terminal.resizeEvents(period: Duration = 200.milliseconds): Flow<TerminalEvent> {
    require(period.isFinite() && period > Duration.ZERO) {
        "resize polling period must be finite and positive: $period"
    }
    return flow { while (true) { emit(updateSize()); delay(period) } }
        .map { it.width to it.height }
        .distinctUntilChanged()
        .map { (width, height) -> TerminalEvent.Resized(Size(width, height)) }
}
