package battletech.tui.setup

import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.ContentSummary
import battletech.tui.input.Keybindings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SetupWorkspaceTest {

    @Test
    fun `unlocked mode panel keeps the normal setup column width`() {
        val width = 80
        val state = SetupState(catalog = ContentSummary(), registry = AssetRegistry.EMPTY)
        val buffer = SetupWorkspace(Keybindings.DEFAULT).render(
            state = state,
            width = width,
            height = 24,
            flash = null,
        )
        val panelTop = SetupBannerView.reservedHeight(width)

        assertEquals("╮", buffer.get(width / 4 - 1, panelTop).char)
        assertEquals(" ", buffer.get(width / 4, panelTop).char)
        assertEquals(" ", buffer.get(width - 1, panelTop).char)
    }
}
