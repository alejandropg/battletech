package battletech.tui.setup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.view.line
import tenter.view.render
import tenter.widget.CheckState
import tenter.widget.checkboxIcon

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

        assertEquals(" ", buffer.get(0, 0).char)
        assertEquals("▶", buffer.get(0, 2).char)
        assertEquals(checkboxIcon(CheckState.CHECKED), buffer.get(2, 0).char)
        assertEquals(checkboxIcon(CheckState.UNCHECKED), buffer.get(2, 2).char)
        assertEquals(ChromeRole.TEXT_PRIMARY, buffer.get(4, 0).style.fg)
        assertEquals(ChromeRole.ACCENT, buffer.get(4, 2).style.fg)
        assertEquals("    Both players share this terminal", buffer.line(1, width = 50))
        assertEquals("    Other players connect with 'join'", buffer.line(3, width = 50))
    }
}
