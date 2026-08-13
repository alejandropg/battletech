package battletech.tui.screen

import tenter.screen.RolePalette

/** The concrete [RolePalette] this theme selects — the one place [DarkPalette] etc. are named. */
internal fun TuiTheme.toRolePalette(): RolePalette = when (this) {
    TuiTheme.DARK -> DarkPalette
    TuiTheme.LIGHT -> LightPalette
    TuiTheme.DARK_256 -> Dark256Palette
    TuiTheme.LIGHT_256 -> Light256Palette
    TuiTheme.DARK_16 -> Dark16Palette
    TuiTheme.LIGHT_16 -> Light16Palette
}
