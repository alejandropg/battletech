package io.archinaut.tenter.input

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

            assertEquals(-MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event))
        }

        @Test
        fun `wheelUp not over panel returns negative delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelUp = true)

            assertEquals(-MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event))
        }

        @Test
        fun `wheelDown over panel returns positive delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelDown = true)

            assertEquals(MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event))
        }

        @Test
        fun `wheelDown not over panel returns positive delta`() {
            val event = MouseEvent(x = 10, y = 10, wheelDown = true)

            assertEquals(MouseInput.SCROLL_STEP, MouseInput.scrollDelta(event))
        }

        @Test
        fun `left press never becomes a scroll delta`() {
            val event = MouseEvent(x = 10, y = 10, left = true)

            assertNull(MouseInput.scrollDelta(event))
        }

        @Test
        fun `right press never becomes a scroll delta`() {
            val event = MouseEvent(x = 10, y = 10, right = true)

            assertNull(MouseInput.scrollDelta(event))
        }

        @Test
        fun `left press not over panel returns null`() {
            val event = MouseEvent(x = 10, y = 10, left = true)

            assertNull(MouseInput.scrollDelta(event))
        }

        @Test
        fun `right press not over panel returns null`() {
            val event = MouseEvent(x = 10, y = 10, right = true)

            assertNull(MouseInput.scrollDelta(event))
        }

        @Test
        fun `release event over panel returns null`() {
            val event = MouseEvent(x = 10, y = 10)

            assertNull(MouseInput.scrollDelta(event))
        }

        @Test
        fun `wheel step can be configured`() {
            val event = MouseEvent(x = 10, y = 10, wheelUp = true)

            assertEquals(-4, MouseInput.scrollDelta(event, step = 4))
        }

        @Test
        fun `nonpositive step is rejected`() {
            val event = MouseEvent(x = 10, y = 10, wheelUp = true)

            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
                MouseInput.scrollDelta(event, step = 0)
            }
        }
    }
}
