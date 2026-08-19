package battletech.tui.view

import battletech.tui.game.GamePanelId
import tenter.panel.Panel
import tenter.panel.PanelSet
import tenter.panel.VerticalTitleView
import tenter.view.HelpView
import tenter.view.View

/**
 * Builds this run's [PanelSet]: the tactical board as the `main` panel plus every side panel, in
 * left-to-right render order (the board fills the space to their left) — a fresh set of instances
 * per call, since each [Panel] is stateful (see its KDoc) and must live for exactly one
 * [Workspace]'s lifetime, never longer. This order *is* the layout order; a panel's
 * [GamePanelId.badge] is the independent focus/identity key and need not match.
 */
internal object Panels {
    fun build(): GamePanelSet {
        val board: GamePanel = Panel(
            id = GamePanelId.BOARD,
            title = "TACTICAL MAP",
            // Never consulted for the main slot — PanelLayout derives the board's width from
            // whatever the side panels leave over.
            normalWidth = 0,
            extent = { it.boardExtent },
            normal = { it.boardView },
        )

        val sides = listOf(
            sidePanel(GamePanelId.TARGET_STATUS, TargetStatusView.TITLE) { frame ->
                TargetStatusView(frame.targetStatusUnit)
            },
            sidePanel(GamePanelId.TARGETS, TargetsView.TITLE) { frame ->
                frame.attackRender?.let {
                    TargetsView(
                        targets = it.targets,
                        weaponAssignments = it.weaponAssignments,
                        primaryTargetId = it.primaryTargetId,
                        cursorTargetIndex = it.cursorTargetIndex,
                        cursorWeaponIndex = it.cursorWeaponIndex,
                    )
                }
            },
            sidePanel(GamePanelId.DECLARED_TARGETS, DeclaredTargetsView.TITLE) { frame ->
                frame.declaredTargets?.let(::DeclaredTargetsView)
            },
            sidePanel(GamePanelId.ATTACK_RESULTS, AttackResultsView.TITLE) { frame ->
                frame.attackResults?.let(::AttackResultsView)
            },
            sidePanel(GamePanelId.UNIT_STATUS, UnitStatusView.TITLE) { frame ->
                UnitStatusView(frame.unitStatus, frame.pendingHeat)
            },
            sidePanel(GamePanelId.LOG, LogView.TITLE) { frame ->
                LogView(entries = frame.logEntries, state = frame.visibleState)
            },
            Panel(
                id = GamePanelId.HELP,
                title = HelpView.TITLE,
                normalWidth = 28,
                normal = { frame -> HelpView(frame.helpSections) },
                // No minimized state — Alt+h dismisses HELP entirely instead (see AppState.helpOpen).
                maximized = { frame -> HelpView(frame.helpSections) },
            ),
        )

        return PanelSet(board, sides)
    }

    /** A private helper keeping the minimized/maximized declaration DRY across every side panel. */
    private fun sidePanel(
        id: GamePanelId,
        title: String,
        width: Int = 28,
        build: (PanelInputs) -> View?,
    ): GamePanel = Panel(
        id = id,
        title = title,
        normalWidth = width,
        normal = build,
        minimized = { VerticalTitleView(title) },
        maximized = build,
    )
}
