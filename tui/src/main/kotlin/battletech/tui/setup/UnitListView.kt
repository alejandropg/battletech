package battletech.tui.setup

import battletech.tactical.unit.MechModel
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.CheckState
import tenter.widget.SelectableRow
import tenter.widget.ValueRow

/**
 * Panels 3/4's roster list: every registered mech, multi-select with a per-model count (D7),
 * catalog order, blank count when 0. Also used unchanged as the MAXIMIZED split view's left pane
 * ([MechSelectionMaximizedView]), which shows full stats via its own record-sheet detail pane.
 *
 * [mechFor] is `null` for both of those (the minimized column and the maximized left pane): the
 * count is right-aligned via [SelectableRow.draw]'s own `right` column. Supplying [mechFor] (used
 * by the NORMAL, unmaximized PLAYER panel) draws a `TON WLK RUN JMP` header and adds four
 * right-aligned stat columns instead — the count moves next to the variant id, in the row's label,
 * so it sits in the same place relative to the variant either way rather than getting pushed past
 * the new stat columns.
 *
 * A stats-mode row's `right` string, and the header's own `right` string, are both always exactly
 * [FIELD_WIDTH] `* 4 + 3` characters (four fixed-width fields, three single-space separators) —
 * that fixed length is what keeps them aligned. [ValueRow.draw] (which both go through) flushes
 * the *entire* `right` string so its last character lands on the panel's last column; it has no
 * notion of "columns", just one right-anchored string per line. A `right` even one character
 * shorter or longer than its neighbors' would silently shift that row's stat fields out of line
 * with the header and every other row.
 */
internal class UnitListView(
    private val variants: List<String>,
    private val counts: (String) -> Int,
    private val cursorIndex: Int,
    private val mechFor: ((String) -> MechModel?)? = null,
) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        if (variants.isEmpty()) {
            content.writeLine("No mechs registered", TEXT_PRIMARY_STYLE)
            return
        }

        if (mechFor != null) drawStatsHeader(content)

        for ((index, variant) in variants.withIndex()) {
            val isCursorHere = index == cursorIndex
            val count = counts(variant)
            val countLabel = if (count == 0) "" else count.toString()
            // CHECKED at any positive count, never INDETERMINATE: in this codebase's checkbox
            // vocabulary that third state means "assigned elsewhere" (see TargetsView), which
            // would read as something quite different from "more than one of this model".
            val state = if (count == 0) CheckState.UNCHECKED else CheckState.CHECKED
            val label = if (mechFor == null || count == 0) variant else "$variant$LABEL_COUNT_SEPARATOR$countLabel"
            val right = if (mechFor == null) countLabel else statsBlock(mechFor(variant))
            SelectableRow.draw(
                content = content,
                label = label,
                checkState = state,
                cursor = isCursorHere,
                right = right,
            )
        }
    }

    /**
     * Blank ([FIELD_WIDTH] spaces) per field when [model] is null — an unregistered/test-fixture
     * variant, not an error. A zero [MechModel.jumpMP] is blank too: most 'mechs can't jump at
     * all, and a column of meaningless zeros is noise next to the ones that can.
     */
    private fun statsBlock(model: MechModel?): String {
        val values = if (model == null) List(4) { "" } else listOf(
            model.tonnage.toString(),
            model.walkingMP.toString(),
            model.runningMP.toString(),
            if (model.jumpMP == 0) "" else model.jumpMP.toString(),
        )
        return values.joinToString(" ") { it.padStart(FIELD_WIDTH) }
    }

    private fun drawStatsHeader(content: TextCursor) {
        ValueRow.draw(content = content, left = "", right = STATS_HEADER, subLines = emptyList(), color = ChromeRole.TEXT_MUTED)
    }

    private companion object {
        const val FIELD_WIDTH = 3
        const val STATS_HEADER = "TON WLK RUN JMP" // 4 * FIELD_WIDTH + 3 separators = 15 chars, must match statsBlock()'s length
        const val LABEL_COUNT_SEPARATOR = "  "
        val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
