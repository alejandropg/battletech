package battletech.tui.screen

import com.github.ajalt.colormath.model.Ansi256
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.terminal.Terminal
import java.util.EnumMap

public class ScreenRenderer(private val terminal: Terminal) {

    // Per-color fg/bg style caches — null means "unstyled / DEFAULT"
    private val fgCache: EnumMap<Color, TextStyle?> = EnumMap<Color, TextStyle?>(Color::class.java).also { map ->
        Color.entries.forEach { map[it] = toFgStyle(it) }
    }

    private val bgCache: EnumMap<Color, TextStyle?> = EnumMap<Color, TextStyle?>(Color::class.java).also { map ->
        Color.entries.forEach { map[it] = toBgStyle(it) }
    }

    // 14×14 combined style cache, indexed by Color.ordinal.  Null means both DEFAULT (no markup).
    // Lazily populated on first use for each (fg, bg) pair.
    private val colorCount = Color.entries.size
    private val composedCache: Array<TextStyle?> = arrayOfNulls(colorCount * colorCount)

    private fun composedStyle(fg: Color, bg: Color): TextStyle? {
        val idx = fg.ordinal * colorCount + bg.ordinal
        val cached = composedCache[idx]
        if (cached != null) return cached

        val fgStyle = fgCache[fg]
        val bgStyle = bgCache[bg]
        val result: TextStyle? = when {
            fgStyle != null && bgStyle != null -> fgStyle + bgStyle
            fgStyle != null -> fgStyle
            bgStyle != null -> bgStyle
            else -> null
        }
        // A null result (DEFAULT×DEFAULT) is indistinguishable from "not computed",
        // so it is recomputed each time — trivially cheap.
        if (result != null) composedCache[idx] = result
        return result
    }

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
                val runFg = firstCell.fg
                val runBg = firstCell.bg
                // collect all consecutive cells with the same (fg, bg)
                val runChars = StringBuilder()
                while (x < buffer.width) {
                    val cell = buffer.get(x, y)
                    if (cell.fg != runFg || cell.bg != runBg) break
                    runChars.append(cell.char)
                    x++
                }
                val runText = runChars.toString()
                val style = composedStyle(runFg, runBg)
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
        Color.BRIGHT_GREEN -> TextColors.brightGreen
        Color.ORANGE -> TextColors.Companion.color(Ansi256(208))
        Color.MAGENTA -> TextColors.magenta
        Color.GRAY -> TextColors.gray
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
        Color.BRIGHT_GREEN -> TextColors.brightGreen.bg
        Color.ORANGE -> TextColors.Companion.color(Ansi256(208)).bg
        Color.MAGENTA -> TextColors.magenta.bg
        Color.GRAY -> TextColors.gray.bg
    }
}
