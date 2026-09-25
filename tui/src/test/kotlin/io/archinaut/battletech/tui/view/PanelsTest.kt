package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.input.Keybindings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.archinaut.tenter.panel.PanelState
import io.archinaut.tenter.panel.declaredCycle

/**
 * What [Panels.build] DECLARES — which panel is main, and which [PanelState]s each one offers —
 * observed by driving the built [GamePanelSet] through its own seam rather than reading its
 * declarations back through accessors that would exist only for this test.
 */
internal class PanelsTest {

    @Test
    fun `the board is the main panel`() {
        // Main-ness is observable as two facts: a fresh set focuses it, and it declares only
        // NORMAL (the next test pins the cycle) — the two things PanelSet.mainAndSides guarantees.
        assertEquals(GamePanelId.BOARD, Panels.build(Keybindings.DEFAULT).focused)
    }

    @Test
    fun `the board never leaves NORMAL`() {
        assertEquals(
            List(4) { PanelState.NORMAL },
            Panels.build(Keybindings.DEFAULT).declaredCycle(GamePanelId.BOARD),
        )
    }

    @Test
    fun `every side panel except HELP cycles MINIMIZED, NORMAL, and MAXIMIZED`() {
        for (id in GamePanelId.entries.filter { it != GamePanelId.BOARD && it != GamePanelId.HELP }) {
            assertEquals(
                listOf(PanelState.MAXIMIZED, PanelState.MINIMIZED, PanelState.NORMAL, PanelState.MAXIMIZED),
                Panels.build(Keybindings.DEFAULT).declaredCycle(id),
                "$id should cycle through all three states",
            )
        }
    }

    @Test
    fun `HELP cycles NORMAL and MAXIMIZED but never minimizes`() {
        assertEquals(
            listOf(PanelState.MAXIMIZED, PanelState.NORMAL, PanelState.MAXIMIZED, PanelState.NORMAL),
            Panels.build(Keybindings.DEFAULT).declaredCycle(GamePanelId.HELP),
        )
    }

    @Test
    fun `every GamePanelId is declared in the set`() {
        val set = Panels.build(Keybindings.DEFAULT)

        // stateOf rejects an id the set does not declare, and the factory rejects duplicates, so
        // querying every enum value proves the mapping is total and one-to-one.
        assertEquals(
            GamePanelId.entries.associateWith { PanelState.NORMAL },
            GamePanelId.entries.associateWith { set.stateOf(it) },
        )
    }

    @Test
    fun `build returns a fresh, independent instance every call`() {
        val first = Panels.build(Keybindings.DEFAULT)
        first.focus(GamePanelId.LOG)
        first.cycleFocusedState(1) // NORMAL -> MAXIMIZED

        val second = Panels.build(Keybindings.DEFAULT)

        assertEquals(PanelState.NORMAL, second.stateOf(GamePanelId.LOG), "a later Panels.build(Keybindings.DEFAULT) must not see an earlier call's state")
        assertEquals(GamePanelId.BOARD, second.focused, "nor an earlier call's focus")
    }
}
