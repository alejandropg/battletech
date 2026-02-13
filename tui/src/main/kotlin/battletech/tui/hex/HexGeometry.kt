package battletech.tui.hex

public object HexGeometry {
    public const val HEX_WIDTH: Int = 7
    public const val HEX_HEIGHT: Int = 4
    public const val COL_STRIDE: Int = 8
    public const val ROW_STRIDE: Int = 4
    public const val ODD_COL_ROW_OFFSET: Int = 2

    public val HEX_TOP: String = " _____ "
    public val HEX_UPPER_LEFT: String = "/     \\"
    public val HEX_LOWER_LEFT: String = "\\     /"
    public val HEX_BOTTOM: String = " \\___/ "
}
