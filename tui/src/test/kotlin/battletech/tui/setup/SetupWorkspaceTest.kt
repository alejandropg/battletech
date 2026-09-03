package battletech.tui.setup

import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.ContentSummary
import battletech.tactical.model.PlayerId
import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.model.content.summarize
import battletech.tactical.unit.MechModels
import battletech.tui.input.Keybindings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.view.HelpView
import tenter.view.line
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
    fun `normal HELP keeps 42 columns and narrows the four setup columns proportionally`() {
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

        assertEquals(SetupPanelId.MODE, workspace.panelAt(18, panelTop + 1))
        assertEquals(SetupPanelId.MAP, workspace.panelAt(20, panelTop + 1))
        assertEquals(SetupPanelId.PLAYER_1, workspace.panelAt(40, panelTop + 1))
        assertEquals(SetupPanelId.PLAYER_2, workspace.panelAt(59, panelTop + 1))
        assertEquals(SetupPanelId.HELP, workspace.panelAt(78, panelTop + 1))
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

        assertEquals(SetupPanelId.MODE, workspace.panelAt(18, panelTop + 1))
        assertNull(workspace.panelAt(20, panelTop + 1))
        assertEquals(SetupPanelId.HELP, workspace.panelAt(78, panelTop + 1))
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

    @Test
    fun `setup panels declare compact minimized and player maximized states`() {
        val panels = SetupPanels.build(Keybindings.DEFAULT).sides

        assertEquals(
            listOf(SetupPanelId.MODE),
            panels.filter { it.id != SetupPanelId.HELP && it.states == listOf(tenter.panel.PanelState.MINIMIZED, tenter.panel.PanelState.NORMAL) }
                .map { it.id },
        )
        assertEquals(
            listOf(SetupPanelId.MAP),
            panels.filter { it.id == SetupPanelId.MAP && it.states == listOf(tenter.panel.PanelState.MINIMIZED, tenter.panel.PanelState.NORMAL, tenter.panel.PanelState.MAXIMIZED) }
                .map { it.id },
        )
        assertEquals(
            listOf(SetupPanelId.PLAYER_1, SetupPanelId.PLAYER_2),
            panels.filter { it.id in setOf(SetupPanelId.PLAYER_1, SetupPanelId.PLAYER_2) && it.states == listOf(tenter.panel.PanelState.MINIMIZED, tenter.panel.PanelState.NORMAL, tenter.panel.PanelState.MAXIMIZED) }
                .map { it.id },
        )
    }

    @Test
    fun `minimized setup panels fit their rows and keep declaration order`() {
        val width = 100
        val state = SetupState(
            catalog = ContentSummary(
                maps = listOf("a", "long-map"),
                mechs = listOf("X", "long-mech"),
            ),
            registry = AssetRegistry.EMPTY,
            modeLocked = true,
            plan = MatchPlan()
                .withCount(PlayerId.PLAYER_1, "long-mech", 12)
                .withCount(PlayerId.PLAYER_2, "long-mech", 1),
        )
        val workspace = SetupWorkspace(Keybindings.DEFAULT)
        val panelTop = SetupBannerView.reservedHeight(width)

        workspace.focus(SetupPanelId.MAP)
        workspace.cycleFocusedState(-1)
        val buffer = workspace.render(state, width = width, height = 24, flash = null)

        assertEquals(SetupPanelId.MODE, workspace.panelAt(0, panelTop + 1))
        assertEquals(SetupPanelId.MAP, workspace.panelAt(28, panelTop + 1))
        assertEquals(SetupPanelId.MAP, workspace.panelAt(43, panelTop + 1))
        assertEquals(SetupPanelId.PLAYER_1, workspace.panelAt(44, panelTop + 1))
        assertTrue(buffer.line(panelTop + 3, x = 30, width = 12).contains("long-map"))
    }

    @Test
    fun `minimized map and player panels do not overlap a remainder column`() {
        val state = SetupState(
            catalog = ContentSummary(
                maps = listOf("long-map"),
                mechs = listOf("long-mech"),
            ),
            registry = AssetRegistry.EMPTY,
            modeLocked = true,
        )
        val panelTop = SetupBannerView.reservedHeight(101)

        val mapWorkspace = SetupWorkspace(Keybindings.DEFAULT)
        mapWorkspace.focus(SetupPanelId.MAP)
        mapWorkspace.cycleFocusedState(-1)
        mapWorkspace.render(state, width = 101, height = 24, flash = null)

        assertEquals(SetupPanelId.MODE, mapWorkspace.panelAt(28, panelTop + 1))
        assertEquals(SetupPanelId.MAP, mapWorkspace.panelAt(29, panelTop + 1))

        val playerWorkspace = SetupWorkspace(Keybindings.DEFAULT)
        playerWorkspace.focus(SetupPanelId.PLAYER_2)
        playerWorkspace.cycleFocusedState(-1)
        playerWorkspace.render(state, width = 102, height = 24, flash = null)
        val playerPanelTop = SetupBannerView.reservedHeight(102)

        assertEquals(SetupPanelId.MAP, playerWorkspace.panelAt(55, playerPanelTop + 1))
        assertEquals(SetupPanelId.PLAYER_1, playerWorkspace.panelAt(56, playerPanelTop + 1))
        assertEquals(SetupPanelId.PLAYER_1, playerWorkspace.panelAt(82, playerPanelTop + 1))
        assertEquals(SetupPanelId.PLAYER_2, playerWorkspace.panelAt(83, playerPanelTop + 1))
    }

    @Test
    fun `maximized player panel splits the selectable list from the full mech card`() {
        val model = MechModels["AS7-D"]
        val registry = AssetRegistry(mechs = mapOf(model.variant to model))
        val state = SetupState(
            catalog = registry.summarize(),
            registry = registry,
            modeLocked = true,
        )
        val workspace = SetupWorkspace(Keybindings.DEFAULT)
        val panelTop = SetupBannerView.reservedHeight(220)

        workspace.focus(SetupPanelId.PLAYER_1)
        workspace.cycleFocusedState(1)
        val buffer = workspace.render(state, width = 220, height = 100, flash = null)
        val dividerRow = (panelTop + 1 until buffer.height).first { buffer.line(it).contains("'MECH DATA") }
        val dividerColumn = (1 until buffer.width).first { buffer.get(it, dividerRow).char == "│" }

        assertTrue(dividerColumn > 1)
        assertEquals("│", buffer.get(dividerColumn, dividerRow).char)
        assertFourBlankColumnsAroundDivider(buffer, dividerRow, dividerColumn)
        assertTrue(buffer.text().contains("AS7-D"))
        assertTrue((dividerColumn + 1 until buffer.width).any { buffer.get(it, dividerRow).char == "'" })
        assertTrue(buffer.text().contains("WARRIOR DATA"))
        assertTrue(buffer.text().contains("INTERNAL STRUCTURE DIAGRAM"))
    }

    @Test
    fun `normal player panel shows the mech stat header and columns`() {
        val model = MechModels["AS7-D"]
        val registry = AssetRegistry(mechs = mapOf(model.variant to model))
        val state = SetupState(catalog = registry.summarize(), registry = registry, modeLocked = true)
        val workspace = SetupWorkspace(Keybindings.DEFAULT)

        val buffer = workspace.render(state, width = 220, height = 40, flash = null)

        assertTrue(buffer.text().contains("TON WLK RUN JMP"))
        val rosterLine = buffer.text().lines().first { it.contains(model.variant) }
        assertTrue(rosterLine.contains(model.tonnage.toString()))
    }

    @Test
    fun `maximized player panel scrolls through the combined list and mech card`() {
        val model = MechModels["AS7-D"]
        val registry = AssetRegistry(mechs = mapOf(model.variant to model))
        val state = SetupState(catalog = registry.summarize(), registry = registry, modeLocked = true)
        val workspace = SetupWorkspace(Keybindings.DEFAULT)

        workspace.focus(SetupPanelId.PLAYER_2)
        workspace.cycleFocusedState(1)
        val first = workspace.render(state, width = 220, height = 24, flash = null)
        workspace.scrollFocused(0, 20)
        val scrolled = workspace.render(state, width = 220, height = 24, flash = null)

        assertTrue(first.text().contains("'MECH DATA"))
        assertTrue(first.text().contains("WARRIOR DATA"))
        assertFalse(scrolled.text().contains("'MECH DATA"))
    }

    @Test
    fun `maximized map panel splits the selector from the board preview`() {
        val map = GameMap(
            hexes = mapOf(
                HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)),
                HexCoordinates(1, 0) to Hex(HexCoordinates(1, 0)),
            ),
            name = "arena",
        )
        val registry = AssetRegistry(maps = mapOf(map.name to map))
        val state = SetupState(catalog = registry.summarize(), registry = registry, modeLocked = true)
        val workspace = SetupWorkspace(Keybindings.DEFAULT)
        val panelTop = SetupBannerView.reservedHeight(120)

        workspace.focus(SetupPanelId.MAP)
        workspace.cycleFocusedState(1)
        val buffer = workspace.render(state, width = 120, height = 40, flash = null)

        assertTrue(buffer.text().contains("arena"))
        assertTrue(buffer.text().contains("01"))
        val dividerColumn = (1 until buffer.width - 1).first { column ->
            (panelTop + 1 until buffer.height).any { row -> buffer.get(column, row).char == "│" }
        }
        assertTrue(dividerColumn > 1)
        assertEquals("│", buffer.get(dividerColumn, panelTop + 2).char)
        assertFourBlankColumnsAroundDivider(buffer, panelTop + 2, dividerColumn)
    }

    @Test
    fun `maximized map panel scrolls through the board preview`() {
        val map = GameMap(
            hexes = (0 until 20).map { row -> HexCoordinates(0, row) }.associateWith { Hex(it) },
            name = "arena",
        )
        val registry = AssetRegistry(maps = mapOf(map.name to map))
        val state = SetupState(catalog = registry.summarize(), registry = registry, modeLocked = true)
        val workspace = SetupWorkspace(Keybindings.DEFAULT)

        workspace.focus(SetupPanelId.MAP)
        workspace.cycleFocusedState(1)
        val first = workspace.render(state, width = 120, height = 24, flash = null)
        workspace.scrollFocused(0, 20)
        val scrolled = workspace.render(state, width = 120, height = 24, flash = null)

        assertTrue(first.text().contains("01"))
        assertNotEquals(first.text(), scrolled.text())
    }

    @Test
    fun `maximized map panel exposes the complete board width to the viewport`() {
        val map = (0 until 15).associate { col ->
            HexCoordinates(col, 0) to Hex(HexCoordinates(col, 0))
        }.let { hexes -> GameMap(hexes = hexes, name = "wide-arena") }
        val registry = AssetRegistry(maps = mapOf(map.name to map))
        val state = SetupState(catalog = registry.summarize(), registry = registry, modeLocked = true)
        val workspace = SetupWorkspace(Keybindings.DEFAULT)

        workspace.focus(SetupPanelId.MAP)
        workspace.cycleFocusedState(1)
        val first = workspace.render(state, width = 80, height = 24, flash = null)
        workspace.scrollFocused(20, 0)
        val scrolled = workspace.render(state, width = 80, height = 24, flash = null)

        assertNotEquals(first.text(), scrolled.text())
    }

    @Test
    fun `maximized map panel previews the cursored map, not the first`() {
        val first = GameMap(
            hexes = (0 until 3).map { row -> HexCoordinates(0, row) }.associateWith { Hex(it) },
            name = "alpha",
        )
        val second = GameMap(
            hexes = (0 until 12).map { row -> HexCoordinates(0, row) }.associateWith { Hex(it) },
            name = "beta",
        )
        val registry = AssetRegistry(maps = mapOf(first.name to first, second.name to second))
        val state = SetupState(
            catalog = registry.summarize(),
            registry = registry,
            modeLocked = true,
            cursors = mapOf(SetupPanelId.MAP to 1),
        )
        val workspace = SetupWorkspace(Keybindings.DEFAULT)

        workspace.focus(SetupPanelId.MAP)
        workspace.cycleFocusedState(1)
        val buffer = workspace.render(state, width = 120, height = 80, flash = null)

        // "beta" has 12 rows to "alpha"'s 3: only the cursored map's board reaches row 11.
        assertTrue(buffer.text().contains("11"))
    }

    @Test
    fun `maximized player panel shows the cursored mech's record sheet, not the first`() {
        val first = MechModels["AS7-D"]
        val second = MechModels["WHM-6R"]
        val registry = AssetRegistry(mechs = mapOf(first.variant to first, second.variant to second))
        val state = SetupState(
            catalog = registry.summarize(),
            registry = registry,
            modeLocked = true,
            cursors = mapOf(SetupPanelId.PLAYER_1 to 1),
        )
        val workspace = SetupWorkspace(Keybindings.DEFAULT)

        workspace.focus(SetupPanelId.PLAYER_1)
        workspace.cycleFocusedState(1)
        val buffer = workspace.render(state, width = 220, height = 100, flash = null)

        // Both variants appear in the left-hand list; only the cursored one's tonnage reaches
        // the record sheet on the right.
        assertTrue(buffer.text().contains(second.tonnage.toString()))
    }

    private fun assertFourBlankColumnsAroundDivider(buffer: tenter.screen.ScreenBuffer, row: Int, dividerColumn: Int) {
        for (offset in 1..4) {
            assertEquals(" ", buffer.get(dividerColumn - offset, row).char)
            assertEquals(" ", buffer.get(dividerColumn + offset, row).char)
        }
    }
}
