package tenter.screen

/**
 * Resolves every [ColorRole] a host application uses — [ChromeRole] plus whatever domain-specific
 * roles the application defines — to a [PaletteColor], all in the same color space. Implemented
 * once per theme as a stateless object whose [foreground] is a single `when` expression; Kotlin
 * checks enum-`when` exhaustiveness at compile time, so adding a role anywhere is a build error
 * in every implementation that doesn't handle it — see [ColorRole]'s KDoc for the two-enum
 * `when` shape this relies on. That guarantee is deliberately relied on instead of a runtime
 * completeness check: a `Map<ColorRole, PaletteColor>` cannot offer the same guarantee (unknown
 * keys are impossible either way, but duplicate keys would silently take the last value, and a
 * missing key would only surface at first use).
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
