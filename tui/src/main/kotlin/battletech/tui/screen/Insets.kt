package battletech.tui.screen

/** Space removed from each edge of a [Canvas] to derive a smaller region. */
public data class Insets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    public operator fun plus(other: Insets): Insets = Insets(
        left + other.left,
        top + other.top,
        right + other.right,
        bottom + other.bottom,
    )

    /** These insets with the vertical edges dropped. */
    public fun horizontal(): Insets = Insets(left = left, right = right)

    /** These insets with the horizontal edges dropped. */
    public fun vertical(): Insets = Insets(top = top, bottom = bottom)

    public companion object {
        public val NONE: Insets = Insets()
        public fun all(n: Int): Insets = Insets(n, n, n, n)
    }
}
