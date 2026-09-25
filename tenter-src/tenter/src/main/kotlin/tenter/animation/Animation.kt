package tenter.animation

import tenter.view.View
import kotlin.time.Duration

/**
 * A finite sequence of equally timed, deterministic terminal views.
 *
 * Implementations expose stable metadata: [frameCount] is positive and [frameDuration] is finite
 * and positive after construction. [frame] is non-advancing and deterministic for an index in
 * `0 until [frameCount]`; it draws in local coordinates within [size], so a smaller destination
 * clips naturally through [tenter.screen.Canvas]. A frame returned from [frame] must remain stable
 * after later frame requests.
 */
public interface Animation {
    public val size: AnimationSize
    public val frameCount: Int
    public val frameDuration: Duration

    /** Returns the content for [index], where [index] is in `0 until [frameCount]`. */
    public fun frame(index: Int): View
}
