package battletech.tui.screen

import tenter.screen.ColorRole
import tenter.screen.PaletteColor
import tenter.screen.RolePalette
import tenter.screen.ChromeRole

private fun code(value: Int): PaletteColor.Ansi16 = PaletteColor.Ansi16(value)

/**
 * [TuiTheme.DARK_16] — authored against the conventional interpretation of the 16 SGR codes, not
 * verified for contrast: codes `0..15` are remapped by the user's terminal theme, so their sRGB
 * values (and therefore luminance) are unknown at build time. Only role *distinctness* is
 * testable here — do not add a contrast assertion against this palette, it would be asserting
 * something this palette cannot guarantee. See the palette plan's "ANSI-16 themes" section.
 */
internal object Dark16Palette : RolePalette {
    override val defaultBackground: PaletteColor = code(30)

    override fun foreground(role: ColorRole): PaletteColor = when (role) {
        is ChromeRole -> ui(role)
        is BoardRole -> board(role)
        else -> error("Unknown color role: $role")
    }

    private fun ui(role: ChromeRole): PaletteColor = when (role) {
        ChromeRole.DEFAULT -> code(37)
        ChromeRole.TEXT_PRIMARY -> code(97)
        ChromeRole.TEXT_MUTED -> code(37)
        // Bright black ("gray") rather than plain white — coordinates recede instead of matching
        // ordinary text brightness. Matches DISABLED's code, which is the same kind of subdued role.
        ChromeRole.TEXT_SUBTLE -> code(90)
        ChromeRole.ACCENT -> code(93)
        ChromeRole.INFO -> code(96)
        ChromeRole.SUCCESS -> code(92)
        ChromeRole.WARNING -> code(93)
        ChromeRole.DANGER -> code(91)
        ChromeRole.DRAFT -> code(37)
        ChromeRole.DISABLED -> code(90)
        ChromeRole.PANEL_BORDER -> code(32)
    }

    private fun board(role: BoardRole): PaletteColor = when (role) {
        BoardRole.PLAYER_1 -> code(94)
        BoardRole.PLAYER_2 -> code(95)
        BoardRole.DESTROYED -> code(37)
        // Same subdued code as TEXT_SUBTLE — the grid is decorative, not information.
        BoardRole.BOARD_BORDER -> code(90)
        BoardRole.BOARD_ACTIVE -> code(93)
        BoardRole.MOVE_WALK -> code(97)
        BoardRole.MOVE_RUN -> code(33)
        BoardRole.MOVE_JUMP -> code(96)
        BoardRole.ATTACK_RANGE -> code(37)
        BoardRole.LINE_OF_SIGHT -> code(93)
        BoardRole.TARGET_VALID -> code(93)
        BoardRole.TARGET_SELECTED -> code(91)
        BoardRole.TERRAIN_CLEAR_BG,
        BoardRole.TERRAIN_WOODS_LIGHT_BG,
        BoardRole.TERRAIN_WOODS_HEAVY_BG,
        BoardRole.TERRAIN_WATER_SHALLOW_BG,
        BoardRole.TERRAIN_WATER_DEEP_BG,
        BoardRole.TERRAIN_ROUGH_BG,
        -> defaultBackground
        BoardRole.TERRAIN_WOODS_LIGHT_ICON -> code(92)
        BoardRole.TERRAIN_WOODS_HEAVY_ICON -> code(32)
        BoardRole.TERRAIN_WATER_ICON -> code(94)
        BoardRole.TERRAIN_ROUGH_ICON -> code(33)
        BoardRole.ELEVATION_1_BADGE_BG -> code(33)
        BoardRole.ELEVATION_2_BADGE_BG -> code(93)
        BoardRole.ELEVATION_HIGH_BADGE_BG -> code(97)
        BoardRole.ELEVATION_BADGE_FG -> code(30)
    }
}

/** [TuiTheme.LIGHT_16] — see [Dark16Palette]'s KDoc: distinctness-tested, not contrast-tested. */
internal object Light16Palette : RolePalette {
    override val defaultBackground: PaletteColor = code(97)

    override fun foreground(role: ColorRole): PaletteColor = when (role) {
        is ChromeRole -> ui(role)
        is BoardRole -> board(role)
        else -> error("Unknown color role: $role")
    }

    private fun ui(role: ChromeRole): PaletteColor = when (role) {
        ChromeRole.DEFAULT -> code(30)
        ChromeRole.TEXT_PRIMARY -> code(30)
        ChromeRole.TEXT_MUTED -> code(30)
        ChromeRole.TEXT_SUBTLE -> code(90)
        ChromeRole.ACCENT -> code(35)
        ChromeRole.INFO -> code(34)
        ChromeRole.SUCCESS -> code(32)
        ChromeRole.WARNING -> code(33)
        ChromeRole.DANGER -> code(31)
        ChromeRole.DRAFT -> code(90)
        ChromeRole.DISABLED -> code(90)
        ChromeRole.PANEL_BORDER -> code(32)
    }

    private fun board(role: BoardRole): PaletteColor = when (role) {
        BoardRole.PLAYER_1 -> code(34)
        BoardRole.PLAYER_2 -> code(35)
        BoardRole.DESTROYED -> code(90)
        BoardRole.BOARD_BORDER -> code(30)
        BoardRole.BOARD_ACTIVE -> code(35)
        BoardRole.MOVE_WALK -> code(30)
        BoardRole.MOVE_RUN -> code(31)
        BoardRole.MOVE_JUMP -> code(36)
        BoardRole.ATTACK_RANGE -> code(90)
        BoardRole.LINE_OF_SIGHT -> code(33)
        BoardRole.TARGET_VALID -> code(33)
        BoardRole.TARGET_SELECTED -> code(31)
        BoardRole.TERRAIN_CLEAR_BG,
        BoardRole.TERRAIN_WOODS_LIGHT_BG,
        BoardRole.TERRAIN_WOODS_HEAVY_BG,
        BoardRole.TERRAIN_WATER_SHALLOW_BG,
        BoardRole.TERRAIN_WATER_DEEP_BG,
        BoardRole.TERRAIN_ROUGH_BG,
        -> defaultBackground
        BoardRole.TERRAIN_WOODS_LIGHT_ICON -> code(32)
        BoardRole.TERRAIN_WOODS_HEAVY_ICON -> code(32)
        BoardRole.TERRAIN_WATER_ICON -> code(34)
        BoardRole.TERRAIN_ROUGH_ICON -> code(33)
        BoardRole.ELEVATION_1_BADGE_BG -> code(33)
        BoardRole.ELEVATION_2_BADGE_BG -> code(93)
        BoardRole.ELEVATION_HIGH_BADGE_BG -> code(96)
        BoardRole.ELEVATION_BADGE_FG -> code(30)
    }
}
