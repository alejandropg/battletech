package tenter.screen

import com.github.ajalt.mordant.rendering.AnsiLevel

/**
 * A [RolePalette] backed by a plain `Map<ColorRole, PaletteColor>` — the shape [RolePalette]'s own
 * KDoc anticipates for "a host application's own file-backed palette": the role set isn't known
 * until a loader has parsed its source, so completeness is a load-time check on [colors] rather
 * than the compile-time exhaustiveness a hand-authored `when`-based palette gets. [level] is the
 * [AnsiLevel] tier [colors] were authored for; every value in [colors] is a [PaletteColor] of the
 * matching subtype (never converted between tiers — see [PaletteColor]'s KDoc).
 *
 * Every [ChromeRole] is required at construction. Domain-role completeness remains with the
 * application's loader, which knows the application's role set. The source map is copied, so
 * later mutation by a loader or caller cannot change a palette after a renderer has cached it.
 *
 * [AnsiLevel.NONE] is not an authoring tier and is rejected: it names a terminal that cannot show
 * color, not a set of color values. Author for the tier the colors use; [ScreenRenderer] drops
 * every SGR tag on its own when the terminal reports NONE.
 */
public class MapRolePalette(
    private val name: String,
    public val level: AnsiLevel,
    override val defaultBackground: PaletteColor,
    colors: Map<ColorRole, PaletteColor>,
) : RolePalette {

    private val colors: Map<ColorRole, PaletteColor> = colors.toMap()

    init {
        require(level != AnsiLevel.NONE) {
            "Palette $name cannot be authored for AnsiLevel.NONE, which has no color values of " +
                "its own. Author it for the tier the palette's colors use; ScreenRenderer " +
                "suppresses every SGR tag when the terminal itself reports NONE."
        }
        val missingChromeRoles = ChromeRole.entries.filterNot(colors.keys::contains)
        require(missingChromeRoles.isEmpty()) {
            "Palette $name is missing chrome roles: ${missingChromeRoles.joinToString(", ")}"
        }
        require(isCorrectTier(defaultBackground)) {
            "Palette $name default background must use $level colors, got $defaultBackground"
        }
        require(colors.values.all(::isCorrectTier)) {
            "Palette $name contains a color that does not use $level"
        }
    }

    override fun foreground(role: ColorRole): PaletteColor =
        colors[role] ?: error("Unknown color role: $role")

    /** [name] — the palette's source name (a built-in stem, or a custom file path), for readable test/log output. */
    override fun toString(): String = name

    private fun isCorrectTier(color: PaletteColor): Boolean = when (level) {
        AnsiLevel.TRUECOLOR -> color is PaletteColor.TrueColor
        AnsiLevel.ANSI256 -> color is PaletteColor.Xterm256
        AnsiLevel.ANSI16 -> color is PaletteColor.Ansi16
        AnsiLevel.NONE -> false
    }
}
