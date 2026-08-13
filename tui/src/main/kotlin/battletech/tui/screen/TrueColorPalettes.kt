package battletech.tui.screen

import tenter.screen.ColorRole
import tenter.screen.PaletteColor
import tenter.screen.RolePalette
import tenter.screen.UiRole

/** `0xRRGGBB` -> [PaletteColor.TrueColor]. Keeps the role tables below visually matched to the hex tables they were authored from. */
private fun rgb(value: Int): PaletteColor.TrueColor =
    PaletteColor.TrueColor((value shr 16) and 0xFF, (value shr 8) and 0xFF, value and 0xFF)

/** [TuiTheme.DARK] — see the palette plan's "Truecolor themes" table for the values and their contrast verification. */
internal object DarkPalette : RolePalette {
    override val defaultBackground: PaletteColor = rgb(0x101418)

    override fun foreground(role: ColorRole): PaletteColor = when (role) {
        is UiRole -> ui(role)
        is BoardRole -> board(role)
        else -> error("Unknown color role: $role")
    }

    private fun ui(role: UiRole): PaletteColor = when (role) {
        UiRole.DEFAULT -> rgb(0xDDE2E5)
        UiRole.TEXT_PRIMARY -> rgb(0xF1F3F5)
        UiRole.TEXT_MUTED -> rgb(0xA6ADB4)
        // Darker than a straight-line 4.5:1 read would give — coordinates are meant to recede,
        // not compete with terrain/units for attention. See TuiPaletteTest's "subtle roles" tier.
        UiRole.TEXT_SUBTLE -> rgb(0x4C5054)
        UiRole.ACCENT -> rgb(0xFFD166)
        UiRole.INFO -> rgb(0x77D4E8)
        UiRole.SUCCESS -> rgb(0x8BD17C)
        UiRole.WARNING -> rgb(0xF3D36A)
        UiRole.DANGER -> rgb(0xFF9999)
        UiRole.DRAFT -> rgb(0xA6ADB4)
        UiRole.DISABLED -> rgb(0x899198)
        UiRole.PANEL_BORDER -> rgb(0x72BF72)
    }

    private fun board(role: BoardRole): PaletteColor = when (role) {
        BoardRole.PLAYER_1 -> rgb(0xA8D8FF)
        BoardRole.PLAYER_2 -> rgb(0xFFC0E7)
        BoardRole.DESTROYED -> rgb(0xC0C6CB)
        // Ordinary hex borders are decorative grid lines, not information — terrain icons and the
        // elevation badge already carry the real content, so the grid intentionally recedes.
        BoardRole.BOARD_BORDER -> rgb(0x686E75)
        BoardRole.BOARD_ACTIVE -> rgb(0xFFD166)
        BoardRole.MOVE_WALK -> rgb(0xF1F3F5)
        // Brightened to preserve the board's contrast floor over the restored shallow-water fill.
        BoardRole.MOVE_RUN -> rgb(0xFFC797)
        BoardRole.MOVE_JUMP -> rgb(0x7FE1E5)
        BoardRole.ATTACK_RANGE -> rgb(0xCDD3D8)
        BoardRole.LINE_OF_SIGHT -> rgb(0xFFE17A)
        BoardRole.TARGET_VALID -> rgb(0xFFE17A)
        BoardRole.TARGET_SELECTED -> rgb(0xFFC4C4)
        BoardRole.TERRAIN_CLEAR_BG -> rgb(0x15191C)
        // Restored from the literal-color palette in e51fecd6. These fills are intentionally
        // brighter than the first semantic-theme pass; affected board foregrounds above and the
        // water icon below are raised just enough to retain the palette's contrast guarantee.
        BoardRole.TERRAIN_WOODS_LIGHT_BG -> rgb(0x3E5E33)
        BoardRole.TERRAIN_WOODS_HEAVY_BG -> rgb(0x2C4826)
        BoardRole.TERRAIN_WATER_SHALLOW_BG -> rgb(0x2F5E7E)
        BoardRole.TERRAIN_WATER_DEEP_BG -> rgb(0x234C68)
        BoardRole.TERRAIN_ROUGH_BG -> rgb(0x3C3A35)
        BoardRole.TERRAIN_WOODS_LIGHT_ICON -> rgb(0xA7D99A)
        BoardRole.TERRAIN_WOODS_HEAVY_ICON -> rgb(0x86C979)
        BoardRole.TERRAIN_WATER_ICON -> rgb(0x98DBFF)
        BoardRole.TERRAIN_ROUGH_ICON -> rgb(0xD7B98B)
        // Darkened so an elevated clear hex — now a whole-hex fill, not just the badge cell —
        // doesn't out-compete the mechs standing on it for attention.
        BoardRole.ELEVATION_1_BADGE_BG -> rgb(0x9E7846)
        BoardRole.ELEVATION_2_BADGE_BG -> rgb(0xB58E54)
        BoardRole.ELEVATION_HIGH_BADGE_BG -> rgb(0xC8A769)
        BoardRole.ELEVATION_BADGE_FG -> rgb(0x121416)
    }
}

