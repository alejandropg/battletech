package battletech.tui.screen

/**
 * Resolves semantic [Color] roles to [PaletteColor]s for one [TuiTheme]. Rendering views only ever
 * see [Color]; theme-specific RGB, xterm, and SGR values live in [RolePalette] implementations
 * and are exposed here as [PaletteColor].
 */
internal class TuiPalette(private val roles: RolePalette) {
    /** [role]'s foreground. For [Color.DEFAULT] this is the theme's default foreground — the only role where [foreground] and [background] differ. */
    internal fun foreground(role: Color): PaletteColor = roles.resolve(role)

    /** [role]'s background. For [Color.DEFAULT] this is the theme's default background, not [RolePalette.resolve]'s (foreground) value. */
    internal fun background(role: Color): PaletteColor =
        if (role == Color.DEFAULT) roles.defaultBackground else roles.resolve(role)

    internal companion object {
        internal fun forTheme(theme: TuiTheme): TuiPalette = TuiPalette(
            when (theme) {
                TuiTheme.DARK -> DarkPalette
                TuiTheme.LIGHT -> LightPalette
                TuiTheme.DARK_256 -> Dark256Palette
                TuiTheme.LIGHT_256 -> Light256Palette
                TuiTheme.DARK_16 -> Dark16Palette
                TuiTheme.LIGHT_16 -> Light16Palette
            },
        )
    }
}
