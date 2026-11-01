package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tui.icon.TUI_CHECKBOX_GLYPHS
import io.archinaut.battletech.tui.view.render
import io.archinaut.tenter.color.StandardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Rect

internal class ModePanelViewTest {

    @Test
    fun `highlight follows the cursor while the checkbox follows the selected mode`() {
        val buffer = render(
            ModePanelView(
                mode = SetupMode.HOT_SEAT,
                modeLocked = false,
                endpoint = null,
                opponentConnected = false,
                cursorIndex = SetupMode.HOST.ordinal,
            ),
            width = 50,
            height = 6,
        )

        assertEquals(" ", buffer.get(0, 0).symbol)
        assertEquals("▶", buffer.get(0, 2).symbol)
        assertEquals(TUI_CHECKBOX_GLYPHS.checked, buffer.get(2, 0).symbol)
        assertEquals(TUI_CHECKBOX_GLYPHS.unchecked, buffer.get(2, 2).symbol)
        assertEquals(StandardRole.TEXT_PRIMARY, buffer.get(4, 0).fg)
        assertEquals(StandardRole.ACCENT, buffer.get(4, 2).fg)
        assertEquals("    Both players share this terminal", buffer.region(Rect(0, 1, 50, 1)).line(0))
        assertEquals("    Other players connect with 'join'", buffer.region(Rect(0, 3, 50, 1)).line(0))
    }

    @Test
    fun `compact mode view keeps only the selectable mode rows`() {
        val buffer = render(
            ModePanelView(
                mode = SetupMode.HOST,
                modeLocked = true,
                endpoint = HostEndpoint(emptyList(), 1234, "session"),
                opponentConnected = true,
                cursorIndex = SetupMode.HOST.ordinal,
                compact = true,
            ),
            width = 20,
            height = 4,
        )

        assertTrue(buffer.text().contains("hot-seat"))
        assertTrue(buffer.text().contains("host"))
        assertFalse(buffer.text().contains("Both players"))
        assertFalse(buffer.text().contains("Session:"))
    }
}
