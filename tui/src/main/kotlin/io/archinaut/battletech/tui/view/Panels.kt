package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.input.ChromeAction
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.battletech.tui.view.record.MechRecordSheetView
import io.archinaut.tenter.panel.Panel
import io.archinaut.tenter.panel.PanelSet
import io.archinaut.tenter.panel.VerticalTitleView

/**
 * Builds this run's [PanelSet]: the tactical board as the `main` panel plus every side panel, in
 * left-to-right render order (the board fills the space to their left) — a fresh set of instances
 * per call, since each [Panel] is stateful (see its KDoc) and must live for exactly one
 * [Workspace]'s lifetime, never longer. This order *is* the layout order; a panel's border badge
 * (derived from [keys] below) is the independent focus/identity key and need not match.
 */
internal object Panels {
    fun build(keys: Keybindings): GamePanelSet {
        val board: GamePanel = Panel(
            id = GamePanelId.BOARD,
            title = "TACTICAL MAP",
            badge = keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.BOARD)).toString(),
            normal = { Panel.Presentation.allocated(it.boardView) },
        )

        val sides = listOf(
            sidePanel(GamePanelId.TARGET_STATUS, TargetStatusView.TITLE, keys) { frame ->
                TargetStatusView(frame.targetStatusUnit)
            },
            sidePanel(GamePanelId.TARGETS, TargetsView.TITLE, keys) { frame ->
                val render = frame.attackRender
                TargetsView(
                    targets = render.targets,
                    weaponAssignments = render.weaponAssignments,
                    primaryTargetId = render.primaryTargetId,
                    cursorTargetIndex = render.cursorTargetIndex,
                    cursorWeaponIndex = render.cursorWeaponIndex,
                )
            },
            sidePanel(GamePanelId.DECLARED_TARGETS, DeclaredTargetsView.TITLE, keys) { frame ->
                DeclaredTargetsView(frame.declaredTargets)
            },
            sidePanel(GamePanelId.ATTACK_RESULTS, AttackResultsView.TITLE, keys) { frame ->
                AttackResultsView(frame.attackResults)
            },
            sidePanel(
                GamePanelId.UNIT_STATUS,
                UnitStatusView.TITLE,
                keys,
                maximized = { frame ->
                    Panel.Presentation.allocated(
                        MechRecordSheetView(frame.unitStatus.subject, frame.state.map, frame.unitStatus.pendingHeat),
                    )
                },
            ) { frame ->
                UnitStatusView(frame.unitStatus.subject, frame.state.map, frame.unitStatus.pendingHeat)
            },
            sidePanel(GamePanelId.LOG, LogView.TITLE, keys) { frame ->
                LogView(entries = frame.logEntries, state = frame.state)
            },
            helpPanel(
                id = GamePanelId.HELP,
                badge = keys.badgeFor(ChromeAction.ToggleHelp),
                sections = { it.helpSections },
                width = HELP_WIDTH,
            ),
        )

        return PanelSet.mainAndSides(board, sides)
    }

    /** A private helper keeping the minimized/maximized declaration DRY across every side panel. */
    private fun sidePanel(
        id: GamePanelId,
        title: String,
        keys: Keybindings,
        width: Int = 28,
        maximized: ((PanelInputs) -> Panel.Presentation)? = null,
        build: (PanelInputs) -> io.archinaut.tenter.view.ContentView,
    ): GamePanel = Panel(
        id = id,
        title = title,
        badge = keys.badgeFor(ChromeAction.FocusPanel(id)).toString(),
        normal = { Panel.Presentation.fixedWidth(build(it), width) },
        minimized = { Panel.Presentation.fixedWidth(VerticalTitleView(title), Panel.MINIMIZED_WIDTH) },
        maximized = maximized ?: { Panel.Presentation.allocated(build(it)) },
    )

    private const val HELP_WIDTH: Int = 42
}
