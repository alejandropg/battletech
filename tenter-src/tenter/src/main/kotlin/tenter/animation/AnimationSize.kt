package tenter.animation

/** The intrinsic terminal-cell dimensions of an animation frame. */
public data class AnimationSize(
    public val width: Int,
    public val height: Int,
) {
    init {
        require(width > 0) { "width must be positive, was $width" }
        require(height > 0) { "height must be positive, was $height" }
    }
}
