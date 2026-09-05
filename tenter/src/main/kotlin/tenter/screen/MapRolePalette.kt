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
 * Completeness (every role a host's [RolePalette] must cover present in [colors]) is the loader's
 * job, not this constructor's — a role missing here fails at first [foreground] call rather than
 * at load time.
 */
public class MapRolePalette(
    private val name: String,
    public val level: AnsiLevel,
    override val defaultBackground: PaletteColor,
    private val colors: Map<ColorRole, PaletteColor>,
) : RolePalette {

    override fun foreground(role: ColorRole): PaletteColor = when (role) {
        is FixedColorRole -> role.color
        else -> colors[role] ?: error("Unknown color role: $role")
    }

    /** [name] — the palette's source name (a built-in stem, or a custom file path), for readable test/log output. */
    override fun toString(): String = name
}
