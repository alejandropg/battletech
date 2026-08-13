package tenter.screen

import com.github.ajalt.colormath.model.Ansi16
import com.github.ajalt.colormath.model.Ansi256
import com.github.ajalt.colormath.model.RGB
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextStyle
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
 * [palette] resolves every [ColorRole] to a [PaletteColor] already authored in the palette's
 * color space — there is no downsampling here or anywhere else in this class. [ansiLevel] is
 * used for exactly one thing: [AnsiLevel.NONE] suppresses tags entirely. Do not grow it back
 * into a conversion knob — that job belongs to which [RolePalette] the caller chose.
 */
internal class TextStyleFactory(private val palette: RolePalette, private val ansiLevel: AnsiLevel) {

    /** Cached open/close ANSI escape strings for one [Cell.Style]; either may be empty. */
    internal class Tags(internal val open: String, internal val close: String)

    // Per-role fg/bg color caches, lazily populated since a host application's ColorRole is an
    // open set (unlike the old closed Color enum, there is no fixed "every role" list to seed
    // these with eagerly). Separate maps because UiRole.DEFAULT resolves to different foreground
    // and background values — a single cache keyed by role could not represent that.
    private val foregroundCache: HashMap<ColorRole, ColorValue> = HashMap()
    private val backgroundCache: HashMap<ColorRole, ColorValue> = HashMap()

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
        color = foregroundCache.getOrPut(style.fg) { palette.foreground(style.fg).toColormathColor() },
        bgColor = backgroundCache.getOrPut(style.bg) { palette.background(style.bg).toColormathColor() },
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
