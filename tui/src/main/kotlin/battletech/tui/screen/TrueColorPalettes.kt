package battletech.tui.screen

/** `0xRRGGBB` -> [PaletteColor.TrueColor]. Keeps the role tables below visually matched to the hex tables they were authored from. */
private fun rgb(value: Int): PaletteColor.TrueColor =
    PaletteColor.TrueColor((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)

/** [TuiTheme.DARK] — see the palette plan's "Truecolor themes" table for the values and their contrast verification. */
internal object DarkPalette : RolePalette {
    override val defaultBackground: PaletteColor = rgb(0x101418)

    override fun resolve(role: Color): PaletteColor = when (role) {
        Color.DEFAULT -> rgb(0xDDE2E5)
        Color.TEXT_PRIMARY -> rgb(0xF1F3F5)
        Color.TEXT_MUTED -> rgb(0xA6ADB4)
        // Darker than a straight-line 4.5:1 read would give — coordinates are meant to recede,
        // not compete with terrain/units for attention. See TuiPaletteTest's "subtle roles" tier.
        Color.TEXT_SUBTLE -> rgb(0x4C5054)
        Color.ACCENT -> rgb(0xFFD166)
        Color.INFO -> rgb(0x77D4E8)
        Color.SUCCESS -> rgb(0x8BD17C)
        Color.WARNING -> rgb(0xF3D36A)
        Color.DANGER -> rgb(0xFF9999)
        Color.PLAYER_1 -> rgb(0xA8D8FF)
        Color.PLAYER_2 -> rgb(0xFFC0E7)
        Color.DRAFT -> rgb(0xA6ADB4)
        Color.DISABLED -> rgb(0x899198)
        Color.DESTROYED -> rgb(0xC0C6CB)
        Color.PANEL_BORDER -> rgb(0x72BF72)
        // Ordinary hex borders are decorative grid lines, not information — terrain icons and the
        // elevation badge already carry the real content, so the grid intentionally recedes.
        Color.BOARD_BORDER -> rgb(0x686E75)
        Color.BOARD_ACTIVE -> rgb(0xFFD166)
        Color.MOVE_WALK -> rgb(0xF1F3F5)
        // Brightened to preserve the board's contrast floor over the restored shallow-water fill.
        Color.MOVE_RUN -> rgb(0xFFC797)
        Color.MOVE_JUMP -> rgb(0x7FE1E5)
        Color.ATTACK_RANGE -> rgb(0xCDD3D8)
        Color.LINE_OF_SIGHT -> rgb(0xFFE17A)
        Color.TARGET_VALID -> rgb(0xFFE17A)
        Color.TARGET_SELECTED -> rgb(0xFFC4C4)
        Color.TERRAIN_CLEAR_BG -> rgb(0x15191C)
        // Restored from the literal-color palette in e51fecd6. These fills are intentionally
        // brighter than the first semantic-theme pass; affected board foregrounds above and the
        // water icon below are raised just enough to retain the palette's contrast guarantee.
        Color.TERRAIN_WOODS_LIGHT_BG -> rgb(0x3E5E33)
        Color.TERRAIN_WOODS_HEAVY_BG -> rgb(0x2C4826)
        Color.TERRAIN_WATER_SHALLOW_BG -> rgb(0x2F5E7E)
        Color.TERRAIN_WATER_DEEP_BG -> rgb(0x234C68)
        Color.TERRAIN_ROUGH_BG -> rgb(0x3C3A35)
        Color.TERRAIN_WOODS_LIGHT_ICON -> rgb(0xA7D99A)
        Color.TERRAIN_WOODS_HEAVY_ICON -> rgb(0x86C979)
        Color.TERRAIN_WATER_ICON -> rgb(0x98DBFF)
        Color.TERRAIN_ROUGH_ICON -> rgb(0xD7B98B)
        // Darkened so an elevated clear hex — now a whole-hex fill, not just the badge cell —
        // doesn't out-compete the mechs standing on it for attention.
        Color.ELEVATION_1_BADGE_BG -> rgb(0x9E7846)
        Color.ELEVATION_2_BADGE_BG -> rgb(0xB58E54)
        Color.ELEVATION_HIGH_BADGE_BG -> rgb(0xC8A769)
        Color.ELEVATION_BADGE_FG -> rgb(0x121416)
    }
}

/** [TuiTheme.LIGHT] — see the palette plan's "Truecolor themes" table for the values and their contrast verification. */
internal object LightPalette : RolePalette {
    override val defaultBackground: PaletteColor = rgb(0xF8F5EE)

    override fun resolve(role: Color): PaletteColor = when (role) {
        Color.DEFAULT -> rgb(0x202428)
        Color.TEXT_PRIMARY -> rgb(0x202428)
        Color.TEXT_MUTED -> rgb(0x45515B)
        Color.TEXT_SUBTLE -> rgb(0x9C9EA0)
        Color.ACCENT -> rgb(0x684800)
        Color.INFO -> rgb(0x005866)
        Color.SUCCESS -> rgb(0x245A27)
        Color.WARNING -> rgb(0x604A00)
        Color.DANGER -> rgb(0x8F1827)
        Color.PLAYER_1 -> rgb(0x004D87)
        Color.PLAYER_2 -> rgb(0x841857)
        Color.DRAFT -> rgb(0x59636C)
        Color.DISABLED -> rgb(0x737B82)
        Color.DESTROYED -> rgb(0x45515B)
        Color.PANEL_BORDER -> rgb(0x245A27)
        Color.BOARD_BORDER -> rgb(0x868B91)
        Color.BOARD_ACTIVE -> rgb(0x684800)
        Color.MOVE_WALK -> rgb(0x202428)
        Color.MOVE_RUN -> rgb(0x7D3900)
        Color.MOVE_JUMP -> rgb(0x005A62)
        Color.ATTACK_RANGE -> rgb(0x45515B)
        Color.LINE_OF_SIGHT -> rgb(0x604A00)
        Color.TARGET_VALID -> rgb(0x604A00)
        Color.TARGET_SELECTED -> rgb(0x8F1827)
        Color.TERRAIN_CLEAR_BG -> rgb(0xF3F0E8)
        // Same intent as DarkPalette: more saturated than the original pale wash. WATER_DEEP has
        // almost no headroom — the water icon's own 4.5:1 against it is the binding constraint —
        // so WATER_SHALLOW (which must stay strictly brighter, per the ordering guarantee) is
        // capped just above it rather than reaching full saturation.
        Color.TERRAIN_WOODS_LIGHT_BG -> rgb(0xC5DEBA)
        Color.TERRAIN_WOODS_HEAVY_BG -> rgb(0xB0CFA6)
        Color.TERRAIN_WATER_SHALLOW_BG -> rgb(0xB0D4EB)
        Color.TERRAIN_WATER_DEEP_BG -> rgb(0xB4D2E2)
        Color.TERRAIN_ROUGH_BG -> rgb(0xDDD6CB)
        Color.TERRAIN_WOODS_LIGHT_ICON -> rgb(0x27632A)
        Color.TERRAIN_WOODS_HEAVY_ICON -> rgb(0x1E5524)
        Color.TERRAIN_WATER_ICON -> rgb(0x0B5E83)
        Color.TERRAIN_ROUGH_ICON -> rgb(0x6E5132)
        // Muted for the same reason as DarkPalette's badges — less conspicuous now that they can
        // fill a whole hex.
        Color.ELEVATION_1_BADGE_BG -> rgb(0xCBB790)
        Color.ELEVATION_2_BADGE_BG -> rgb(0xC3A670)
        Color.ELEVATION_HIGH_BADGE_BG -> rgb(0xB89053)
        Color.ELEVATION_BADGE_FG -> rgb(0x1B1710)
    }
}
