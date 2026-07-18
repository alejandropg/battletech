package battletech.tui.screen

import com.github.ajalt.colormath.model.Ansi256
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import java.util.EnumMap
import com.github.ajalt.colormath.Color as ColorValue

internal class TextStyleFactory {

    // Per-color fg/bg color caches — null means "unstyled / DEFAULT". Populated eagerly since
    // Color has few entries; buildStyle reads straight from these instead of re-deriving per call.
    private val fgCache: Map<Color, ColorValue> = EnumMap<Color, ColorValue>(Color::class.java).also { map ->
        Color.entries.forEach { map[it] = toFgStyle(it)?.color }
    }

    private val bgCache: Map<Color, ColorValue> = EnumMap<Color, ColorValue>(Color::class.java).also { map ->
        Color.entries.forEach { map[it] = toBgStyle(it)?.color }
    }

    // Combined style cache, keyed by the full Cell.Style. Lazily populated on first use for each distinct Style.
    private val styleCache: HashMap<Cell.Style, TextStyle> = HashMap()

    internal fun styleFor(style: Cell.Style): TextStyle? {
        if (style == Cell.Style.DEFAULT) return null

        val cached = styleCache[style]
        if (cached != null) return cached

        val result = buildStyle(style)
        styleCache[style] = result
        return result
    }

    private fun buildStyle(style: Cell.Style): TextStyle = TextStyle(
        color = fgCache[style.fg],
        bgColor = bgCache[style.bg],
        strikethrough = style.strikethrough,
    )

    private fun toFgStyle(color: Color): TextStyle? = when (color) {
        Color.DEFAULT -> null
        Color.BLACK -> TextColors.black
        Color.RED -> TextColors.red
        Color.GREEN -> TextColors.green
        Color.BLUE -> TextColors.blue
        Color.YELLOW -> TextColors.yellow
        Color.CYAN -> TextColors.cyan
        Color.WHITE -> TextColors.white
        Color.DARK_GREEN -> TextColors.color(Ansi256(22))
        Color.BROWN -> TextColors.color(Ansi256(130))
        Color.BRIGHT_YELLOW -> TextColors.brightYellow
        Color.BRIGHT_GREEN -> TextColors.brightGreen
        Color.ORANGE -> TextColors.color(Ansi256(208))
        Color.MAGENTA -> TextColors.magenta
        Color.LIGHT_BLUE -> TextColors.color(Ansi256(117))
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
        Color.DARK_GREEN -> TextColors.color(Ansi256(22)).bg
        Color.BROWN -> TextColors.color(Ansi256(130)).bg
        Color.BRIGHT_YELLOW -> TextColors.brightYellow.bg
        Color.BRIGHT_GREEN -> TextColors.brightGreen.bg
        Color.ORANGE -> TextColors.color(Ansi256(208)).bg
        Color.MAGENTA -> TextColors.magenta.bg
        Color.LIGHT_BLUE -> TextColors.color(Ansi256(117)).bg
        Color.GRAY -> TextColors.gray.bg
    }
}
