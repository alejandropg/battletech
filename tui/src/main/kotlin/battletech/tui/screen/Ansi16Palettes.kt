package battletech.tui.screen

private fun code(value: Int): PaletteColor.Basic = PaletteColor.Basic(value)

/**
 * [TuiTheme.DARK_16] — authored against the conventional interpretation of the 16 SGR codes, not
 * verified for contrast: codes `0..15` are remapped by the user's terminal theme, so their sRGB
 * values (and therefore luminance) are unknown at build time. Only role *distinctness* is
 * testable here — do not add a contrast assertion against this palette, it would be asserting
 * something this palette cannot guarantee. See the palette plan's "ANSI-16 themes" section.
 */
internal object Dark16Palette : RolePalette {
    override val defaultBackground: PaletteColor = code(30)

    override fun resolve(role: Color): PaletteColor = when (role) {
        Color.DEFAULT -> code(37)
        Color.TEXT_PRIMARY -> code(97)
        Color.TEXT_MUTED -> code(37)
        // Bright black ("gray") rather than plain white — coordinates recede instead of matching
        // ordinary text brightness. Matches DISABLED's code, which is the same kind of subdued role.
        Color.TEXT_SUBTLE -> code(90)
        Color.ACCENT -> code(93)
        Color.INFO -> code(96)
        Color.SUCCESS -> code(92)
        Color.WARNING -> code(93)
        Color.DANGER -> code(91)
        Color.PLAYER_1 -> code(94)
        Color.PLAYER_2 -> code(95)
        Color.DRAFT -> code(37)
        Color.DISABLED -> code(90)
        Color.DESTROYED -> code(37)
        Color.PANEL_BORDER -> code(32)
        // Same subdued code as TEXT_SUBTLE — the grid is decorative, not information.
        Color.BOARD_BORDER -> code(90)
        Color.BOARD_ACTIVE -> code(93)
        Color.MOVE_WALK -> code(97)
        Color.MOVE_RUN -> code(33)
        Color.MOVE_JUMP -> code(96)
        Color.ATTACK_RANGE -> code(37)
        Color.LINE_OF_SIGHT -> code(93)
        Color.TARGET_VALID -> code(93)
        Color.TARGET_SELECTED -> code(91)
        Color.TERRAIN_CLEAR_BG,
        Color.TERRAIN_WOODS_LIGHT_BG,
        Color.TERRAIN_WOODS_HEAVY_BG,
        Color.TERRAIN_WATER_SHALLOW_BG,
        Color.TERRAIN_WATER_DEEP_BG,
        Color.TERRAIN_ROUGH_BG,
        -> defaultBackground
        Color.TERRAIN_WOODS_LIGHT_ICON -> code(92)
        Color.TERRAIN_WOODS_HEAVY_ICON -> code(32)
        Color.TERRAIN_WATER_ICON -> code(94)
        Color.TERRAIN_ROUGH_ICON -> code(33)
        Color.ELEVATION_1_BADGE_BG -> code(33)
        Color.ELEVATION_2_BADGE_BG -> code(93)
        Color.ELEVATION_HIGH_BADGE_BG -> code(97)
        Color.ELEVATION_BADGE_FG -> code(30)
    }
}

/** [TuiTheme.LIGHT_16] — see [Dark16Palette]'s KDoc: distinctness-tested, not contrast-tested. */
internal object Light16Palette : RolePalette {
    override val defaultBackground: PaletteColor = code(97)

    override fun resolve(role: Color): PaletteColor = when (role) {
        Color.DEFAULT -> code(30)
        Color.TEXT_PRIMARY -> code(30)
        Color.TEXT_MUTED -> code(30)
        Color.TEXT_SUBTLE -> code(90)
        Color.ACCENT -> code(35)
        Color.INFO -> code(34)
        Color.SUCCESS -> code(32)
        Color.WARNING -> code(33)
        Color.DANGER -> code(31)
        Color.PLAYER_1 -> code(34)
        Color.PLAYER_2 -> code(35)
        Color.DRAFT -> code(90)
        Color.DISABLED -> code(90)
        Color.DESTROYED -> code(90)
        Color.PANEL_BORDER -> code(32)
        Color.BOARD_BORDER -> code(30)
        Color.BOARD_ACTIVE -> code(35)
        Color.MOVE_WALK -> code(30)
        Color.MOVE_RUN -> code(31)
        Color.MOVE_JUMP -> code(36)
        Color.ATTACK_RANGE -> code(90)
        Color.LINE_OF_SIGHT -> code(33)
        Color.TARGET_VALID -> code(33)
        Color.TARGET_SELECTED -> code(31)
        Color.TERRAIN_CLEAR_BG,
        Color.TERRAIN_WOODS_LIGHT_BG,
        Color.TERRAIN_WOODS_HEAVY_BG,
        Color.TERRAIN_WATER_SHALLOW_BG,
        Color.TERRAIN_WATER_DEEP_BG,
        Color.TERRAIN_ROUGH_BG,
        -> defaultBackground
        Color.TERRAIN_WOODS_LIGHT_ICON -> code(32)
        Color.TERRAIN_WOODS_HEAVY_ICON -> code(32)
        Color.TERRAIN_WATER_ICON -> code(34)
        Color.TERRAIN_ROUGH_ICON -> code(33)
        Color.ELEVATION_1_BADGE_BG -> code(33)
        Color.ELEVATION_2_BADGE_BG -> code(93)
        Color.ELEVATION_HIGH_BADGE_BG -> code(96)
        Color.ELEVATION_BADGE_FG -> code(30)
    }
}
