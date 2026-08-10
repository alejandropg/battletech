package battletech.tui.screen

internal class ContentWriter(val canvas: Canvas) {
    val width: Int get() = canvas.width

    var cy = 0

    fun writeHeader(label: String) {
        writeln(sectionHeader(label), CYAN_STYLE)
    }

    private fun sectionHeader(label: String): String {
        val prefix = "── $label "
        val fill = (width - prefix.length).coerceAtLeast(0)
        return prefix + "─".repeat(fill)
    }

    fun writeln(text: String, style: Cell.Style = Cell.Style.DEFAULT) {
        val truncated = if (CellWidth.of(text) > width) truncateToWidth(text, width - 1) + "…" else text
        canvas.writeString(0, cy, truncated, style)
        cy += 1
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

    fun writeStr(column: Int = 0, text: String, style: Cell.Style = Cell.Style.DEFAULT) {
        canvas.writeString(column, cy, text, style)
    }

    /** Writes [left] flush to the panel's left edge and [right] flush to its right edge. */
    fun writeRow(left: String, right: String, style: Cell.Style = Cell.Style.DEFAULT) {
        val rightWidth = CellWidth.of(right)
        val maxLeft = (width - rightWidth - 1).coerceAtLeast(0)
        val leftText = if (CellWidth.of(left) > maxLeft) truncateToWidth(left, maxLeft - 1) + "…" else left
        canvas.writeString(0, cy, leftText, style)
        canvas.writeString(width - rightWidth, cy, right, style)
        cy += 1
    }

    fun newLine() {
        cy += 1
    }

    /** Marks the current row (full width) as the content the enclosing scrollable view should keep visible. */
    fun markFocus(height: Int = 1) {
        canvas.markFocus(0, cy, width, height)
    }

    private companion object {
        private val CYAN_STYLE = Cell.Style(fg = Color.CYAN)
    }
}
