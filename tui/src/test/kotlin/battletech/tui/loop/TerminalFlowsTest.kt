package battletech.tui.loop

import com.github.ajalt.mordant.input.KeyboardEvent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class TerminalFlowsTest {

    private fun key(key: String, ctrl: Boolean = false): KeyboardEvent =
        KeyboardEvent(key, ctrl = ctrl)

    private val ctrlC = key("c", ctrl = true)
    private val someKey = key("ArrowUp")
    private val otherKey = key("Enter")

    @Nested
    inner class UiInputEventsTest {

        @Test
        fun `key before ctrl+c emits Input then Quit, ctrl+c is not forwarded as Input`() = runTest {
            val events = uiInputEvents(flowOf(someKey, ctrlC)).toList()

            assertEquals(listOf(UiEvent.Input(someKey), UiEvent.Quit), events)
        }

        @Test
        fun `flow that completes without quit key still ends with Quit`() = runTest {
            val events = uiInputEvents(flowOf(someKey, otherKey)).toList()

            assertEquals(listOf(UiEvent.Input(someKey), UiEvent.Input(otherKey), UiEvent.Quit), events)
        }

        @Test
        fun `quit placed before other keys suppresses the rest`() = runTest {
            val events = uiInputEvents(flowOf(ctrlC, otherKey)).toList()

            assertEquals(listOf(UiEvent.Quit), events)
        }
    }
}
