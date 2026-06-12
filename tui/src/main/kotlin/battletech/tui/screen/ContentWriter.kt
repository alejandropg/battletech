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
        val dashes = (width - label.length - 1).coerceAtLeast(0)
        return "$label ${"─".repeat(dashes)}"
    }

    fun writeln(text: String, fg: Color = Color.DEFAULT, bg: Color = Color.DEFAULT) {
        val truncated = if (text.length > width) text.substring(0, width - 1) + "…" else text
        buffer.writeString(x, cy, truncated, fg, bg)
        cy += 1
    }

    fun writeStr(padding: Int = 0, text: String, fg: Color = Color.DEFAULT, bg: Color = Color.DEFAULT) {
        val cx = x + padding
        buffer.writeString(cx, cy, text, fg, bg)
    }

    fun newLine() {
        cy += 1
    }

}
