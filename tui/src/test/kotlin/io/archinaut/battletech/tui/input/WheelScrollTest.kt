package io.archinaut.battletech.tui.input

import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class WheelScrollTest {

    @Test
    fun `wheel ticks scroll by the wheel step`() {
        assertEquals(-WHEEL_SCROLL_STEP, wheelScrollDelta(MouseEvent(x = 1, y = 1, wheelUp = true)))
        assertEquals(WHEEL_SCROLL_STEP, wheelScrollDelta(MouseEvent(x = 1, y = 1, wheelDown = true)))
    }

    @Test
    fun `button presses never scroll`() {
        assertNull(wheelScrollDelta(MouseEvent(x = 1, y = 1, left = true)))
        assertNull(wheelScrollDelta(MouseEvent(x = 1, y = 1, right = true)))
    }
}
