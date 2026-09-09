package tenter.screen

/**
 * A conservative ANSI-16 palette for consumers that need a working palette immediately.
 * ANSI-16 colors are terminal-defined, so the terminal's configured palette determines the
 * exact appearance. Construct a new palette/renderer to change roles; palettes are stable for a
 * renderer's lifetime.
 */
public object DefaultRolePalette : RolePalette {

    override val defaultBackground: PaletteColor = PaletteColor.Ansi16(30)

    override fun foreground(role: ColorRole): PaletteColor = when (role) {
        ChromeRole.DEFAULT, ChromeRole.TEXT_PRIMARY -> PaletteColor.Ansi16(97)
        ChromeRole.TEXT_MUTED, ChromeRole.PANEL_BORDER -> PaletteColor.Ansi16(37)
        ChromeRole.TEXT_SUBTLE, ChromeRole.DISABLED -> PaletteColor.Ansi16(90)
        ChromeRole.ACCENT, ChromeRole.WARNING -> PaletteColor.Ansi16(93)
        ChromeRole.INFO -> PaletteColor.Ansi16(96)
        ChromeRole.SUCCESS, ChromeRole.PANEL_BORDER_FOCUSED -> PaletteColor.Ansi16(92)
        ChromeRole.DANGER -> PaletteColor.Ansi16(91)
        ChromeRole.DRAFT -> PaletteColor.Ansi16(95)
        else -> error("DefaultRolePalette only resolves ChromeRole, got: $role")
    }
}
