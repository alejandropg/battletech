package tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class ChromeInputTest {

    private fun key(key: String, ctrl: Boolean = false, alt: Boolean = false): KeyboardEvent =
        KeyboardEvent(key, ctrl = ctrl, alt = alt)

    @Nested
    inner class IsQuitTest {
        @Test
        fun `ctrl+c is quit`() {
            assertTrue(ChromeInput.isQuit(key("c", ctrl = true)))
        }

        @Test
        fun `other keys are not quit`() {
            assertFalse(ChromeInput.isQuit(key("ArrowUp")))
            assertFalse(ChromeInput.isQuit(key("Enter")))
        }
    }

    @Nested
    inner class PanelKeyTest {
        @Test
        fun `Alt+3 returns '3'`() {
            assertEquals('3', ChromeInput.panelKey(key("3", alt = true)))
        }

        @Test
        fun `Alt+0 returns '0'`() {
            assertEquals('0', ChromeInput.panelKey(key("0", alt = true)))
        }

        @Test
        fun `Alt+h returns 'h'`() {
            assertEquals('h', ChromeInput.panelKey(key("h", alt = true)))
        }

        @Test
        fun `Alt+H is lowercased to 'h'`() {
            assertEquals('h', ChromeInput.panelKey(key("H", alt = true)))
        }

        @Test
        fun `plain 3 without alt returns null`() {
            assertNull(ChromeInput.panelKey(key("3")))
        }

        @Test
        fun `Ctrl+3 without alt returns null`() {
            assertNull(ChromeInput.panelKey(key("3", ctrl = true)))
        }
    }

    @Nested
    inner class ScrollDeltaTest {
        @Test
        fun `wheelUp over panel returns negative delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelUp = true)

            assertEquals(-ChromeInput.SCROLL_STEP, ChromeInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `wheelUp not over panel returns negative delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelUp = true)

            assertEquals(-ChromeInput.SCROLL_STEP, ChromeInput.scrollDelta(event, overPanel = false))
        }

        @Test
        fun `wheelDown over panel returns positive delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelDown = true)

            assertEquals(ChromeInput.SCROLL_STEP, ChromeInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `wheelDown not over panel returns positive delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelDown = true)

            assertEquals(ChromeInput.SCROLL_STEP, ChromeInput.scrollDelta(event, overPanel = false))
        }

        @Test
        fun `left press over panel returns negative delta (Mordant wheel workaround)`() {
            val event = MouseEvent(x = 10, y = 10, left = true)

            assertEquals(-ChromeInput.SCROLL_STEP, ChromeInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `right press over panel returns positive delta (Mordant wheel workaround)`() {
            val event = MouseEvent(x = 10, y = 10, right = true)

            assertEquals(ChromeInput.SCROLL_STEP, ChromeInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `left press not over panel returns null`() {
            val event = MouseEvent(x = 10, y = 10, left = true)

            assertNull(ChromeInput.scrollDelta(event, overPanel = false))
        }

        @Test
        fun `right press not over panel returns null`() {
            val event = MouseEvent(x = 10, y = 10, right = true)

            assertNull(ChromeInput.scrollDelta(event, overPanel = false))
        }

        @Test
        fun `release event over panel returns null`() {
            val event = MouseEvent(x = 10, y = 10)

            assertNull(ChromeInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `wheelUp takes precedence over overPanel=false`() {
            val event = MouseEvent(x = 10, y = 10, wheelUp = true)

            assertEquals(-ChromeInput.SCROLL_STEP, ChromeInput.scrollDelta(event, overPanel = false))
        }
    }

    @Nested
    inner class PanActionTest {
        @Test
        fun `h l k j pan left right up down by the given step`() {
            assertEquals(PanAction.Pan(-5, 0), ChromeInput.panAction(key("h"), stepX = 5, stepY = 3))
            assertEquals(PanAction.Pan(5, 0), ChromeInput.panAction(key("l"), stepX = 5, stepY = 3))
            assertEquals(PanAction.Pan(0, -3), ChromeInput.panAction(key("k"), stepX = 5, stepY = 3))
            assertEquals(PanAction.Pan(0, 3), ChromeInput.panAction(key("j"), stepX = 5, stepY = 3))
        }

        @Test
        fun `ctrl+arrows pan the same as hjkl`() {
            assertEquals(PanAction.Pan(-5, 0), ChromeInput.panAction(key("ArrowLeft", ctrl = true), stepX = 5, stepY = 3))
            assertEquals(PanAction.Pan(5, 0), ChromeInput.panAction(key("ArrowRight", ctrl = true), stepX = 5, stepY = 3))
            assertEquals(PanAction.Pan(0, -3), ChromeInput.panAction(key("ArrowUp", ctrl = true), stepX = 5, stepY = 3))
            assertEquals(PanAction.Pan(0, 3), ChromeInput.panAction(key("ArrowDown", ctrl = true), stepX = 5, stepY = 3))
        }

        @Test
        fun `plain arrows without ctrl are not pan actions`() {
            assertNull(ChromeInput.panAction(key("ArrowLeft"), stepX = 5, stepY = 3))
            assertNull(ChromeInput.panAction(key("ArrowUp"), stepX = 5, stepY = 3))
        }

        @Test
        fun `Home recenters`() {
            assertEquals(PanAction.Recenter, ChromeInput.panAction(key("Home"), stepX = 5, stepY = 3))
        }

        @Test
        fun `alt+h is not a pan action — callers reserve it for a panel chord`() {
            assertNull(ChromeInput.panAction(key("h", alt = true), stepX = 5, stepY = 3))
        }

        @Test
        fun `unrelated keys are not pan actions`() {
            assertNull(ChromeInput.panAction(key("Enter"), stepX = 5, stepY = 3))
            assertNull(ChromeInput.panAction(key("x"), stepX = 5, stepY = 3))
        }
    }

    @Nested
    inner class StateCycleTest {
        @Test
        fun `plus is +1`() {
            assertEquals(1, ChromeInput.stateCycle(key("+")))
        }

        @Test
        fun `minus is -1`() {
            assertEquals(-1, ChromeInput.stateCycle(key("-")))
        }

        @Test
        fun `unrelated keys return null`() {
            assertNull(ChromeInput.stateCycle(key("=")))
            assertNull(ChromeInput.stateCycle(key("Enter")))
        }

        @Test
        fun `modified plus minus return null`() {
            assertNull(ChromeInput.stateCycle(key("+", ctrl = true)))
            assertNull(ChromeInput.stateCycle(key("-", alt = true)))
        }
    }

    @Nested
    inner class ScrollActionTest {
        @Test
        fun `arrow up down are one-line scrolls`() {
            assertEquals(ScrollAction.Lines(-1), ChromeInput.scrollAction(key("ArrowUp")))
            assertEquals(ScrollAction.Lines(1), ChromeInput.scrollAction(key("ArrowDown")))
        }

        @Test
        fun `page up down are one-viewport scrolls`() {
            assertEquals(ScrollAction.Pages(-1), ChromeInput.scrollAction(key("PageUp")))
            assertEquals(ScrollAction.Pages(1), ChromeInput.scrollAction(key("PageDown")))
        }

        @Test
        fun `ctrl+ArrowUp is null — it is the board pan, not a panel scroll`() {
            assertNull(ChromeInput.scrollAction(key("ArrowUp", ctrl = true)))
        }

        @Test
        fun `alt+ArrowUp is null`() {
            assertNull(ChromeInput.scrollAction(key("ArrowUp", alt = true)))
        }

        @Test
        fun `unrelated keys return null`() {
            assertNull(ChromeInput.scrollAction(key("Enter")))
            assertNull(ChromeInput.scrollAction(key("j")))
        }
    }
}
