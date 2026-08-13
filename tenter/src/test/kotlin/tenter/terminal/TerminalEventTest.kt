package tenter.terminal

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class TerminalEventTest {

    private fun key(key: String, ctrl: Boolean = false): KeyboardEvent =
        KeyboardEvent(key, ctrl = ctrl)

    private val ctrlC = key("c", ctrl = true)
    private val someKey = key("ArrowUp")
    private val otherKey = key("Enter")

    @Nested
    inner class TerminalEventsTest {

        @Test
        fun `key before ctrl+c emits Input then Quit, ctrl+c is not forwarded as Input`() = runTest {
            val events = terminalEvents(flowOf(someKey, ctrlC)).toList()

            assertEquals(listOf(TerminalEvent.Input(someKey), TerminalEvent.Quit), events)
        }

        @Test
        fun `flow that completes without quit key still ends with Quit`() = runTest {
            val events = terminalEvents(flowOf(someKey, otherKey)).toList()

            assertEquals(listOf(TerminalEvent.Input(someKey), TerminalEvent.Input(otherKey), TerminalEvent.Quit), events)
        }

        @Test
        fun `quit placed before other keys suppresses the rest`() = runTest {
            val events = terminalEvents(flowOf(ctrlC, otherKey)).toList()

            assertEquals(listOf(TerminalEvent.Quit), events)
        }
    }

    @Nested
    inner class ResizeEventsTest {

        /**
         * Mordant's `Size` has no `equals` and `updateSize()` returns a fresh instance per call,
         * so deduplicating on `Size` itself silently does nothing: every poll emits and a naive
         * collector re-renders several times a second forever. Beyond the waste, anything keyed
         * off "the terminal resized" then fires on a timer and stomps the user's manual scroll a
         * fraction of a second after they make it.
         */
        @Test
        fun `an unchanging terminal size emits exactly one resize event, not one per poll`() = runTest {
            val terminal = Terminal(terminalInterface = TerminalRecorder(width = 80, height = 20))

            val seen = mutableListOf<TerminalEvent>()
            val job = launch { terminal.resizeEvents(period = 10.milliseconds).toList(seen) }
            advanceTimeBy(500.milliseconds) // ~50 poll periods
            job.cancel()

            assertEquals(1, seen.size, "expected a single startup emission, got: $seen")
            val only = seen.single() as TerminalEvent.Resized
            assertEquals(80, only.size.width)
            assertEquals(20, only.size.height)
        }
    }
}
