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
     * default role where [foreground] and [background] differ. Implementations may specialize other
     * backgrounds too. [FixedColorRole] is resolved by the
     * renderer before this function is called, not by a palette implementation.
     */
    public fun foreground(role: ColorRole): PaletteColor

    /** [role]'s background. For [ChromeRole.DEFAULT] this is [defaultBackground], not [foreground]'s value. */
    public fun background(role: ColorRole): PaletteColor =
        if (role == ChromeRole.DEFAULT) defaultBackground else foreground(role)
}

/**
 * Layers a copied override map over this stable palette, preserving all untouched foregrounds,
 * backgrounds, and application roles, including previous overrides. An override replaces both
 * colors of its role, except DEFAULT's background which uses [defaultBackground].
 * Chrome colors and overrides are validated eagerly; delegated application colors are validated
 * when resolved. The base palette must remain stable for the renderer's lifetime.
 */
public fun RolePalette.withOverrides(
    name: String = "overrides",
    overrides: Map<ColorRole, PaletteColor> = emptyMap(),
    defaultBackground: PaletteColor = this.defaultBackground,
): RolePalette = OverrideRolePalette(name, this, overrides, defaultBackground)
