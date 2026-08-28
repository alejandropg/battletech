package battletech.tui.view

import battletech.tui.game.GamePanelId
import battletech.tui.input.Keybindings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.panel.PanelState

/**
 * What [Panels.build] DECLARES — which panel is main, and which [PanelState]s each one offers.
 * Read straight off the built [GamePanelSet] rather than off a rendered frame: a panel's builder
 * now always returns a view (see [tenter.panel.Panel]'s KDoc), so rendering one would demand a
 * frame carrying every panel's data, which has nothing to do with what this test is about.
 */
internal class PanelsTest {

    @Test
    fun `the board is the main panel`() {
        assertEquals(GamePanelId.BOARD, Panels.build(Keybindings.DEFAULT).main.id)
    }

    @Test
    fun `the board declares only NORMAL`() {
        assertEquals(listOf(PanelState.NORMAL), Panels.build(Keybindings.DEFAULT).main.states)
    }

    @Test
    fun `every side panel except HELP declares MINIMIZED, NORMAL, and MAXIMIZED`() {
        for (panel in Panels.build(Keybindings.DEFAULT).sides.filter { it.id != GamePanelId.HELP }) {
            assertEquals(
                listOf(PanelState.MINIMIZED, PanelState.NORMAL, PanelState.MAXIMIZED),
                panel.states,
                "${panel.id} should declare all three states",
            )
        }
    }

    @Test
    fun `HELP declares NORMAL and MAXIMIZED but not MINIMIZED`() {
        val help = Panels.build(Keybindings.DEFAULT).sides.first { it.id == GamePanelId.HELP }

        assertEquals(listOf(PanelState.NORMAL, PanelState.MAXIMIZED), help.states)
    }

    @Test
    fun `every GamePanelId appears exactly once across main and sides`() {
        val set = Panels.build(Keybindings.DEFAULT)

        val allIds = (listOf(set.main.id) + set.sides.map { it.id })
        assertEquals(GamePanelId.entries, allIds.distinct().sorted(), "every id, no duplicates")
    }

    @Test
    fun `build returns a fresh, independent instance every call`() {
        val first = Panels.build(Keybindings.DEFAULT)
        first.focus(GamePanelId.LOG)
        first.cycleFocusedState(1) // NORMAL -> MAXIMIZED

        val second = Panels.build(Keybindings.DEFAULT)

        val log = second.sides.first { it.id == GamePanelId.LOG }
        assertEquals(PanelState.NORMAL, log.state, "a later Panels.build(Keybindings.DEFAULT) must not see an earlier call's state")
        assertEquals(GamePanelId.BOARD, second.focused, "nor an earlier call's focus")
    }
}
