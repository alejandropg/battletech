package io.archinaut.tenter.screen

/** Space removed from each edge of a [Canvas] to derive a smaller region. */
public data class Insets(
    public val left: Int = 0,
    public val top: Int = 0,
    public val right: Int = 0,
    public val bottom: Int = 0,
) {
    init {
        require(left >= 0) { "left inset must not be negative: $left" }
        require(top >= 0) { "top inset must not be negative: $top" }
        require(right >= 0) { "right inset must not be negative: $right" }
        require(bottom >= 0) { "bottom inset must not be negative: $bottom" }
    }

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
