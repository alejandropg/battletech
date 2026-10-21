package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.model.content.AssetRegistry
import io.archinaut.battletech.tactical.model.content.ContentSummary
import io.archinaut.battletech.tactical.model.content.MatchPlan
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.battletech.tui.view.GamePanels
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.widget.panel.PanelSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Where [SetupPanels.areas] places the setup screen's panels for each panel mode. */
internal class SetupPanelsAreasTest {

    private val body = Rect(0, 5, 120, 19)
    private val keys = Keybindings.DEFAULT
    private val catalog = ContentSummary(maps = listOf("arena"), mechs = listOf("AS7-D"))

    private fun state(plan: MatchPlan = MatchPlan(), helpOpen: Boolean = false): SetupState = SetupState(
        catalog = catalog,
        registry = AssetRegistry.EMPTY,
        modeLocked = true,
        plan = plan,
        helpOpen = helpOpen,
    )

    private fun areas(panels: PanelSet<SetupPanelId, SetupPanelInputs>, state: SetupState): Map<SetupPanelId, Rect> =
        SetupPanels.areas(panels, SetupPanelInputs(state, keys), SetupPanelVisibility.visiblePanels(state), body)

    @Test
    fun `the four grid panels share equal columns and HELP trails at the game screen's width`() {
        val areas = areas(SetupPanels.build(keys), state(helpOpen = true))
        // 78 columns left of HELP split four ways: the 2-cell remainder goes to the earliest columns.

        assertEquals(Rect(0, 5, 20, 19), areas[SetupPanelId.MODE])
        assertEquals(Rect(59, 5, 19, 19), areas[SetupPanelId.PLAYER_2])
        assertEquals(Rect(120 - GamePanels.HELP_WIDTH, 5, GamePanels.HELP_WIDTH, 19), areas[SetupPanelId.HELP])
    }

    @Test
    fun `a minimized player panel is as wide as its compact list, and widens as its counts do`() {
        val panels = SetupPanels.build(keys)
        panels.focus(SetupPanelId.PLAYER_1)
        panels.cycleFocusedMode(-1)

        val single = areas(panels, state(MatchPlan().withCount(PlayerId.PLAYER_1, "AS7-D", 9)))
        val double = areas(panels, state(MatchPlan().withCount(PlayerId.PLAYER_1, "AS7-D", 10)))

        assertEquals(single.getValue(SetupPanelId.PLAYER_1).width + 1, double.getValue(SetupPanelId.PLAYER_1).width)
    }

    @Test
    fun `a maximized panel takes the whole area alone`() {
        val panels = SetupPanels.build(keys)
        panels.focus(SetupPanelId.MAP)
        panels.cycleFocusedMode(1)

        assertEquals(mapOf(SetupPanelId.MAP to body), areas(panels, state()))
    }
}
