package battletech.tui.setup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
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
            width = 30,
            height = 5,
        )

        assertEquals(" ", buffer.get(0, 0).char)
        assertEquals("▶", buffer.get(0, 1).char)
        assertEquals(checkboxIcon(CheckState.CHECKED), buffer.get(2, 0).char)
        assertEquals(checkboxIcon(CheckState.UNCHECKED), buffer.get(2, 1).char)
        assertEquals(ChromeRole.TEXT_PRIMARY, buffer.get(4, 0).style.fg)
        assertEquals(ChromeRole.ACCENT, buffer.get(4, 1).style.fg)
    }
}
