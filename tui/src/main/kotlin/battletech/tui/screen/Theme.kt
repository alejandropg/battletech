package battletech.tui.screen

import com.github.ajalt.mordant.rendering.AnsiLevel
import tenter.screen.ColorRole
import tenter.screen.PaletteColor
import tenter.screen.RolePalette

/**
 * A [RolePalette] loaded from a theme file by [resolveTheme] — see `docs/color-themes.md` for the
 * file format and the rationale behind each built-in theme's values. [level] is the
 * [AnsiLevel] tier [colors] were authored for; every value in [colors] is a [PaletteColor] of the
 * matching subtype (never converted between tiers — see [PaletteColor]'s KDoc).
 *
 * [colors] must already contain every [tenter.screen.ChromeRole] and [BoardRole] — that
 * completeness is [ThemeFile.toTheme]'s job, not this constructor's; a role missing here fails at
 * first [foreground] call rather than at load time. Construct through [resolveTheme]/[ThemeLoader]
 * in production; a hand-built [Theme] is only for tests exercising [RolePalette] in isolation.
 */
public class Theme(
    private val name: String,
    internal val level: AnsiLevel,
    override val defaultBackground: PaletteColor,
    private val colors: Map<ColorRole, PaletteColor>,
) : RolePalette {

    override fun foreground(role: ColorRole): PaletteColor =
        colors[role] ?: error("Unknown color role: $role")

    /** [name] — the theme's file stem (built-in) or path (custom), for readable test/log output. */
    override fun toString(): String = name
}
