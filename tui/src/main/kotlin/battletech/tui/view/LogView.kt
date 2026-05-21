package battletech.tui.view

import battletech.tactical.session.LogEntry
import battletech.tui.screen.ScreenBuffer

public class LogView(
    private val entries: List<LogEntry>,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "LOG")

        val innerX = x + 2
        val innerY = y + 1
        val innerWidth = (width - 4).coerceAtLeast(0)
        val innerHeight = (height - 2).coerceAtLeast(0)
        if (innerWidth <= 0 || innerHeight <= 0) return

        val visualLines = entries.flatMap { wrapEntry(it, innerWidth) }
        val visible = if (visualLines.size <= innerHeight) visualLines else visualLines.takeLast(innerHeight)

        for ((i, line) in visible.withIndex()) {
            buffer.writeString(innerX, innerY + i, line)
        }
    }

    private fun wrapEntry(entry: LogEntry, width: Int): List<String> {
        val prefix = "[T${entry.turn}] "
        val indent = " ".repeat(prefix.length)
        val firstLineCapacity = (width - prefix.length).coerceAtLeast(1)
        val continuationCapacity = (width - indent.length).coerceAtLeast(1)

        val words = entry.text.split(' ').filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        var capacity = firstLineCapacity

        for (word in words) {
            val sep = if (current.isEmpty()) "" else " "
            val needed = sep.length + word.length
            if (current.length + needed <= capacity) {
                current.append(sep).append(word)
            } else {
                lines += current.toString()
                current = StringBuilder()
                capacity = continuationCapacity
                if (word.length <= capacity) {
                    current.append(word)
                } else {
                    // Word longer than capacity — hard-split.
                    var remaining = word
                    while (remaining.length > capacity) {
                        lines += remaining.take(capacity)
                        remaining = remaining.drop(capacity)
                    }
                    current.append(remaining)
                }
            }
        }
        if (current.isNotEmpty()) lines += current.toString()

        return lines.mapIndexed { index, text ->
            if (index == 0) prefix + text else indent + text
        }
    }
}
