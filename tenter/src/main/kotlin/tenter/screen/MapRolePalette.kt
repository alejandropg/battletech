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
 */
public class MapRolePalette(
    private val name: String,
    public val level: AnsiLevel,
    override val defaultBackground: PaletteColor,
    colors: Map<ColorRole, PaletteColor>,
) : RolePalette {

    private val colors: Map<ColorRole, PaletteColor> = colors.toMap()

    init {
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

    override fun foreground(role: ColorRole): PaletteColor = when (role) {
        else -> colors[role] ?: error("Unknown color role: $role")
    }

    /** [name] — the palette's source name (a built-in stem, or a custom file path), for readable test/log output. */
    override fun toString(): String = name

    private fun isCorrectTier(color: PaletteColor): Boolean = when (level) {
        AnsiLevel.TRUECOLOR -> color is PaletteColor.TrueColor
        AnsiLevel.ANSI256 -> color is PaletteColor.Xterm256
        AnsiLevel.ANSI16 -> color is PaletteColor.Ansi16
        AnsiLevel.NONE -> false
    }
}
