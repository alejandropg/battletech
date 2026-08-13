package tenter.screen

public data class Cell(
    val char: String = " ",
    val style: Style = Style.DEFAULT,
) {
    public companion object {
        /** The default, unstyled space cell — reused wherever a blank cell is needed so a fresh
         * screen doesn't allocate a distinct identical instance per cell. */
        public val EMPTY: Cell = Cell()
    }

    public data class Style(
        val fg: ColorRole = UiRole.DEFAULT,
        val bg: ColorRole = UiRole.DEFAULT,
        val strikethrough: Boolean = false,
    ) {
        public companion object {
            /** The all-defaults style; reused everywhere an unstyled cell is needed. */
            public val DEFAULT: Style = Style()
        }
    }
}
