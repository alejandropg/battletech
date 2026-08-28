package tenter.input

import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class MouseInputTest {

    @Nested
    inner class ScrollDeltaTest {
        @Test
        fun `wheelUp over panel returns negative delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelUp = true)

            assertEquals(-MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `wheelUp not over panel returns negative delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelUp = true)

            assertEquals(-MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event, overPanel = false))
        }

        @Test
        fun `wheelDown over panel returns positive delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelDown = true)

            assertEquals(MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `wheelDown not over panel returns positive delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelDown = true)

            assertEquals(MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event, overPanel = false))
        }

        @Test
        fun `left press over panel returns negative delta (Mordant wheel workaround)`() {
            val event = MouseEvent(x = 10, y = 10, left = true)

            assertEquals(-MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `right press over panel returns positive delta (Mordant wheel workaround)`() {
            val event = MouseEvent(x = 10, y = 10, right = true)

            assertEquals(MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `left press not over panel returns null`() {
            val event = MouseEvent(x = 10, y = 10, left = true)

            assertNull(MouseInput.scrollDelta(event, overPanel = false))
        }

        @Test
        fun `right press not over panel returns null`() {
            val event = MouseEvent(x = 10, y = 10, right = true)

            assertNull(MouseInput.scrollDelta(event, overPanel = false))
        }

        @Test
        fun `release event over panel returns null`() {
            val event = MouseEvent(x = 10, y = 10)

            assertNull(MouseInput.scrollDelta(event, overPanel = true))
        }

        @Test
        fun `wheelUp takes precedence over overPanel=false`() {
            val event = MouseEvent(x = 10, y = 10, wheelUp = true)

            assertEquals(-MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event, overPanel = false))
        }
    }
}
