package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tui.input.ChromeAction
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.battletech.tui.view.GamePanels
import io.archinaut.battletech.tui.view.helpPanel
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.screen.Layout
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.widget.panel.Panel
import io.archinaut.tenter.widget.panel.PanelMode
import io.archinaut.tenter.widget.panel.PanelSet

/**
 * The setup screen's panels: what each one shows ([build]) and where each one goes ([areas]).
 *
 * [build] returns a fresh set of instances per call — each [Panel] is stateful, and must live for
 * exactly one [SetupWorkspace]'s lifetime, never longer (see [Panel]'s KDoc).
 */
internal object SetupPanels {
    fun build(keys: Keybindings): PanelSet<SetupPanelId, SetupPanelInputs> = PanelSet(
        listOf(
            Panel(
                id = SetupPanelId.MODE,
                title = "MODE",
                badge = keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.MODE)).toString(),
                normal = { it.modeView },
                minimized = { it.minimizedModeView },
            ),
            Panel(
                id = SetupPanelId.MAP,
                title = "MAP",
                badge = keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.MAP)).toString(),
                normal = { it.mapView },
                maximized = { it.maximizedMapView },
                minimized = { it.mapView },
            ),
            Panel(
                id = SetupPanelId.PLAYER_1,
                title = "PLAYER 1",
                badge = keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.PLAYER_1)).toString(),
                normal = { it.player1View },
                maximized = { it.maximizedPlayer1View },
                minimized = { it.minimizedPlayer1View },
            ),
            Panel(
                id = SetupPanelId.PLAYER_2,
                title = "PLAYER 2",
                badge = keys.badgeFor(ChromeAction.FocusPanel(SetupPanelId.PLAYER_2)).toString(),
                normal = { it.player2View },
                maximized = { it.maximizedPlayer2View },
                minimized = { it.minimizedPlayer2View },
            ),
            helpPanel(
                id = SetupPanelId.HELP,
                badge = keys.badgeFor(ChromeAction.ToggleHelp),
                sections = { it.helpSections },
            ),
        ),
    )

    /**
     * Where each panel goes in [area] this frame. The four [GRID] panels share [GRID]'s columns
     * equally — a hidden one keeps its column empty — while a minimized one shrinks to its compact
     * list's width, which [inputs] measures from this frame's content. HELP trails at the game
     * screen's HELP width. A maximized panel takes the whole area alone.
     */
    fun areas(
        panels: PanelSet<SetupPanelId, SetupPanelInputs>,
        inputs: SetupPanelInputs,
        visible: Set<SetupPanelId>,
        area: Rect,
    ): Map<SetupPanelId, Rect> {
        visible.firstOrNull { panels.modeOf(it) == PanelMode.MAXIMIZED }?.let { return mapOf(it to area) }

        val grid = GRID.filter { it in visible }
        val columns = grid.map { id ->
            if (panels.modeOf(id) == PanelMode.MINIMIZED) Constraint.Length(inputs.minimizedWidth(id)) else Constraint.Fill(1)
        }
        val reserved = List(GRID.size - grid.size) { Constraint.Fill(1) }
        val help = listOfNotNull(SetupPanelId.HELP.takeIf { it in visible })
        val rects = Layout.horizontal(
            columns + reserved + help.map { Constraint.Length(GamePanels.HELP_WIDTH) },
        ).split(area)
        return (grid.zip(rects) + help.zip(rects.takeLast(help.size))).toMap()
    }

    /** The proportional setup grid, left to right. */
    private val GRID: List<SetupPanelId> = listOf(
        SetupPanelId.MODE,
        SetupPanelId.MAP,
        SetupPanelId.PLAYER_1,
        SetupPanelId.PLAYER_2,
    )
}
