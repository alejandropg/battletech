package battletech.tui.setup

import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.ContentSummary
import battletech.tui.input.Keybindings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.view.HelpView
import tenter.view.text

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

    @Test
    fun `normal HELP keeps 28 columns and narrows the four setup columns proportionally`() {
        val width = 120
        val state = SetupState(
            catalog = ContentSummary(),
            registry = AssetRegistry.EMPTY,
            modeLocked = true,
            helpOpen = true,
        )
        val workspace = SetupWorkspace(Keybindings.DEFAULT)
        val panelTop = SetupBannerView.reservedHeight(width)

        workspace.render(state, width = width, height = 24, flash = null)

        assertEquals(SetupPanelId.MODE, workspace.panelAt(22, panelTop + 1))
        assertEquals(SetupPanelId.MAP, workspace.panelAt(23, panelTop + 1))
        assertEquals(SetupPanelId.PLAYER_1, workspace.panelAt(46, panelTop + 1))
        assertEquals(SetupPanelId.PLAYER_2, workspace.panelAt(69, panelTop + 1))
        assertEquals(SetupPanelId.HELP, workspace.panelAt(92, panelTop + 1))
        assertEquals(SetupPanelId.HELP, workspace.panelAt(119, panelTop + 1))
    }

    @Test
    fun `HELP preserves the four-column grid before rosters are visible`() {
        val width = 120
        val state = SetupState(
            catalog = ContentSummary(),
            registry = AssetRegistry.EMPTY,
            helpOpen = true,
        )
        val workspace = SetupWorkspace(Keybindings.DEFAULT)
        val panelTop = SetupBannerView.reservedHeight(width)

        workspace.render(state, width = width, height = 24, flash = null)

        assertEquals(SetupPanelId.MODE, workspace.panelAt(22, panelTop + 1))
        assertNull(workspace.panelAt(23, panelTop + 1))
        assertEquals(SetupPanelId.HELP, workspace.panelAt(92, panelTop + 1))
    }

    @Test
    fun `maximizing HELP takes the full setup content region`() {
        val state = SetupState(
            catalog = ContentSummary(),
            registry = AssetRegistry.EMPTY,
            modeLocked = true,
            helpOpen = true,
        )
        val workspace = SetupWorkspace(Keybindings.DEFAULT)
        val width = 120
        val panelTop = SetupBannerView.reservedHeight(width)

        workspace.focus(SetupPanelId.HELP)
        workspace.cycleFocusedState(1)
        val buffer = workspace.render(state, width = width, height = 24, flash = null)

        assertEquals(SetupPanelId.HELP, workspace.panelAt(0, panelTop + 1))
        assertEquals(SetupPanelId.HELP, workspace.panelAt(width - 1, panelTop + 1))
        assertTrue(buffer.text().contains(HelpView.TITLE))
    }
}
