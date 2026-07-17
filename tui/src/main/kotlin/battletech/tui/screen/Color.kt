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
    GRAY;

    internal companion object {
        /** Uncommitted / preview content the player has not committed yet. */
        internal val DRAFT: Color = GRAY

        /** Disabled or unavailable elements (e.g. unusable weapons). */
        internal val DISABLED: Color = GRAY
    }
}
