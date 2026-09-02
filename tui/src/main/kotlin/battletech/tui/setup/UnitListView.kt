package battletech.tui.setup

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.CheckState
import tenter.widget.SelectableRow

/**
 * Panels 3/4: every registered mech, multi-select with a per-model count (D7). Rows show the
 * variant only plus a right-aligned count, catalog order, blank when the count is 0.
 */
internal class UnitListView(
    private val variants: List<String>,
    private val counts: (String) -> Int,
    private val cursorIndex: Int,
) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        if (variants.isEmpty()) {
            content.writeLine("No mechs registered", TEXT_PRIMARY_STYLE)
            return
        }

        for ((index, variant) in variants.withIndex()) {
            val isCursorHere = index == cursorIndex
            val count = counts(variant)
            val countLabel = if (count == 0) "" else count.toString()
            // CHECKED at any positive count, never INDETERMINATE: in this codebase's checkbox
            // vocabulary that third state means "assigned elsewhere" (see TargetsView), which
            // would read as something quite different from "more than one of this model".
            val state = if (count == 0) CheckState.UNCHECKED else CheckState.CHECKED
            SelectableRow.draw(
                content = content,
                label = variant,
                checkState = state,
                cursor = isCursorHere,
                right = countLabel,
            )
        }
    }

    private companion object {
        val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
