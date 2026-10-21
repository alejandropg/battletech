package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tui.screen.UiRole
import io.archinaut.battletech.tui.screen.BoardRole
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.palette.ChromeRole

/**
 * Shared cell styles for the maximized UNIT STATUS record sheet — one palette so every card
 * (mech data, warrior data, armor/structure diagrams, heat ladder, crit table, weapons) agrees
 * on what "damaged", "intact", and "destroyed" look like, rather than each card picking its own.
 */
internal object SheetStyles {
    val TEXT_PRIMARY: Style = Style(ChromeRole.TEXT_PRIMARY)
    val TEXT_MUTED: Style = Style(ChromeRole.TEXT_MUTED)
    val ACCENT: Style = Style(ChromeRole.ACCENT)
    val DANGER: Style = Style(ChromeRole.DANGER)
    val DRAFT: Style = Style(UiRole.DRAFT)
    val DESTROYED: Style = Style(BoardRole.DESTROYED, strikethrough = true)
}
