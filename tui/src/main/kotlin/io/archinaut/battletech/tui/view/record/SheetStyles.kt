package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.screen.AppRole
import io.archinaut.battletech.tui.screen.BoardRole
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.color.StandardRole

/**
 * Shared cell styles for the maximized UNIT STATUS record sheet — one palette so every card
 * (mech data, warrior data, armor/structure diagrams, heat ladder, crit table, weapons) agrees
 * on what "damaged", "intact", and "destroyed" look like, rather than each card picking its own.
 */
internal object SheetStyles {
    val TEXT_PRIMARY: Style = Style(StandardRole.TEXT_PRIMARY)
    val TEXT_MUTED: Style = Style(StandardRole.TEXT_MUTED)
    val ACCENT: Style = Style(StandardRole.ACCENT)
    val DANGER: Style = Style(StandardRole.DANGER)
    val DRAFT: Style = Style(AppRole.DRAFT)
    val DESTROYED: Style = Style(BoardRole.DESTROYED, strikethrough = true)
}
