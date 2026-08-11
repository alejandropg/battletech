package battletech.tui.screen

private fun idx(index: Int): PaletteColor.Indexed = PaletteColor.Indexed(index)

/**
 * [TuiTheme.DARK_256] — all six `TERRAIN_*_BG` roles resolve to [defaultBackground]. The
 * xterm-256 cube has no dark brown and only one usable dark green, so any colored terrain fill
 * that still clears 4.5:1 against board foregrounds forces every foreground toward the same
 * near-white — trading tinted hexes for eleven distinguishable foregrounds is the better tradeoff
 * here, and terrain material is carried by the icon color and glyph instead. See the palette
 * plan's "ANSI-256 themes" table for the full verification.
 */
internal object Dark256Palette : RolePalette {
    override val defaultBackground: PaletteColor = idx(233)

    override fun resolve(role: Color): PaletteColor = when (role) {
        Color.DEFAULT -> idx(253)
        Color.TEXT_PRIMARY -> idx(255)
        Color.TEXT_MUTED -> idx(145)
        // Darker/lower-contrast than a straight 4.5:1 read — coordinates recede rather than
        // compete for attention. See TuiPaletteTest's "subtle roles" tier.
        Color.TEXT_SUBTLE -> idx(239)
        Color.ACCENT -> idx(221)
        Color.INFO -> idx(116)
        Color.SUCCESS -> idx(114)
        Color.WARNING -> idx(221)
        Color.DANGER -> idx(210)
        Color.PLAYER_1 -> idx(117)
        Color.PLAYER_2 -> idx(218)
        Color.DRAFT -> idx(145)
        Color.DISABLED -> idx(244)
        Color.DESTROYED -> idx(251)
        Color.PANEL_BORDER -> idx(71)
        // Ordinary hex borders are decorative grid lines, not information — see DarkPalette's
        // BOARD_BORDER KDoc for the same call in the truecolor theme.
        Color.BOARD_BORDER -> idx(242)
        Color.BOARD_ACTIVE -> idx(221)
        Color.MOVE_WALK -> idx(255)
        Color.MOVE_RUN -> idx(215)
        Color.MOVE_JUMP -> idx(116)
        Color.ATTACK_RANGE -> idx(251)
        Color.LINE_OF_SIGHT -> idx(222)
        Color.TARGET_VALID -> idx(222)
        Color.TARGET_SELECTED -> idx(210)
        Color.TERRAIN_CLEAR_BG,
        Color.TERRAIN_WOODS_LIGHT_BG,
        Color.TERRAIN_WOODS_HEAVY_BG,
        Color.TERRAIN_WATER_SHALLOW_BG,
        Color.TERRAIN_WATER_DEEP_BG,
        Color.TERRAIN_ROUGH_BG,
        -> defaultBackground
        Color.TERRAIN_WOODS_LIGHT_ICON -> idx(150)
        Color.TERRAIN_WOODS_HEAVY_ICON -> idx(71)
        Color.TERRAIN_WATER_ICON -> idx(117)
        Color.TERRAIN_ROUGH_ICON -> idx(180)
        // Darkened a tier each (was 137/179/222) — see DarkPalette's ELEVATION_*_BADGE_BG KDoc.
        Color.ELEVATION_1_BADGE_BG -> idx(101)
        Color.ELEVATION_2_BADGE_BG -> idx(137)
        Color.ELEVATION_HIGH_BADGE_BG -> idx(179)
        Color.ELEVATION_BADGE_FG -> idx(233)
    }
}

/** [TuiTheme.LIGHT_256] — see [Dark256Palette]'s KDoc for why terrain fills collapse to [defaultBackground]. */
internal object Light256Palette : RolePalette {
    override val defaultBackground: PaletteColor = idx(255)

    override fun resolve(role: Color): PaletteColor = when (role) {
        Color.DEFAULT -> idx(235)
        Color.TEXT_PRIMARY -> idx(235)
        Color.TEXT_MUTED -> idx(239)
        Color.TEXT_SUBTLE -> idx(248)
        Color.ACCENT -> idx(58)
        Color.INFO -> idx(23)
        Color.SUCCESS -> idx(22)
        Color.WARNING -> idx(58)
        Color.DANGER -> idx(88)
        Color.PLAYER_1 -> idx(18)
        Color.PLAYER_2 -> idx(89)
        Color.DRAFT -> idx(241)
        Color.DISABLED -> idx(243)
        Color.DESTROYED -> idx(239)
        Color.PANEL_BORDER -> idx(22)
        Color.BOARD_BORDER -> idx(245)
        Color.BOARD_ACTIVE -> idx(94)
        Color.MOVE_WALK -> idx(235)
        Color.MOVE_RUN -> idx(88)
        Color.MOVE_JUMP -> idx(23)
        Color.ATTACK_RANGE -> idx(239)
        Color.LINE_OF_SIGHT -> idx(94)
        Color.TARGET_VALID -> idx(94)
        Color.TARGET_SELECTED -> idx(88)
        Color.TERRAIN_CLEAR_BG,
        Color.TERRAIN_WOODS_LIGHT_BG,
        Color.TERRAIN_WOODS_HEAVY_BG,
        Color.TERRAIN_WATER_SHALLOW_BG,
        Color.TERRAIN_WATER_DEEP_BG,
        Color.TERRAIN_ROUGH_BG,
        -> defaultBackground
        Color.TERRAIN_WOODS_LIGHT_ICON -> idx(22)
        Color.TERRAIN_WOODS_HEAVY_ICON -> idx(22)
        Color.TERRAIN_WATER_ICON -> idx(24)
        Color.TERRAIN_ROUGH_ICON -> idx(94)
        // Muted a tier each (was 187/180/173) — see Dark256Palette's ELEVATION_*_BADGE_BG comment.
        Color.ELEVATION_1_BADGE_BG -> idx(180)
        Color.ELEVATION_2_BADGE_BG -> idx(173)
        Color.ELEVATION_HIGH_BADGE_BG -> idx(137)
        Color.ELEVATION_BADGE_FG -> idx(233)
    }
}
