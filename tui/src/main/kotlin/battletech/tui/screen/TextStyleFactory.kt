package battletech.tui.screen

import com.github.ajalt.colormath.model.Ansi16
import com.github.ajalt.colormath.model.Ansi256
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import java.util.EnumMap
import com.github.ajalt.colormath.Color as ColorValue

/**
 * Renders [Cell.Style]s to cached open/close ANSI escape strings.
 *
 * Mordant styles text through [TextStyle.invoke], which runs a regex scan over the styled text —
 * fine for occasional one-off printing, but too costly to repeat for every run of every row of
 * every frame. Each distinct [Cell.Style] is styled through Mordant exactly once, at first use,
 * and the result is split into an open/close pair ([Tags]) that the renderer can then paste
 * around plain text with no further Mordant involvement.
 *
 * Colors are downsampled to [ansiLevel] once here too, since [ScreenRenderer] writes with
 * [com.github.ajalt.mordant.terminal.Terminal.rawPrint], which — unlike
 * [com.github.ajalt.mordant.terminal.Terminal.print] — bypasses Mordant's own downsampling.
 */
internal class TextStyleFactory(private val ansiLevel: AnsiLevel = AnsiLevel.TRUECOLOR) {

    /** Cached open/close ANSI escape strings for one [Cell.Style]; either may be empty. */
    internal class Tags(internal val open: String, internal val close: String)

    // Per-color fg/bg color caches, downsampled once to `ansiLevel` — null means "unstyled /
    // DEFAULT" (or, at AnsiLevel.NONE, simply unused — see tagsFor). Populated eagerly since
    // Color has few entries; buildStyle reads straight from these instead of re-deriving per call.
    private val colorCache: Map<Color, ColorValue> = EnumMap<Color, ColorValue>(Color::class.java).also { map ->
        Color.entries.forEach { map[it] = toColorStyle(it)?.color?.downsample(ansiLevel) }
    }

    // Tags cache, keyed by the full Cell.Style. Lazily populated on first use for each distinct
    // Style — each entry pays Mordant's one-off styling cost exactly once.
    private val tagsCache: HashMap<Cell.Style, Tags> = HashMap()

    /** The open/close tag pair for [style], or `null` if it renders no ANSI codes at all. */
    internal fun tagsFor(style: Cell.Style): Tags? {
        if (style == Cell.Style.DEFAULT || ansiLevel == AnsiLevel.NONE) return null

        tagsCache[style]?.let { return it }

        val rendered = buildStyle(style)(SENTINEL)
        val split = rendered.indexOf(SENTINEL)
        val tags = if (split < 0) {
            Tags("", "")
        } else {
            Tags(rendered.substring(0, split), rendered.substring(split + SENTINEL.length))
        }
        tagsCache[style] = tags
        return tags
    }

    private fun buildStyle(style: Cell.Style): TextStyle = TextStyle(
        color = colorCache[style.fg],
        bgColor = colorCache[style.bg],
        strikethrough = style.strikethrough,
    )

    private fun ColorValue.downsample(level: AnsiLevel): ColorValue? = when (level) {
        AnsiLevel.NONE -> null
        AnsiLevel.ANSI16 -> if (this is Ansi16) this else toSRGB().clamp().toAnsi16()
        AnsiLevel.ANSI256 -> if (this is Ansi16 || this is Ansi256) this else toSRGB().clamp().toAnsi256()
        AnsiLevel.TRUECOLOR -> this
    }

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
//        Color.ROUGH_BG -> TextColors.rgb("#332C24")

        // SOFT
//        Color.WOODS_LIGHT_BG -> TextColors.rgb("#34402E")
//        Color.WOODS_HEAVY_BG -> TextColors.rgb("#253018")
//        Color.WATER_SHALLOW_BG -> TextColors.rgb("#2C3F52")
//        Color.WATER_DEEP_BG -> TextColors.rgb("#1F2D3D")
//        Color.ELEVATION_LOW_BG -> TextColors.rgb("#3D3020")
//        Color.ELEVATION_HIGH_BG -> TextColors.rgb("#4A3C29")
//        Color.ROUGH_BG -> TextColors.rgb("#3D3226")

        // FRESH
        Color.WOODS_LIGHT_BG -> TextColors.rgb("#3E5E33")
        Color.WOODS_HEAVY_BG -> TextColors.rgb("#2C4826")
        Color.WATER_SHALLOW_BG -> TextColors.rgb("#2F5E7E")
        Color.WATER_DEEP_BG -> TextColors.rgb("#234C68")
        Color.ELEVATION_LOW_BG -> TextColors.rgb("#5A4327")
        Color.ELEVATION_HIGH_BG -> TextColors.rgb("#6B5433")
        Color.ROUGH_BG -> TextColors.rgb("#4A4030")

        // VIVID
//        Color.WOODS_LIGHT_BG -> TextColors.rgb("#47762F")
//        Color.WOODS_HEAVY_BG -> TextColors.rgb("#356027")
//        Color.WATER_SHALLOW_BG -> TextColors.rgb("#2E6E96")
//        Color.WATER_DEEP_BG -> TextColors.rgb("#245B7E")
//        Color.ELEVATION_LOW_BG -> TextColors.rgb("#6E4E28")
//        Color.ELEVATION_HIGH_BG -> TextColors.rgb("#866437")
//        Color.ROUGH_BG -> TextColors.rgb("#5C4E38")

        // MEADOW (brighter / cheerful)
//        Color.WOODS_LIGHT_BG -> TextColors.rgb("#4C7A3A")
//        Color.WOODS_HEAVY_BG -> TextColors.rgb("#38652C")
//        Color.WATER_SHALLOW_BG -> TextColors.rgb("#2F7CA2")
//        Color.WATER_DEEP_BG -> TextColors.rgb("#256A8E")
//        Color.ELEVATION_LOW_BG -> TextColors.rgb("#7C5C30")
//        Color.ELEVATION_HIGH_BG -> TextColors.rgb("#977340")
//        Color.ROUGH_BG -> TextColors.rgb("#665640")

    }

    private companion object {
        // A literal space can't appear inside an SGR/OSC escape sequence, so styling a
        // single-space sentinel safely marks the boundary between Mordant's open and close tags.
        private const val SENTINEL = " "
    }
}
