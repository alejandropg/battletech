package battletech.tui.view.record

import battletech.tui.screen.BoardRole
import tenter.screen.Cell
import tenter.screen.ChromeRole

/**
 * Shared cell styles for the maximized UNIT STATUS record sheet — one palette so every card
 * (mech data, warrior data, armor/structure diagrams, heat ladder, crit table, weapons) agrees
 * on what "damaged", "intact", and "destroyed" look like, rather than each card picking its own.
 */
internal object SheetStyles {
    val TEXT_PRIMARY: Cell.Style = Cell.Style(ChromeRole.TEXT_PRIMARY)
    val TEXT_MUTED: Cell.Style = Cell.Style(ChromeRole.TEXT_MUTED)
    val ACCENT: Cell.Style = Cell.Style(ChromeRole.ACCENT)
    val INFO: Cell.Style = Cell.Style(ChromeRole.INFO)
    val SUCCESS: Cell.Style = Cell.Style(ChromeRole.SUCCESS)
    val DANGER: Cell.Style = Cell.Style(ChromeRole.DANGER)
    val DRAFT: Cell.Style = Cell.Style(ChromeRole.DRAFT)
    val DESTROYED: Cell.Style = Cell.Style(BoardRole.DESTROYED, strikethrough = true)
}
