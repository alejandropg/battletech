package battletech.tui.setup

import tenter.panel.PanelId

internal enum class SetupPanelId : PanelId { MODE, MAP, PLAYER_1, PLAYER_2, HELP }

/** The panel `Enter`/`Tab` moves to from [focused], walking [visible] in declaration order and wrapping. */
internal fun nextPanel(focused: SetupPanelId, visible: List<SetupPanelId>): SetupPanelId {
    if (visible.isEmpty()) return focused
    val index = visible.indexOf(focused)
    if (index == -1) return visible.first()
    return visible[(index + 1) % visible.size]
}
