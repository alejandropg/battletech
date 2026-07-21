package battletech.tui.screen

import com.github.ajalt.colormath.model.Ansi256
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import java.util.EnumMap
import com.github.ajalt.colormath.Color as ColorValue

internal class TextStyleFactory {

    // Per-color fg/bg color caches — null means "unstyled / DEFAULT". Populated eagerly since
    // Color has few entries; buildStyle reads straight from these instead of re-deriving per call.
    private val colorCache: Map<Color, ColorValue> = EnumMap<Color, ColorValue>(Color::class.java).also { map ->
        Color.entries.forEach { map[it] = toColorStyle(it)?.color }
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
        color = colorCache[style.fg],
        bgColor = colorCache[style.bg],
        strikethrough = style.strikethrough,
    )

    private fun toColorStyle(color: Color): TextStyle? = when (color) {
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

        // WHISPER
//        Color.WOODS_LIGHT_BG -> TextColors.rgb("#2B3327")
//        Color.WOODS_HEAVY_BG -> TextColors.rgb("#222B20")
//        Color.WATER_SHALLOW_BG -> TextColors.rgb("#26333F")
//        Color.WATER_DEEP_BG -> TextColors.rgb("#1F2A33")
//        Color.ELEVATION_LOW_BG -> TextColors.rgb("#332A20")
//        Color.ELEVATION_HIGH_BG -> TextColors.rgb("#3D3428")

        // SOFT
//        Color.WOODS_LIGHT_BG -> TextColors.rgb("#34402E")
//        Color.WOODS_HEAVY_BG -> TextColors.rgb("#253018")
//        Color.WATER_SHALLOW_BG -> TextColors.rgb("#2C3F52")
//        Color.WATER_DEEP_BG -> TextColors.rgb("#1F2D3D")
//        Color.ELEVATION_LOW_BG -> TextColors.rgb("#3D3020")
//        Color.ELEVATION_HIGH_BG -> TextColors.rgb("#4A3C29")

        // FRESH
        Color.WOODS_LIGHT_BG -> TextColors.rgb("#3E5E33")
        Color.WOODS_HEAVY_BG -> TextColors.rgb("#2C4826")
        Color.WATER_SHALLOW_BG -> TextColors.rgb("#2F5E7E")
        Color.WATER_DEEP_BG -> TextColors.rgb("#234C68")
        Color.ELEVATION_LOW_BG -> TextColors.rgb("#5A4327")
        Color.ELEVATION_HIGH_BG -> TextColors.rgb("#6B5433")

        // VIVID
//        Color.WOODS_LIGHT_BG -> TextColors.rgb("#47762F")
//        Color.WOODS_HEAVY_BG -> TextColors.rgb("#356027")
//        Color.WATER_SHALLOW_BG -> TextColors.rgb("#2E6E96")
//        Color.WATER_DEEP_BG -> TextColors.rgb("#245B7E")
//        Color.ELEVATION_LOW_BG -> TextColors.rgb("#6E4E28")
//        Color.ELEVATION_HIGH_BG -> TextColors.rgb("#866437")

        // MEADOW (brighter / cheerful)
//        Color.WOODS_LIGHT_BG -> TextColors.rgb("#4C7A3A")
//        Color.WOODS_HEAVY_BG -> TextColors.rgb("#38652C")
//        Color.WATER_SHALLOW_BG -> TextColors.rgb("#2F7CA2")
//        Color.WATER_DEEP_BG -> TextColors.rgb("#256A8E")
//        Color.ELEVATION_LOW_BG -> TextColors.rgb("#7C5C30")
//        Color.ELEVATION_HIGH_BG -> TextColors.rgb("#977340")

    }
}
