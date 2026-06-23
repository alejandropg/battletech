package battletech.tui.screen

internal class ContentWriter(
    val buffer: ScreenBuffer,
    val x: Int,
    val y: Int,
    val width: Int
) {
    var cy = y

    fun writeHeader(label: String) {
        writeln(sectionHeader(label), Color.CYAN)
    }

    private fun sectionHeader(label: String): String {
        val prefix = "── $label "
        val fill = (width - prefix.length).coerceAtLeast(0)
        return prefix + "─".repeat(fill)
    }

    fun writeln(text: String, fg: Color = Color.DEFAULT, bg: Color = Color.DEFAULT) {
        val truncated = if (CellWidth.of(text) > width) truncateToWidth(text, width - 1) + "…" else text
        buffer.writeString(x, cy, truncated, fg, bg)
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

    fun writeStr(padding: Int = 0, text: String, fg: Color = Color.DEFAULT, bg: Color = Color.DEFAULT) {
        val cx = x + padding
        buffer.writeString(cx, cy, text, fg, bg)
    }

    /** Writes [left] flush to the panel's left edge and [right] flush to its right edge. */
    fun writeRow(left: String, right: String, fg: Color = Color.DEFAULT) {
        val rightWidth = CellWidth.of(right)
        val maxLeft = (width - rightWidth - 1).coerceAtLeast(0)
        val leftText = if (CellWidth.of(left) > maxLeft) truncateToWidth(left, maxLeft - 1) + "…" else left
        buffer.writeString(x, cy, leftText, fg)
        buffer.writeString(x + width - rightWidth, cy, right, fg)
        cy += 1
    }

    fun newLine() {
        cy += 1
    }

}
