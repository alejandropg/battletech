package battletech.tui.screen

import tenter.screen.ColorRole
import tenter.screen.PaletteColor
import tenter.screen.RolePalette
import tenter.screen.UiRole

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

    override fun resolve(role: ColorRole): PaletteColor = when (role) {
        is UiRole -> ui(role)
        is BoardRole -> board(role)
        else -> error("Unknown color role: $role")
    }

    private fun ui(role: UiRole): PaletteColor = when (role) {
        UiRole.DEFAULT -> idx(253)
        UiRole.TEXT_PRIMARY -> idx(255)
        UiRole.TEXT_MUTED -> idx(145)
        // Darker/lower-contrast than a straight 4.5:1 read — coordinates recede rather than
        // compete for attention. See TuiPaletteTest's "subtle roles" tier.
        UiRole.TEXT_SUBTLE -> idx(239)
        UiRole.ACCENT -> idx(221)
        UiRole.INFO -> idx(116)
        UiRole.SUCCESS -> idx(114)
        UiRole.WARNING -> idx(221)
        UiRole.DANGER -> idx(210)
        UiRole.DRAFT -> idx(145)
        UiRole.DISABLED -> idx(244)
        UiRole.PANEL_BORDER -> idx(71)
    }

    private fun board(role: BoardRole): PaletteColor = when (role) {
        BoardRole.PLAYER_1 -> idx(117)
        BoardRole.PLAYER_2 -> idx(218)
        BoardRole.DESTROYED -> idx(251)
        // Ordinary hex borders are decorative grid lines, not information — see DarkPalette's
        // BOARD_BORDER KDoc for the same call in the truecolor theme.
        BoardRole.BOARD_BORDER -> idx(242)
        BoardRole.BOARD_ACTIVE -> idx(221)
        BoardRole.MOVE_WALK -> idx(255)
        BoardRole.MOVE_RUN -> idx(215)
        BoardRole.MOVE_JUMP -> idx(116)
        BoardRole.ATTACK_RANGE -> idx(251)
        BoardRole.LINE_OF_SIGHT -> idx(222)
        BoardRole.TARGET_VALID -> idx(222)
        BoardRole.TARGET_SELECTED -> idx(210)
        BoardRole.TERRAIN_CLEAR_BG,
        BoardRole.TERRAIN_WOODS_LIGHT_BG,
        BoardRole.TERRAIN_WOODS_HEAVY_BG,
        BoardRole.TERRAIN_WATER_SHALLOW_BG,
        BoardRole.TERRAIN_WATER_DEEP_BG,
        BoardRole.TERRAIN_ROUGH_BG,
        -> defaultBackground
        BoardRole.TERRAIN_WOODS_LIGHT_ICON -> idx(150)
        BoardRole.TERRAIN_WOODS_HEAVY_ICON -> idx(71)
        BoardRole.TERRAIN_WATER_ICON -> idx(117)
        BoardRole.TERRAIN_ROUGH_ICON -> idx(180)
        // Darkened a tier each (was 137/179/222) — see DarkPalette's ELEVATION_*_BADGE_BG KDoc.
        BoardRole.ELEVATION_1_BADGE_BG -> idx(101)
        BoardRole.ELEVATION_2_BADGE_BG -> idx(137)
        BoardRole.ELEVATION_HIGH_BADGE_BG -> idx(179)
        BoardRole.ELEVATION_BADGE_FG -> idx(233)
    }
}

/** [TuiTheme.LIGHT_256] — see [Dark256Palette]'s KDoc for why terrain fills collapse to [defaultBackground]. */
internal object Light256Palette : RolePalette {
    override val defaultBackground: PaletteColor = idx(255)

    override fun resolve(role: ColorRole): PaletteColor = when (role) {
        is UiRole -> ui(role)
        is BoardRole -> board(role)
        else -> error("Unknown color role: $role")
    }

    private fun ui(role: UiRole): PaletteColor = when (role) {
        UiRole.DEFAULT -> idx(235)
        UiRole.TEXT_PRIMARY -> idx(235)
        UiRole.TEXT_MUTED -> idx(239)
        UiRole.TEXT_SUBTLE -> idx(248)
        UiRole.ACCENT -> idx(58)
        UiRole.INFO -> idx(23)
        UiRole.SUCCESS -> idx(22)
        UiRole.WARNING -> idx(58)
        UiRole.DANGER -> idx(88)
        UiRole.DRAFT -> idx(241)
        UiRole.DISABLED -> idx(243)
        UiRole.PANEL_BORDER -> idx(22)
    }

    private fun board(role: BoardRole): PaletteColor = when (role) {
        BoardRole.PLAYER_1 -> idx(18)
        BoardRole.PLAYER_2 -> idx(89)
        BoardRole.DESTROYED -> idx(239)
        BoardRole.BOARD_BORDER -> idx(245)
        BoardRole.BOARD_ACTIVE -> idx(94)
        BoardRole.MOVE_WALK -> idx(235)
        BoardRole.MOVE_RUN -> idx(88)
        BoardRole.MOVE_JUMP -> idx(23)
        BoardRole.ATTACK_RANGE -> idx(239)
        BoardRole.LINE_OF_SIGHT -> idx(94)
        BoardRole.TARGET_VALID -> idx(94)
        BoardRole.TARGET_SELECTED -> idx(88)
        BoardRole.TERRAIN_CLEAR_BG,
        BoardRole.TERRAIN_WOODS_LIGHT_BG,
        BoardRole.TERRAIN_WOODS_HEAVY_BG,
        BoardRole.TERRAIN_WATER_SHALLOW_BG,
        BoardRole.TERRAIN_WATER_DEEP_BG,
        BoardRole.TERRAIN_ROUGH_BG,
        -> defaultBackground
        BoardRole.TERRAIN_WOODS_LIGHT_ICON -> idx(22)
        BoardRole.TERRAIN_WOODS_HEAVY_ICON -> idx(22)
        BoardRole.TERRAIN_WATER_ICON -> idx(24)
        BoardRole.TERRAIN_ROUGH_ICON -> idx(94)
        // Muted a tier each (was 187/180/173) — see Dark256Palette's ELEVATION_*_BADGE_BG comment.
        BoardRole.ELEVATION_1_BADGE_BG -> idx(180)
        BoardRole.ELEVATION_2_BADGE_BG -> idx(173)
        BoardRole.ELEVATION_HIGH_BADGE_BG -> idx(137)
        BoardRole.ELEVATION_BADGE_FG -> idx(233)
    }
}
