package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.input.ChromeAction
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.battletech.tui.view.record.MechRecordSheetView
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.screen.Layout
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.VerticalText
import io.archinaut.tenter.widget.panel.Panel
import io.archinaut.tenter.widget.panel.PanelMode
import io.archinaut.tenter.widget.panel.PanelSet

/**
 * The game screen's panels: what each one shows ([build]) and where each one goes ([areas]).
 *
 * [build] returns a fresh set of instances per call, since each [Panel] is stateful (see its KDoc)
 * and must live for exactly one [Workspace]'s lifetime, never longer. A panel's border badge
 * (derived from `keys` below) is the independent focus/identity key and need not match its place.
 */
internal object GamePanels {
    fun build(keys: Keybindings): PanelSet<GamePanelId, PanelInputs> {
        val board: Panel<GamePanelId, PanelInputs> = Panel(
            id = GamePanelId.BOARD,
            title = "TACTICAL MAP",
            badge = keys.badgeFor(ChromeAction.FocusPanel(GamePanelId.BOARD)).toString(),
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
                maximized = { frame ->
                    MechRecordSheetView(frame.unitStatus.subject, frame.state.map, frame.unitStatus.pendingHeat)
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
            ),
        )

        return PanelSet(listOf(board) + sides)
    }

    /**
     * Where each panel goes in [area] this frame. The board fills the space left of the [visible]
     * side panels, which sit in [SIDE_ORDER] at their mode's width; a maximized side panel takes
     * the whole area alone. When the side panels don't fit, the board shrinks first, then the
     * right-most side panels.
     */
    fun areas(panels: PanelSet<GamePanelId, PanelInputs>, visible: Set<GamePanelId>, area: Rect): Map<GamePanelId, Rect> {
        val sides = SIDE_ORDER.filter { it in visible }
        sides.firstOrNull { panels.modeOf(it) == PanelMode.MAXIMIZED }?.let { return mapOf(it to area) }

        val widths = sides.map { id ->
            Constraint.Length(if (panels.modeOf(id) == PanelMode.MINIMIZED) MINIMIZED_WIDTH else normalWidth(id))
        }
        val rects = Layout.horizontal(listOf(Constraint.Fill(1)) + widths).split(area)
        return (listOf(GamePanelId.BOARD) + sides).zip(rects).toMap()
    }

    /** A private helper keeping the minimized/maximized declaration DRY across every side panel. */
    private fun sidePanel(
        id: GamePanelId,
        title: String,
        keys: Keybindings,
        maximized: ((PanelInputs) -> MeasurableWidget)? = null,
        build: (PanelInputs) -> MeasurableWidget,
    ): Panel<GamePanelId, PanelInputs> = Panel(
        id = id,
        title = title,
        badge = keys.badgeFor(ChromeAction.FocusPanel(id)).toString(),
        normal = build,
        minimized = { VerticalText(title) },
        maximized = maximized ?: build,
    )

    private fun normalWidth(id: GamePanelId): Int = if (id == GamePanelId.HELP) HELP_WIDTH else SIDE_WIDTH

    /** Side panels left to right, as they sit beside the board. */
    private val SIDE_ORDER: List<GamePanelId> = listOf(
        GamePanelId.TARGET_STATUS,
        GamePanelId.TARGETS,
        GamePanelId.DECLARED_TARGETS,
        GamePanelId.ATTACK_RESULTS,
        GamePanelId.UNIT_STATUS,
        GamePanelId.LOG,
        GamePanelId.HELP,
    )

    private const val SIDE_WIDTH: Int = 28

    /** HELP's width beside the board — shared with the setup screen's trailing HELP column. */
    const val HELP_WIDTH: Int = 42

    /** A minimized panel's border, padding, and one column for its vertical title. */
    const val MINIMIZED_WIDTH: Int = 7
}
