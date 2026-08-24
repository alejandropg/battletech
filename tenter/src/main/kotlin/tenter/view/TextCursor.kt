package tenter.view

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.text.CellWidth
import tenter.text.TextTruncation

public class TextCursor(private val canvas: Canvas) {
    public val width: Int get() = canvas.width

    /** The row the next [writeLine]/[writeRow] lands on. */
    public var row: Int = 0
        private set

    public fun writeHeader(label: String) {
        writeLine(sectionHeader(label), INFO_STYLE)
    }

    /**
     * Draws [view] as a self-contained band at the cursor's current row, using all of the
     * canvas's width — measuring the view's own height into an offscreen stream, then blitting
     * it in and advancing past it. This is the composition primitive a multi-card layout needs
     * so cards can be stacked by height without knowing each other's row count up front;
     * [Columns] uses the same measure-then-place trick one level down, for the cards *within* a
     * band. Returns the number of rows [view] used.
     */
    public fun draw(view: View): Int {
        val remaining = canvas.region(0, row, canvas.width, canvas.height - row)
        if (remaining.width <= 0 || remaining.height <= 0) return 0
        val stream = Canvas.offscreen(remaining.width, remaining.height)
        view.draw(stream)
        val used = stream.contentHeight()
        canvas.blit(stream, 0, 0, 0, row, remaining.width, used)
        repeat(used) { newLine() }
        return used
    }

    private fun sectionHeader(label: String): String {
        val prefix = "── $label "
        val fill = (width - prefix.length).coerceAtLeast(0)
        return prefix + "─".repeat(fill)
    }

    /** Writes [text] on the current row and advances. Returns the row it was written to. */
    public fun writeLine(text: String, style: Cell.Style = Cell.Style.DEFAULT): Int {
        val written = row
        canvas.writeString(0, written, TextTruncation.ellipsize(text, width), style)
        row += 1
        return written
    }

    /** Writes [text] at [column] on the current row, without advancing. */
    public fun write(column: Int, text: String, style: Cell.Style = Cell.Style.DEFAULT) {
        canvas.writeString(column, row, text, style)
    }

    /** Which edge of a [width]-wide field [write] anchors [text] against. */
    public enum class Align {
        LEFT,
        RIGHT,
    }

    /**
     * Writes [text] on the current row within a [width]-wide field starting at [column], without
     * advancing — [Align.LEFT] flush to [column], [Align.RIGHT] flush to `column + width`. The
     * shared primitive behind any fixed-width table column so each caller states "this field is
     * N wide, right-aligned" once instead of hand-computing the right-aligned starting column
     * itself.
     */
    public fun write(column: Int, width: Int, text: String, style: Cell.Style = Cell.Style.DEFAULT, align: Align = Align.LEFT) {
        val startColumn = if (align == Align.LEFT) column else column + width - CellWidth.of(text)
        canvas.writeString(startColumn, row, text, style)
    }

    /**
     * Writes [left] flush to the panel's left edge (truncated with an ellipsis if it would
     * collide with [right]) and [right] flush to the right edge, then advances. [rightStyle]
     * defaults to [leftStyle] for a single-color row. Returns the row written.
     */
    public fun writeRow(left: String, right: String, leftStyle: Cell.Style = Cell.Style.DEFAULT, rightStyle: Cell.Style = leftStyle): Int {
        val rightWidth = CellWidth.of(right)
        val maxLeft = (width - rightWidth - 1).coerceAtLeast(0)
        val written = row
        canvas.writeString(0, written, TextTruncation.ellipsize(left, maxLeft), leftStyle)
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
