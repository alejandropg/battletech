package io.archinaut.battletech.tui.setup

import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.screen.Padding
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.Borders
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.Row
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.block

/**
 * Composition for a maximized setup panel: [left] at [leftWidth], then — when there is a [detail] —
 * a divider and the detail in the rest of the width. A detail wider than that, such as a map
 * preview, widens the row, so it scrolls horizontally rather than wrapping below the list. The
 * detail panes make no visibility request, so the list's cursor is the one the panel follows.
 */
internal class SplitMaximizedView(
    private val leftWidth: Int,
    private val left: MeasurableWidget,
    private val detail: MeasurableWidget?,
) : MeasurableWidget {

    override fun write(writer: RowWriter) {
        val children = listOfNotNull(
            Constraint.Length(leftWidth) to left,
            detail?.let { Constraint.Fill() to it.block(DIVIDER) },
        )
        writer.draw(Row(children, spacing = SIDE_GUTTER))
    }

    internal companion object {
        const val SIDE_GUTTER: Int = 4

        /** The divider down the detail's left edge, with the same gutter after it as before it. */
        private val DIVIDER = Block(borders = Borders.LEFT, padding = Padding(left = SIDE_GUTTER))
    }
}
