package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.widget.panel.PanelMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * What [GamePanels.build] DECLARES — which panel comes first, and which [PanelMode]s each one offers —
 * observed by driving the built [io.archinaut.tenter.widget.panel.PanelSet] through its own seam rather than reading its
 * declarations back through accessors that would exist only for this test.
 */
internal class PanelsTest {

    @Test
    fun `the board is focused first`() {
        // A fresh set focuses its first declared panel, and the board declares only NORMAL (the
        // next test pins the cycle).
        assertEquals(GamePanelId.BOARD, GamePanels.build(Keybindings.DEFAULT).focused)
    }

    @Test
    fun `the board never leaves NORMAL`() {
        assertEquals(
            List(4) { PanelMode.NORMAL },
            GamePanels.build(Keybindings.DEFAULT).declaredCycle(GamePanelId.BOARD),
        )
    }

    @Test
    fun `every side panel except HELP cycles MINIMIZED, NORMAL, and MAXIMIZED`() {
        for (id in GamePanelId.entries.filter { it != GamePanelId.BOARD && it != GamePanelId.HELP }) {
            assertEquals(
                listOf(PanelMode.MAXIMIZED, PanelMode.MINIMIZED, PanelMode.NORMAL, PanelMode.MAXIMIZED),
                GamePanels.build(Keybindings.DEFAULT).declaredCycle(id),
                "$id should cycle through all three states",
            )
        }
    }

    @Test
    fun `HELP cycles NORMAL and MAXIMIZED but never minimizes`() {
        assertEquals(
            listOf(PanelMode.MAXIMIZED, PanelMode.NORMAL, PanelMode.MAXIMIZED, PanelMode.NORMAL),
            GamePanels.build(Keybindings.DEFAULT).declaredCycle(GamePanelId.HELP),
        )
    }

    @Test
    fun `every GamePanelId is declared in the set`() {
        val set = GamePanels.build(Keybindings.DEFAULT)

        // modeOf rejects an id the set does not declare, and the factory rejects duplicates, so
        // querying every enum value proves the mapping is total and one-to-one.
        assertEquals(
            GamePanelId.entries.associateWith { PanelMode.NORMAL },
            GamePanelId.entries.associateWith { set.modeOf(it) },
        )
    }

    @Test
    fun `build returns a fresh, independent instance every call`() {
        val first = GamePanels.build(Keybindings.DEFAULT)
        first.focus(GamePanelId.LOG)
        first.cycleFocusedMode(1) // NORMAL -> MAXIMIZED

        val second = GamePanels.build(Keybindings.DEFAULT)

        assertEquals(PanelMode.NORMAL, second.modeOf(GamePanelId.LOG), "a later GamePanels.build(Keybindings.DEFAULT) must not see an earlier call's state")
        assertEquals(GamePanelId.BOARD, second.focused, "nor an earlier call's focus")
    }
}
