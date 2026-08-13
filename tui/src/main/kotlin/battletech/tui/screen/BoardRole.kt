package battletech.tui.screen

import tenter.screen.ColorRole

/**
 * The tactical-board-specific color roles: terrain, movement, player, and elevation semantics
 * that have no meaning to `tenter`'s generic widgets. Every [battletech.tui.screen.RolePalette]
 * (via [tenter.screen.RolePalette]) resolves these alongside [tenter.screen.ChromeRole] — see
 * [ColorRole]'s KDoc for the two-enum `when` shape every palette implements.
 *
 * Several roles deliberately resolve to the same value in some themes (e.g. `ACCENT`/
 * `BOARD_ACTIVE` from [tenter.screen.ChromeRole], or `DANGER`/[TARGET_SELECTED] here). That is
 * intentional, not duplication: they are semantically distinct call sites that happen to share a
 * color today, and keeping them separate lets a theme diverge them later without touching a
 * single call site. Do not merge them into one entry.
 */
public enum class BoardRole : ColorRole {
    PLAYER_1,
    PLAYER_2,
    DESTROYED,
    BOARD_BORDER,
    BOARD_ACTIVE,
    MOVE_WALK,
    MOVE_RUN,
    MOVE_JUMP,
    ATTACK_RANGE,
    LINE_OF_SIGHT,
    TARGET_VALID,
    TARGET_SELECTED,

    TERRAIN_CLEAR_BG,
    TERRAIN_WOODS_LIGHT_BG,
    TERRAIN_WOODS_HEAVY_BG,
    TERRAIN_WATER_SHALLOW_BG,
    TERRAIN_WATER_DEEP_BG,
    TERRAIN_ROUGH_BG,
    TERRAIN_WOODS_LIGHT_ICON,
    TERRAIN_WOODS_HEAVY_ICON,
    TERRAIN_WATER_ICON,
    TERRAIN_ROUGH_ICON,

    ELEVATION_1_BADGE_BG,
    ELEVATION_2_BADGE_BG,
    ELEVATION_HIGH_BADGE_BG,
    ELEVATION_BADGE_FG,
}
