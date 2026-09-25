package io.archinaut.tenter.view

import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.screen.StyledText
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.text.TextTruncation
import io.archinaut.tenter.text.textClusters

public class TextCursor private constructor(
    private val sink: TextSink,
) {
    public constructor(canvas: Canvas) : this(CanvasTextSink(canvas))

    internal constructor(width: Int, sink: TextSink) : this(sink) {
        require(width == sink.width) { "cursor width must match its sink" }
    }

    public val width: Int get() = sink.width

    /** The row the next [writeLine]/[writeRow] lands on. */
    public var row: Int = 0
        private set

    internal var occupiedHeight: Int = 0
        private set

    public fun writeHeader(label: String) {
        writeLine(sectionHeader(label), INFO_STYLE)
    }

    /** Prepares [content] once, places its logical layout at the current row, and advances past it. */
    public fun draw(content: ContentView): Int {
        val layout = content.layout(width)
        sink.place(layout, 0, row)
        repeat(layout.height) { newLine() }
        return layout.height
    }

    private fun sectionHeader(label: String): String {
        val prefix = "── $label "
        val fill = (width - CellWidth.of(prefix)).coerceAtLeast(0)
        return prefix + "─".repeat(fill)
    }

    /** Writes [text] on the current row and advances. Returns the row it was written to. */
    public fun writeLine(text: String, style: Cell.Style = Cell.Style.DEFAULT): Int {
        val written = row
        sink.write(0, written, TextTruncation.ellipsize(text, width), style)
        row += 1
        occupy(row)
        return written
    }

    /**
     * Styled counterpart of [writeLine] — one row, ellipsized to the canvas width exactly as
     * the flat overload is, each span painted in its own style. This is the reason [StyledText]
     * exists: the caller never computes a column.
     */
    public fun writeLine(text: StyledText): Int {
        val written = row
        var column = 0
        for (span in text.ellipsize(width).spans) {
            sink.write(column, written, span.text, span.style)
            column += CellWidth.of(span.text)
        }
        row += 1
        occupy(row)
        return written
    }

    /** Writes [text] at [column] on the current row, without advancing. */
    public fun write(column: Int, text: String, style: Cell.Style = Cell.Style.DEFAULT) {
        sink.write(column, row, text, style)
        occupy(row + 1)
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
        if (width <= 0) return
        val fitted = TextTruncation.ellipsize(text, width)
        val fittedWidth = CellWidth.of(fitted)
        val startColumn = if (align == Align.LEFT) column else column + width - fittedWidth
        sink.write(startColumn, row, fitted, style)
        occupy(row + 1)
    }

    /**
     * Writes [left] flush to the panel's left edge (truncated with an ellipsis if it would
     * collide with [right]) and [right] flush to the right edge, then advances. [rightStyle]
     * defaults to [leftStyle] for a single-color row. Returns the row written.
     */
    public fun writeRow(left: String, right: String, leftStyle: Cell.Style = Cell.Style.DEFAULT, rightStyle: Cell.Style = leftStyle): Int {
        val visibleRight = TextTruncation.ellipsize(right, width)
        val rightWidth = CellWidth.of(visibleRight)
        val maxLeft = (width - rightWidth - 1).coerceAtLeast(0)
        val written = row
        sink.write(0, written, TextTruncation.ellipsize(left, maxLeft), leftStyle)
        sink.write(width - rightWidth, written, visibleRight, rightStyle)
        row += 1
        occupy(row)
        return written
    }

    public fun newLine() {
        row += 1
        occupy(row)
    }

    /** Marks the current row (full width) as the content the enclosing scrollable view should keep visible. */
    public fun markReveal(height: Int = 1) {
        sink.reveal(0, row, width, height)
    }

    /** Marks [atRow] (full width), rather than the current row, as the content to keep visible. */
    public fun markRevealAt(atRow: Int, height: Int = 1) {
        sink.reveal(0, atRow, width, height)
    }

    /** Overlays [text] onto an already-written [atRow] at [column] — for repainting part of a finished row. */
    public fun writeAt(column: Int, atRow: Int, text: String, style: Cell.Style = Cell.Style.DEFAULT) {
        sink.write(column, atRow, text, style)
        occupy(atRow + 1)
    }

    private fun occupy(height: Int) {
        if (height > occupiedHeight) occupiedHeight = height
    }

    private companion object {
        private val INFO_STYLE = Cell.Style(fg = ChromeRole.INFO)
    }
}

internal fun drawVerticalText(canvas: Canvas, text: String, style: Cell.Style) {
    val centerX = canvas.width / 2
    var row = 0
    for (cluster in textClusters(text)) {
        if (row >= canvas.height) break
        if (cluster.width == 0) continue
        canvas.set(centerX, row, Cell(cluster.drawableText, style))
        row++
    }
}
