package battletech.tui.view

import battletech.tui.aGameState
import battletech.tui.anAppState
import battletech.tui.game.GamePanelId
import battletech.tui.game.phase.MovementPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.panel.PanelState
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer

internal class PanelsTest {

    private val inputs = PanelInputs(anAppState(MovementPhase.SelectingUnit, gameState = aGameState()))
    private val allSideIds = GamePanelId.entries.toSet() - GamePanelId.BOARD

    private fun renderAll(set: GamePanelSet): GamePanelLayout =
        set.render(Canvas.of(ScreenBuffer(300, 60)), inputs, allSideIds, reservedTop = 0)

    @Test
    fun `the board is the main panel`() {
        val layout = renderAll(Panels.build())

        assertEquals(GamePanelId.BOARD, layout.main?.panel?.id)
    }

    @Test
    fun `the board declares only NORMAL`() {
        val layout = renderAll(Panels.build())

        assertEquals(listOf(PanelState.NORMAL), layout.main!!.panel.states)
    }

    @Test
    fun `every side panel except HELP declares MINIMIZED, NORMAL, and MAXIMIZED`() {
        val layout = renderAll(Panels.build())

        for (slot in layout.sides.filter { it.panel.id != GamePanelId.HELP }) {
            assertEquals(
                listOf(PanelState.MINIMIZED, PanelState.NORMAL, PanelState.MAXIMIZED),
                slot.panel.states,
                "${slot.panel.id} should declare all three states",
            )
        }
    }

    @Test
    fun `HELP declares NORMAL and MAXIMIZED but not MINIMIZED`() {
        val layout = renderAll(Panels.build())

        val help = layout.sides.first { it.panel.id == GamePanelId.HELP }
        assertEquals(listOf(PanelState.NORMAL, PanelState.MAXIMIZED), help.panel.states)
    }

    @Test
    fun `every GamePanelId appears exactly once across main and sides`() {
        val layout = renderAll(Panels.build())

        val allIds = (listOfNotNull(layout.main?.panel?.id) + layout.sides.map { it.panel.id }).toSet()
        assertEquals(GamePanelId.entries.toSet(), allIds)
    }

    @Test
    fun `build returns a fresh, independent instance every call`() {
        val first = Panels.build()
        first.focus(GamePanelId.LOG)
        first.cycleFocusedState(1) // NORMAL -> MAXIMIZED
        renderAll(first)

        val second = Panels.build()
        val layout = renderAll(second)

        val log = layout.sides.first { it.panel.id == GamePanelId.LOG }
        assertEquals(PanelState.NORMAL, log.panel.state, "a later Panels.build() must not see an earlier call's state")
    }
}
