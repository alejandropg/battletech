package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.screen.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Where [GamePanels.areas] places the game screen's panels for each panel mode. */
internal class GamePanelsAreasTest {

    private val body = Rect(0, 3, 120, 37)
    private val visible = setOf(GamePanelId.UNIT_STATUS, GamePanelId.LOG, GamePanelId.HELP)

    @Test
    fun `the board fills the width left of the side panels, each at its normal width`() {
        val areas = GamePanels.areas(GamePanels.build(Keybindings.DEFAULT), visible, body)

        assertEquals(
            mapOf(
                GamePanelId.BOARD to Rect(0, 3, 22, 37),
                GamePanelId.UNIT_STATUS to Rect(22, 3, 28, 37),
                GamePanelId.LOG to Rect(50, 3, 28, 37),
                GamePanelId.HELP to Rect(78, 3, 42, 37),
            ),
            areas,
        )
    }

    @Test
    fun `a minimized side panel shrinks to its stub and the board takes the freed width`() {
        val panels = GamePanels.build(Keybindings.DEFAULT)
        panels.focus(GamePanelId.LOG)
        panels.cycleFocusedMode(-1)

        val areas = GamePanels.areas(panels, visible, body)

        assertEquals(Rect(0, 3, 43, 37), areas[GamePanelId.BOARD])
        assertEquals(Rect(71, 3, GamePanels.MINIMIZED_WIDTH, 37), areas[GamePanelId.LOG])
    }

    @Test
    fun `a maximized side panel takes the whole area alone`() {
        val panels = GamePanels.build(Keybindings.DEFAULT)
        panels.focus(GamePanelId.UNIT_STATUS)
        panels.cycleFocusedMode(1)

        assertEquals(mapOf(GamePanelId.UNIT_STATUS to body), GamePanels.areas(panels, visible, body))
    }

    @Test
    fun `hidden side panels take no space`() {
        val areas = GamePanels.areas(GamePanels.build(Keybindings.DEFAULT), emptySet(), body)

        assertEquals(mapOf(GamePanelId.BOARD to body), areas)
    }
}
