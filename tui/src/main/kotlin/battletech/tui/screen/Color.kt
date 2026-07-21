package battletech.tui.screen

public enum class Color {
    DEFAULT,
    BLACK,
    RED,
    GREEN,
    BLUE,
    YELLOW,
    CYAN,
    WHITE,
    DARK_GREEN,
    BROWN,
    BRIGHT_YELLOW,
    BRIGHT_GREEN,
    ORANGE,
    MAGENTA,
    LIGHT_BLUE,
    GRAY,

    // Soft terrain background tints (truecolor). Used only as backgrounds — dark and
    // desaturated so units and icons keep the spotlight. See TextStyleFactory for RGB values.
    WOODS_LIGHT_BG,
    WOODS_HEAVY_BG,
    WATER_SHALLOW_BG,
    WATER_DEEP_BG,
    ELEVATION_LOW_BG,
    ELEVATION_HIGH_BG;

    internal companion object {
        /** Uncommitted / preview content the player has not committed yet. */
        internal val DRAFT: Color = GRAY

        /** Disabled or unavailable elements (e.g. unusable weapons). */
        internal val DISABLED: Color = GRAY
    }
}
