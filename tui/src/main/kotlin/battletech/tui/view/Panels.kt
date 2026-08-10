package battletech.tui.view

import battletech.tui.game.PanelId

/**
 * Builds the side panels, in left-to-right render order (the tactical board fills the space to
 * their left) — a fresh [Panel] instance per call, since each one is stateful (see [Panel]) and
 * must live for exactly one [Workspace]'s lifetime, never longer. This order *is* the layout
 * order; a panel's [PanelId.key] is the independent collapse/identity key and need not match.
 */
internal object Panels {
    fun build(): List<Panel> = listOf(
        Panel(PanelId.TARGET_STATUS, TargetStatusView.TITLE, expandedWidth = 28) { frame ->
            TargetStatusView(frame.targetStatusUnit)
        },
        Panel(PanelId.TARGETS, TargetsView.TITLE, expandedWidth = 28) { frame ->
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
        Panel(PanelId.DECLARED_TARGETS, DeclaredTargetsView.TITLE, expandedWidth = 28) { frame ->
            frame.declaredTargets?.let(::DeclaredTargetsView)
        },
        Panel(PanelId.ATTACK_RESULTS, AttackResultsView.TITLE, expandedWidth = 28) { frame ->
            frame.attackResults?.let(::AttackResultsView)
        },
        Panel(PanelId.UNIT_STATUS, UnitStatusView.TITLE, expandedWidth = 28) { frame ->
            UnitStatusView(frame.unitStatus, frame.pendingHeat)
        },
        Panel(PanelId.LOG, LogView.TITLE, expandedWidth = 28) { frame ->
            LogView(entries = frame.logEntries, state = frame.visibleState)
        },
        Panel(PanelId.HELP, HelpView.TITLE, expandedWidth = 28) { frame ->
            HelpView(frame.helpSections)
        },
    )
}
