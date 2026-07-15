package battletech.tui.view

import battletech.tui.game.PanelId

/**
 * The side-panel registry, in left-to-right render order (the tactical board
 * fills the space to their left). This list *is* the layout order; a panel's
 * [PanelId.index] is the independent collapse/identity key and need not match.
 */
internal object Panels {
    val ordered: List<Panel> = listOf(
        Panel(PanelId.TARGET_STATUS, TargetStatusView.TITLE, width = 28) { frame ->
            TargetStatusView(frame.targetStatusUnit)
        },
        Panel(PanelId.TARGETS, TargetsView.TITLE, width = 28) { frame ->
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
        Panel(PanelId.DECLARED_TARGETS, DeclaredTargetsView.TITLE, width = 28) { frame ->
            frame.declaredTargets?.let(::DeclaredTargetsView)
        },
        Panel(PanelId.ATTACK_RESULTS, AttackResultsView.TITLE, width = 28) { frame ->
            frame.attackResults?.let(::AttackResultsView)
        },
        Panel(PanelId.UNIT_STATUS, UnitStatusView.TITLE, width = 28) { frame ->
            UnitStatusView(frame.unitStatus, frame.pendingHeat)
        },
        Panel(PanelId.LOG, LogView.TITLE, width = 28, anchorBottom = true) { frame ->
            LogView(entries = frame.logEntries, state = frame.visibleState)
        },
    )
}
