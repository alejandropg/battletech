package io.archinaut.tenter.screen

/** An immutable, nonnegative cell rectangle. Coordinates are local to its containing canvas. */
public data class Rect(
    public val x: Int,
    public val y: Int,
    public val width: Int,
    public val height: Int,
) {
    init {
        require(width >= 0) { "rectangle width must not be negative: $width" }
        require(height >= 0) { "rectangle height must not be negative: $height" }
    }

    /** True when ([x], [y]) is inside this rectangle's positive-area cells. */
    public fun contains(x: Int, y: Int): Boolean =
        x.toLong() >= this.x &&
            y.toLong() >= this.y &&
            x.toLong() < this.x.toLong() + width &&
            y.toLong() < this.y.toLong() + height

    /** Removes [insets], clamping each edge so the result remains a valid rectangle. */
    public fun inset(insets: Insets): Rect {
        val left = insets.left.coerceAtMost(width)
        val top = insets.top.coerceAtMost(height)
        val right = insets.right.coerceAtMost(width - left)
        val bottom = insets.bottom.coerceAtMost(height - top)
        return Rect(
            x = x + left,
            y = y + top,
            width = width - left - right,
            height = height - top - bottom,
        )
    }
}
