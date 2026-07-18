package battletech.tui.screen

import com.github.ajalt.mordant.terminal.Terminal

public class ScreenRenderer(private val terminal: Terminal) {

    private val styleFactory = TextStyleFactory()

    public fun render(buffer: ScreenBuffer) {
        renderFull(buffer)
        System.out.flush()
    }

    public fun clear() {
        terminal.cursor.hide()
        terminal.cursor.move {
            clearScreen()
            setPosition(0, 0)
        }
        System.out.flush()
    }

    public fun cleanup() {
        terminal.cursor.show()
        terminal.cursor.move {
            clearScreen()
            setPosition(0, 0)
        }
        System.out.flush()
    }

    private fun renderFull(buffer: ScreenBuffer) {
        val sb = StringBuilder()
        sb.append(terminal.cursor.getMoves { setPosition(0, 0) })
        for (y in 0 until buffer.height) {
            var x = 0
            while (x < buffer.width) {
                val firstCell = buffer.get(x, y)
                val runStyle = firstCell.style
                // collect all consecutive cells with the same style
                val runChars = StringBuilder()
                while (x < buffer.width) {
                    val cell = buffer.get(x, y)
                    if (cell.style != runStyle) break
                    runChars.append(cell.char)
                    x++
                }
                val runText = runChars.toString()
                val style = styleFactory.styleFor(runStyle)
                if (style != null) {
                    sb.append(style(runText))
                } else {
                    sb.append(runText)
                }
            }
            if (y < buffer.height - 1) sb.append("\r\n")
        }
        terminal.rawPrint(sb)
        System.out.flush()
    }
}
