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
 * source is parsed; that trades the compile-time guarantee for a **load-time** one — the loader
 * validates the exact role key set (every role present, no unknown names) and rejects the source
 * otherwise, so a missing role is still caught before first use, just later than a build.
 */
public interface RolePalette {
    /** This palette's default background. [foreground] of [ChromeRole.DEFAULT] is the default *foreground*. */
    public val defaultBackground: PaletteColor

    /** [role]'s foreground. For [ChromeRole.DEFAULT] this is the default foreground — the only role where [foreground] and [background] differ. */
    public fun foreground(role: ColorRole): PaletteColor

    /** [role]'s background. For [ChromeRole.DEFAULT] this is [defaultBackground], not [foreground]'s value. */
    public fun background(role: ColorRole): PaletteColor =
        if (role == ChromeRole.DEFAULT) defaultBackground else foreground(role)
}
