package battletech.tui.view

import battletech.tactical.model.GameState
import battletech.tactical.session.LogEntry
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.TextWrap

public class LogView(
    private val entries: List<LogEntry>,
    private val gameState: GameState,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "LOG", index = 0)

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
        val text = GameLogFormatter.format(entry.event, gameState) ?: return emptyList()
        val prefix = "[%02d] ".format(entry.turn)
        val indent = " ".repeat(prefix.length)
        val firstWidth = (width - prefix.length).coerceAtLeast(1)
        val contWidth = (width - indent.length).coerceAtLeast(1)
        return TextWrap.wrap(text, firstWidth, contWidth)
            .mapIndexed { i, line -> if (i == 0) prefix + line else indent + line }
    }
}
