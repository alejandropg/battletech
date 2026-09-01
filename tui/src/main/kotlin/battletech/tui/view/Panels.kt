package battletech.tui.view

import battletech.tui.game.GamePanelId
import battletech.tui.input.ChromeAction
import battletech.tui.input.Keybindings
import battletech.tui.view.record.MechRecordSheetView
import tenter.panel.Panel
import tenter.panel.PanelSet
import tenter.panel.VerticalTitleView
import tenter.view.HelpView
import tenter.view.View

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
            // Never consulted for the main slot — PanelLayout derives the board's width from
            // whatever the side panels leave over.
            normalWidth = 0,
            extent = { it.boardExtent },
            badge = keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.BOARD)),
            normal = { it.boardView },
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
                maximized = { frame -> MechRecordSheetView(frame.unitStatus.subject, frame.state.map, frame.unitStatus.pendingHeat) },
            ) { frame ->
                UnitStatusView(frame.unitStatus.subject, frame.state.map, frame.unitStatus.pendingHeat)
            },
            sidePanel(GamePanelId.LOG, LogView.TITLE, keys) { frame ->
                LogView(entries = frame.logEntries, state = frame.state)
            },
            Panel(
                id = GamePanelId.HELP,
                title = HelpView.TITLE,
                normalWidth = 28,
                badge = keys.badgeFor(ChromeAction.ToggleHelp),
                normal = { frame -> HelpView(frame.helpSections) },
                // No minimized state — ? dismisses HELP entirely instead (see AppState.helpOpen).
                maximized = { frame -> HelpView(frame.helpSections) },
            ),
        )

        return PanelSet(board, sides)
    }

    /** A private helper keeping the minimized/maximized declaration DRY across every side panel. */
    private fun sidePanel(
        id: GamePanelId,
        title: String,
        keys: Keybindings,
        width: Int = 28,
        maximized: ((PanelInputs) -> View)? = null,
        build: (PanelInputs) -> View,
    ): GamePanel = Panel(
        id = id,
        title = title,
        normalWidth = width,
        badge = keys.badgeFor(ChromeAction.FocusPanel(id)),
        normal = build,
        minimized = { VerticalTitleView(title) },
        maximized = maximized ?: build,
    )
}
