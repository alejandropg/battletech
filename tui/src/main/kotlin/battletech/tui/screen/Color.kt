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
    ORANGE,
    MAGENTA,
    GRAY;

    public companion object {
        /** Uncommitted / preview content the player has not committed yet. */
        public val DRAFT: Color = GRAY

        /** Disabled or unavailable elements (e.g. unusable weapons). */
        public val DISABLED: Color = GRAY
    }
}
