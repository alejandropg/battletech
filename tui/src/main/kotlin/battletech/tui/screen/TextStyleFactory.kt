package battletech.tui.screen

import com.github.ajalt.colormath.model.Ansi16
import com.github.ajalt.colormath.model.Ansi256
import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.rendering.AnsiLevel
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
 * [palette] resolves every [Color] role to a [PaletteColor] already authored in the right color
 * space for [ansiLevel] — there is no downsampling here or anywhere else in this class.
 * [ansiLevel] is used for exactly one thing: [AnsiLevel.NONE] suppresses tags entirely. Do not
 * grow it back into a conversion knob — that job now belongs to which [TuiTheme] was selected.
 */
internal class TextStyleFactory(private val palette: TuiPalette, private val ansiLevel: AnsiLevel) {

    /** Cached open/close ANSI escape strings for one [Cell.Style]; either may be empty. */
    internal class Tags(internal val open: String, internal val close: String)

    // Per-role fg/bg color caches. Separate maps because Color.DEFAULT resolves to different
    // foreground and background values — a single cache keyed by Color could not represent that.
    // Populated eagerly since Color has few entries; buildStyle reads straight from these instead
    // of re-deriving per call.
    private val foregroundCache: Map<Color, ColorValue> = EnumMap<Color, ColorValue>(Color::class.java).also { map ->
        Color.entries.forEach { map[it] = palette.foreground(it).toColormathColor() }
    }
    private val backgroundCache: Map<Color, ColorValue> = EnumMap<Color, ColorValue>(Color::class.java).also { map ->
        Color.entries.forEach { map[it] = palette.background(it).toColormathColor() }
    }

    // Tags cache, keyed by the full Cell.Style. Lazily populated on first use for each distinct
    // Style — each entry pays Mordant's one-off styling cost exactly once.
    private val tagsCache: HashMap<Cell.Style, Tags> = HashMap()

    /** The open/close tag pair for [style], or `null` if it renders no ANSI codes at all. */
    internal fun tagsFor(style: Cell.Style): Tags? {
        if (ansiLevel == AnsiLevel.NONE) return null

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
        color = foregroundCache[style.fg],
        bgColor = backgroundCache[style.bg],
        strikethrough = style.strikethrough,
    )

    /** [PaletteColor] -> its colormath equivalent, matching the color space it was authored in. */
    private fun PaletteColor.toColormathColor(): ColorValue = when (this) {
        is PaletteColor.TrueColor -> RGB.from255(red, green, blue)
        is PaletteColor.Indexed -> Ansi256(index)
        is PaletteColor.Basic -> Ansi16(code)
    }

    private companion object {
        // A literal space can't appear inside an SGR/OSC escape sequence, so styling a
        // single-space sentinel safely marks the boundary between Mordant's open and close tags.
        private const val SENTINEL = " "
    }
}
