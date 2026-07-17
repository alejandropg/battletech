package battletech.tui.screen

public data class Cell(
    val char: String = " ",
    val style: Style = Style.DEFAULT,
) {
    public data class Style(
        val fg: Color = Color.DEFAULT,
        val bg: Color = Color.DEFAULT,
        val strikethrough: Boolean = false,
    ) {
        internal companion object {
            /** The all-defaults style; reused everywhere an unstyled cell is needed. */
            internal val DEFAULT: Style = Style()
        }
    }
}
