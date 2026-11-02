package io.archinaut.battletech.tui.input

import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class LegacyPanelMouseTest {

    @Test
    fun `the TUI fallback keeps left and right side-panel scrolling`() {
        assertEquals(
            -WHEEL_SCROLL_STEP,
            legacyPanelScrollDelta(MouseEvent(x = 1, y = 1, left = true), sidePanel = true),
        )
        assertEquals(
            WHEEL_SCROLL_STEP,
            legacyPanelScrollDelta(MouseEvent(x = 1, y = 1, right = true), sidePanel = true),
        )
    }

    @Test
    fun `the TUI fallback never turns board buttons into scrolling`() {
        assertNull(legacyPanelScrollDelta(MouseEvent(x = 1, y = 1, left = true), sidePanel = false))
        assertEquals(
            -WHEEL_SCROLL_STEP,
            legacyPanelScrollDelta(MouseEvent(x = 1, y = 1, wheelUp = true), sidePanel = false),
        )
    }
}
