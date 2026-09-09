package tenter.screen

/**
 * Resolves every [ColorRole] a host application uses — [ChromeRole] plus whatever domain-specific
 * roles the application defines — to a [PaletteColor], all in the same color space.
 *
 * Two implementation shapes exist. A palette hand-authored in Kotlin as a stateless object with
 * [foreground] a single `when` expression gets its completeness checked at **compile** time —
 * Kotlin's enum-`when` exhaustiveness makes "you forgot a role" a build error in every such
 * implementation (see [ColorRole]'s KDoc for the two-enum `when` shape this relies on). A palette
 * loaded from an external source (a host application's own file-backed palette, say) is
 * necessarily backed by something map-shaped instead, since the role set isn't known until the
 * source is parsed; that trades the compile-time guarantee for a **load-time** one. [MapRolePalette]
 * validates every toolkit role before construction; an application loader remains responsible for
 * validating its own domain-role set.
 */
public interface RolePalette {
    /** This palette's default background. [foreground] of [ChromeRole.DEFAULT] is the default *foreground*. */
    public val defaultBackground: PaletteColor

    /**
     * [role]'s semantic foreground. For [ChromeRole.DEFAULT] this is the default foreground — the
     * only role where [foreground] and [background] differ. [FixedColorRole] is resolved by the
     * renderer before this function is called, not by a palette implementation.
     */
    public fun foreground(role: ColorRole): PaletteColor

    /** [role]'s background. For [ChromeRole.DEFAULT] this is [defaultBackground], not [foreground]'s value. */
    public fun background(role: ColorRole): PaletteColor =
        if (role == ChromeRole.DEFAULT) defaultBackground else foreground(role)
}

/**
 * Returns a copied palette containing every chrome role from this palette and [overrides].
 * Overrides may replace chrome roles or add application-specific roles. The returned palette
 * validates its complete chrome set and color tier before it can be rendered; roles not present
 * in either the chrome set or [overrides] still fail clearly when resolved.
 */
public fun RolePalette.withOverrides(
    name: String = "overrides",
    overrides: Map<ColorRole, PaletteColor> = emptyMap(),
    defaultBackground: PaletteColor = this.defaultBackground,
): MapRolePalette {
    val colors = ChromeRole.entries.associateWith { role -> foreground(role) } + overrides
    return MapRolePalette(
        name = name,
        level = levelOf(defaultBackground),
        defaultBackground = defaultBackground,
        colors = colors,
    )
}

private fun levelOf(color: PaletteColor): com.github.ajalt.mordant.rendering.AnsiLevel = when (color) {
    is PaletteColor.TrueColor -> com.github.ajalt.mordant.rendering.AnsiLevel.TRUECOLOR
    is PaletteColor.Xterm256 -> com.github.ajalt.mordant.rendering.AnsiLevel.ANSI256
    is PaletteColor.Ansi16 -> com.github.ajalt.mordant.rendering.AnsiLevel.ANSI16
}
