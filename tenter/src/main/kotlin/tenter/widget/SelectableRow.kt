package tenter.widget

import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.ColorRole
import tenter.view.TextCursor

/** Renders one cursor-highlightable row with an optional checkbox and right-aligned value. */
public object SelectableRow {

    /**
     * Draws [label] with the standard cursor marker, highlight color, checkbox, and reveal
     * behavior. When [right] is non-null, the row uses [ValueRow] and renders [subLines] below it.
     * [checkboxColor] overrides the intrinsic checkbox color when the row is not highlighted.
     */
    public fun draw(
        content: TextCursor,
        label: String,
        checkState: CheckState,
        cursor: Boolean,
        right: String? = null,
        subLines: List<String> = emptyList(),
        textColor: ColorRole = ChromeRole.TEXT_PRIMARY,
        checkboxColor: ColorRole? = null,
    ) {
        val row = content.row
        if (cursor) content.markReveal()

        val color = if (cursor) ChromeRole.ACCENT else textColor
        val cursorGlyph = if (cursor) "▶" else " "
        val left = "$cursorGlyph   $label"
        if (right == null) {
            content.writeLine(left, Cell.Style(color))
        } else {
            ValueRow.draw(content, left, right, subLines, color)
        }

        val resolvedCheckboxColor = when {
            cursor -> ChromeRole.ACCENT
            checkboxColor != null -> checkboxColor
            else -> Checkbox.intrinsicColor(checkState)
        }
        Checkbox.draw(content, CHECKBOX_COLUMN, row, checkState, resolvedCheckboxColor)
    }

    private const val CHECKBOX_COLUMN = 2
}
