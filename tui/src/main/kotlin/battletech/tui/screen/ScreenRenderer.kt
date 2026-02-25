package battletech.tui.screen

import com.github.ajalt.colormath.model.Ansi256
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.terminal.Terminal

public class ScreenRenderer(private val terminal: Terminal) {

    private var previousBuffer: ScreenBuffer? = null

    public fun render(buffer: ScreenBuffer) {
        val prev = previousBuffer
        if (prev == null) {
            renderFull(buffer)
        } else {
            val changes = prev.diff(buffer)
            for (change in changes) {
                renderCell(change.x, change.y, change.cell)
            }
        }
        System.out.flush()
        previousBuffer = buffer
    }

    public fun clear() {
        terminal.cursor.hide()
        print("\u001b[2J\u001b[H")
        System.out.flush()
        previousBuffer = null
    }

    public fun cleanup() {
        terminal.cursor.show()
        print("\u001b[2J\u001b[H")
        System.out.flush()
    }

    private fun renderFull(buffer: ScreenBuffer) {
        val sb = StringBuilder()
        sb.append("\u001b[H")
        for (y in 0 until buffer.height) {
            for (x in 0 until buffer.width) {
                val cell = buffer.get(x, y)
                sb.append(styledChar(cell))
            }
            if (y < buffer.height - 1) sb.append("\r\n")
        }
        print(sb)
    }

    private fun renderCell(x: Int, y: Int, cell: Cell) {
        print("\u001b[${y + 1};${x + 1}H${styledChar(cell)}")
    }

    private fun styledChar(cell: Cell): String {
        val style = toMordantStyle(cell.fg, cell.bg)
        return if (style != null) {
            style("${cell.char}")
        } else {
            "${cell.char}"
        }
    }

    private fun toMordantStyle(fg: Color, bg: Color): TextStyle? {
        val fgStyle = toFgStyle(fg)
        val bgStyle = toBgStyle(bg)
        if (fgStyle == null && bgStyle == null) return null
        return when {
            fgStyle != null && bgStyle != null -> fgStyle + bgStyle
            fgStyle != null -> fgStyle
            else -> bgStyle!!
        }
    }

    private fun toFgStyle(color: Color): TextStyle? = when (color) {
        Color.DEFAULT -> null
        Color.BLACK -> TextColors.black
        Color.RED -> TextColors.red
        Color.GREEN -> TextColors.green
        Color.BLUE -> TextColors.blue
        Color.YELLOW -> TextColors.yellow
        Color.CYAN -> TextColors.cyan
        Color.WHITE -> TextColors.white
        Color.DARK_GREEN -> TextColors.Companion.color(Ansi256(22))
        Color.BROWN -> TextColors.Companion.color(Ansi256(130))
        Color.BRIGHT_YELLOW -> TextColors.brightYellow
        Color.ORANGE -> TextColors.Companion.color(Ansi256(208))
    }

    private fun toBgStyle(color: Color): TextStyle? = when (color) {
        Color.DEFAULT -> null
        Color.BLACK -> TextColors.black.bg
        Color.RED -> TextColors.red.bg
        Color.GREEN -> TextColors.green.bg
        Color.BLUE -> TextColors.blue.bg
        Color.YELLOW -> TextColors.yellow.bg
        Color.CYAN -> TextColors.cyan.bg
        Color.WHITE -> TextColors.white.bg
        Color.DARK_GREEN -> TextColors.Companion.color(Ansi256(22)).bg
        Color.BROWN -> TextColors.Companion.color(Ansi256(130)).bg
        Color.BRIGHT_YELLOW -> TextColors.brightYellow.bg
        Color.ORANGE -> TextColors.Companion.color(Ansi256(208)).bg
    }
}
