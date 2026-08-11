package battletech.tui.screen

/**
 * A semantic color role. Every role resolves to a concrete color through the active
 * [TuiTheme]/[TuiPalette]. Rendering callers use only these roles; concrete RGB, xterm, and SGR
 * values are confined to the [RolePalette] implementations.
 *
 * Several roles deliberately resolve to the same value in some themes (e.g. [ACCENT]/
 * [BOARD_ACTIVE], [DANGER]/[TARGET_SELECTED], [LINE_OF_SIGHT]/[TARGET_VALID]). That is
 * intentional, not duplication: they are semantically distinct call sites that happen to share a
 * color today, and keeping them separate lets a theme diverge them later without touching a
 * single call site. Do not merge them into one entry.
 */
public enum class Color {
    /** The theme's default surface — the only role whose foreground and background differ. */
    DEFAULT,

    TEXT_PRIMARY,
    TEXT_MUTED,
    TEXT_SUBTLE,
    ACCENT,
    INFO,
    SUCCESS,
    WARNING,
    DANGER,
    PLAYER_1,
    PLAYER_2,
    DRAFT,
    DISABLED,
    DESTROYED,
    PANEL_BORDER,
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
