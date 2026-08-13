package tenter.view

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.text.CellWidth

public class TextCursor(private val canvas: Canvas) {
    public val width: Int get() = canvas.width

    /** The row the next [writeLine]/[writeRow] lands on. */
    public var row: Int = 0
        private set

    public fun writeHeader(label: String) {
        writeLine(sectionHeader(label), INFO_STYLE)
    }

    private fun sectionHeader(label: String): String {
        val prefix = "── $label "
        val fill = (width - prefix.length).coerceAtLeast(0)
        return prefix + "─".repeat(fill)
    }

    /** Writes [text] on the current row and advances. Returns the row it was written to. */
    public fun writeLine(text: String, style: Cell.Style = Cell.Style.DEFAULT): Int {
        val truncated = if (CellWidth.of(text) > width) truncateToWidth(text, width - 1) + "…" else text
        val written = row
        canvas.writeString(0, written, truncated, style)
        row += 1
        return written
    }

    private fun truncateToWidth(text: String, maxWidth: Int): String {
        var displayWidth = 0
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val codePointWidth = CellWidth.of(codePoint)
            if (displayWidth + codePointWidth > maxWidth) break
            displayWidth += codePointWidth
            i += Character.charCount(codePoint)
        }
        return text.substring(0, i)
    }

    /** Writes [text] at [column] on the current row, without advancing. */
    public fun write(column: Int, text: String, style: Cell.Style = Cell.Style.DEFAULT) {
        canvas.writeString(column, row, text, style)
    }

    /**
     * Writes [left] flush to the panel's left edge (truncated with an ellipsis if it would
     * collide with [right]) and [right] flush to the right edge, then advances. [rightStyle]
     * defaults to [leftStyle] for a single-color row. Returns the row written.
     */
    public fun writeRow(left: String, right: String, leftStyle: Cell.Style = Cell.Style.DEFAULT, rightStyle: Cell.Style = leftStyle): Int {
        val rightWidth = CellWidth.of(right)
        val maxLeft = (width - rightWidth - 1).coerceAtLeast(0)
        val leftText = if (CellWidth.of(left) > maxLeft) truncateToWidth(left, maxLeft - 1) + "…" else left
        val written = row
        canvas.writeString(0, written, leftText, leftStyle)
        canvas.writeString(width - rightWidth, written, right, rightStyle)
        row += 1
        return written
    }

    public fun newLine() {
        row += 1
    }

    /** Marks the current row (full width) as the content the enclosing scrollable view should keep visible. */
    public fun markReveal(height: Int = 1) {
        canvas.markReveal(0, row, width, height)
    }

    /** Marks [atRow] (full width), rather than the current row, as the content to keep visible. */
    public fun markRevealAt(atRow: Int, height: Int = 1) {
        canvas.markReveal(0, atRow, width, height)
    }

    /** Overlays [text] onto an already-written [atRow] at [column] — for repainting part of a finished row. */
    public fun writeAt(column: Int, atRow: Int, text: String, style: Cell.Style = Cell.Style.DEFAULT) {
        canvas.writeString(column, atRow, text, style)
    }

    private companion object {
        private val INFO_STYLE = Cell.Style(fg = ChromeRole.INFO)
    }
}