/** [TuiTheme.LIGHT] — see the palette plan's "Truecolor themes" table for the values and their contrast verification. */
internal object LightPalette : RolePalette {
    override val defaultBackground: PaletteColor = rgb(0xF8F5EE)

    override fun foreground(role: ColorRole): PaletteColor = when (role) {
        is UiRole -> ui(role)
        is BoardRole -> board(role)
        else -> error("Unknown color role: $role")
    }

    private fun ui(role: UiRole): PaletteColor = when (role) {
        UiRole.DEFAULT -> rgb(0x202428)
        UiRole.TEXT_PRIMARY -> rgb(0x202428)
        UiRole.TEXT_MUTED -> rgb(0x45515B)
        UiRole.TEXT_SUBTLE -> rgb(0x9C9EA0)
        UiRole.ACCENT -> rgb(0x684800)
        UiRole.INFO -> rgb(0x005866)
        UiRole.SUCCESS -> rgb(0x245A27)
        UiRole.WARNING -> rgb(0x604A00)
        UiRole.DANGER -> rgb(0x8F1827)
        UiRole.DRAFT -> rgb(0x59636C)
        UiRole.DISABLED -> rgb(0x737B82)
        UiRole.PANEL_BORDER -> rgb(0x245A27)
    }

    private fun board(role: BoardRole): PaletteColor = when (role) {
        BoardRole.PLAYER_1 -> rgb(0x004D87)
        BoardRole.PLAYER_2 -> rgb(0x841857)
        BoardRole.DESTROYED -> rgb(0x45515B)
        BoardRole.BOARD_BORDER -> rgb(0x868B91)
        BoardRole.BOARD_ACTIVE -> rgb(0x684800)
        BoardRole.MOVE_WALK -> rgb(0x202428)
        BoardRole.MOVE_RUN -> rgb(0x7D3900)
        BoardRole.MOVE_JUMP -> rgb(0x005A62)
        BoardRole.ATTACK_RANGE -> rgb(0x45515B)
        BoardRole.LINE_OF_SIGHT -> rgb(0x604A00)
        BoardRole.TARGET_VALID -> rgb(0x604A00)
        BoardRole.TARGET_SELECTED -> rgb(0x8F1827)
        BoardRole.TERRAIN_CLEAR_BG -> rgb(0xF3F0E8)
        // Same intent as DarkPalette: more saturated than the original pale wash. WATER_DEEP has
        // almost no headroom — the water icon's own 4.5:1 against it is the binding constraint —
        // so WATER_SHALLOW (which must stay strictly brighter, per the ordering guarantee) is
        // capped just above it rather than reaching full saturation.
        BoardRole.TERRAIN_WOODS_LIGHT_BG -> rgb(0xC5DEBA)
        BoardRole.TERRAIN_WOODS_HEAVY_BG -> rgb(0xB0CFA6)
        BoardRole.TERRAIN_WATER_SHALLOW_BG -> rgb(0xB0D4EB)
        BoardRole.TERRAIN_WATER_DEEP_BG -> rgb(0xB4D2E2)
        BoardRole.TERRAIN_ROUGH_BG -> rgb(0xDDD6CB)
        BoardRole.TERRAIN_WOODS_LIGHT_ICON -> rgb(0x27632A)
        BoardRole.TERRAIN_WOODS_HEAVY_ICON -> rgb(0x1E5524)
        BoardRole.TERRAIN_WATER_ICON -> rgb(0x0B5E83)
        BoardRole.TERRAIN_ROUGH_ICON -> rgb(0x6E5132)
        // Muted for the same reason as DarkPalette's badges — less conspicuous now that they can
        // fill a whole hex.
        BoardRole.ELEVATION_1_BADGE_BG -> rgb(0xCBB790)
        BoardRole.ELEVATION_2_BADGE_BG -> rgb(0xC3A670)
        BoardRole.ELEVATION_HIGH_BADGE_BG -> rgb(0xB89053)
        BoardRole.ELEVATION_BADGE_FG -> rgb(0x1B1710)
    }
}
