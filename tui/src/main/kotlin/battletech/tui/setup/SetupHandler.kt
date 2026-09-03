package battletech.tui.setup

import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.unit.AutoDeploy
import tenter.view.FlashMessage

/** Shown when a read-only mirror presses an editing key — one message for `space`/`a`/`d`/`c`. */
internal const val READ_ONLY_REFUSAL: String = "the host is choosing — this view is read-only"

internal data class SetupTransition(
    val state: SetupState,
    val flash: FlashMessage? = null,
    val committed: MatchPlan? = null,
)

/**
 * Pure. Returns null when [action] means nothing for [focused] right now, or a refusal transition
 * carrying a [READ_ONLY_REFUSAL] flash — state untouched — when a read-only mirror tries to edit.
 */
internal fun handleSetup(action: SetupAction, focused: SetupPanelId, state: SetupState): SetupTransition? {
    // The mirror browses but never edits: MoveCursor falls through (it drives the maximized
    // panels' detail pane, so the joiner can read every map and record sheet), and everything
    // else is refused out loud rather than silently. Written as a negative guard so a future
    // SetupAction variant is refused by default rather than silently punching a hole here.
    if (state.readOnly && focused != SetupPanelId.HELP && action !is SetupAction.MoveCursor) {
        return SetupTransition(state, flash = FlashMessage(READ_ONLY_REFUSAL))
    }

    return when (focused) {
        SetupPanelId.MODE -> handleModePanel(action, state)
        SetupPanelId.MAP -> handleMapPanel(action, state)
        SetupPanelId.PLAYER_1 -> handlePlayerPanel(action, state, PlayerId.PLAYER_1)
        SetupPanelId.PLAYER_2 -> handlePlayerPanel(action, state, PlayerId.PLAYER_2)
        SetupPanelId.HELP -> null
    }
}

private fun handleModePanel(action: SetupAction, state: SetupState): SetupTransition? {
    // Locked, so the mode itself is settled — but this panel stays focusable and in the Enter
    // cycle (D5), and `c` is the commit key on every panel (D8), so it must still commit (or
    // flash why it can't) rather than silently doing nothing here.
    if (state.modeLocked) return if (action == SetupAction.Commit) commitOrFlash(state) else null
    return when (action) {
        is SetupAction.MoveCursor -> moveModeCursor(state, action.delta)
        SetupAction.Toggle -> SetupTransition(state.copy(mode = SetupMode.entries[modeCursorOf(state)]))
        // HOST: endpoint stays null here — beginHosting() is an effect the loop performs and
        // folds back into state, not something this pure handler can do.
        SetupAction.Commit -> SetupTransition(state.copy(modeLocked = true))
        else -> null
    }
}

private fun moveModeCursor(state: SetupState, delta: Int): SetupTransition {
    val cursor = (modeCursorOf(state) + delta).coerceIn(0, SetupMode.entries.lastIndex)
    return SetupTransition(state.copy(cursors = state.cursors + (SetupPanelId.MODE to cursor)))
}

private fun handleMapPanel(action: SetupAction, state: SetupState): SetupTransition? {
    val maps = state.catalog.maps
    return when (action) {
        is SetupAction.MoveCursor ->
            if (maps.isEmpty()) null else moveCursor(state, SetupPanelId.MAP, action.delta, maps.lastIndex)
        SetupAction.Toggle -> {
            if (maps.isEmpty()) return null
            val selected = maps[cursorOf(state, SetupPanelId.MAP).coerceIn(0, maps.lastIndex)]
            val newMapName = if (state.plan.mapName == selected) null else selected
            SetupTransition(state.copy(plan = state.plan.withMap(newMapName)))
        }
        is SetupAction.Adjust -> null
        SetupAction.Commit -> commitOrFlash(state)
        SetupAction.NextPanel -> null
    }
}

private fun handlePlayerPanel(action: SetupAction, state: SetupState, player: PlayerId): SetupTransition? {
    val panel = playerPanel(player)
    val mechs = state.catalog.mechs
    return when (action) {
        is SetupAction.MoveCursor ->
            if (mechs.isEmpty()) null else moveCursor(state, panel, action.delta, mechs.lastIndex)
        SetupAction.Toggle -> {
            if (mechs.isEmpty()) return null
            val variant = mechs[cursorOf(state, panel).coerceIn(0, mechs.lastIndex)]
            val newCount = if (state.plan.count(player, variant) > 0) 0 else 1
            SetupTransition(state.copy(plan = state.plan.withCount(player, variant, newCount)))
        }
        is SetupAction.Adjust -> {
            if (mechs.isEmpty()) return null
            val variant = mechs[cursorOf(state, panel).coerceIn(0, mechs.lastIndex)]
            if (action.delta > 0) increment(state, player, variant) else decrement(state, player, variant)
        }
        SetupAction.Commit -> commitOrFlash(state)
        SetupAction.NextPanel -> null
    }
}

private fun increment(state: SetupState, player: PlayerId, variant: String): SetupTransition {
    // The map is not selected yet: allow the increment freely — commitBlocker() catches an
    // unselected map later, at commit time.
    val capacity = state.plan.mapName?.let(state.registry::map)?.let { AutoDeploy.capacity(it, player) }
    if (capacity != null && state.plan.totalUnits(player) >= capacity) {
        return SetupTransition(state, flash = FlashMessage("no room on the map for more units"))
    }
    val current = state.plan.count(player, variant)
    return SetupTransition(state.copy(plan = state.plan.withCount(player, variant, current + 1)))
}

private fun decrement(state: SetupState, player: PlayerId, variant: String): SetupTransition {
    val current = state.plan.count(player, variant)
    return SetupTransition(state.copy(plan = state.plan.withCount(player, variant, (current - 1).coerceAtLeast(0))))
}

private fun moveCursor(state: SetupState, panel: SetupPanelId, delta: Int, lastIndex: Int): SetupTransition {
    val cursor = (cursorOf(state, panel) + delta).coerceIn(0, lastIndex)
    return SetupTransition(state.copy(cursors = state.cursors + (panel to cursor)))
}

private fun cursorOf(state: SetupState, panel: SetupPanelId): Int = state.cursors[panel] ?: 0

private fun modeCursorOf(state: SetupState): Int =
    (state.cursors[SetupPanelId.MODE] ?: state.mode.ordinal).coerceIn(0, SetupMode.entries.lastIndex)

private fun playerPanel(player: PlayerId): SetupPanelId = when (player) {
    PlayerId.PLAYER_1 -> SetupPanelId.PLAYER_1
    PlayerId.PLAYER_2 -> SetupPanelId.PLAYER_2
}

private fun commitOrFlash(state: SetupState): SetupTransition {
    val blocker = state.commitBlocker()
    return if (blocker != null) SetupTransition(state, flash = FlashMessage(blocker)) else SetupTransition(state, committed = state.plan)
}
