package tenter.animation

import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import tenter.view.View

/**
 * Samples a finite collection of animations against elapsed monotonic time.
 *
 * Playback owns the timeline only. It does not own a clock, a renderer, or the application data
 * carried by each [Clip]. Sampling is pure: a caller may sample the same elapsed time repeatedly,
 * or sample times out of order. The input list and each animation's metadata are copied at
 * construction, so later mutation cannot change the timeline or its original ordering. A clip is
 * visible exactly while `startAfter <= elapsed < startAfter + frameDuration * frameCount`; its
 * frame zero is visible at the exact start and it is absent at the exact end. Construction and
 * sampling reject invalid or non-finite timeline values with [IllegalArgumentException].
 */
public class AnimationPlayback<T>(clips: List<Clip<T>>) {
    private val timelines: List<Timeline<T>> = clips.map(::Timeline)

    /** An animation together with the opaque value presented alongside each visible frame. */
    public data class Clip<T>(
        public val animation: Animation,
        public val value: T,
        public val startAfter: Duration = ZERO,
    )

    /** A visible animation frame and its application-owned value. */
    public data class Frame<T>(
        public val value: T,
        public val size: AnimationSize,
        public val content: View,
    )

    /**
     * The visible frames at a sampled time and the next positive timeline transition.
     *
     * A non-null [nextChangeIn] is the shortest positive delay until a pending clip starts, a
     * visible clip changes frame, or a visible clip ends. It is null only when every clip has
     * finished, including an empty playback. Therefore an empty [frames] list with a non-null
     * delay represents a gap before a future clip starts.
     */
    public data class Sample<T>(
        public val frames: List<Frame<T>>,
        public val nextChangeIn: Duration?,
    )

    /**
     * Samples all clips at [elapsed], omitting clips that have not started or have finished.
     * At an exact start this returns frame zero; at an exact end the clip is omitted.
     */
    public fun sample(elapsed: Duration): Sample<T> {
        require(elapsed.isFinite() && elapsed >= ZERO) {
            "elapsed must be finite and non-negative, was $elapsed"
        }

        val frames = ArrayList<Frame<T>>()
        var nextChange: Duration? = null

        timelines.forEach { timeline ->
            when {
                elapsed < timeline.startAfter -> {
                    nextChange = minPositive(nextChange, timeline.startAfter - elapsed)
                }

                elapsed < timeline.endAt -> {
                    val frameIndex = timeline.frameIndexAt(elapsed)
                    frames += Frame(
                        value = timeline.value,
                        size = timeline.size,
                        content = timeline.animation.frame(frameIndex),
                    )
                    val transition = if (frameIndex + 1 < timeline.frameCount) {
                        timeline.startAfter + timeline.frameDuration * (frameIndex + 1)
                    } else {
                        timeline.endAt
                    }
                    nextChange = minPositive(nextChange, transition - elapsed)
                }
            }
        }

        return Sample(frames = frames.toList(), nextChangeIn = nextChange)
    }

    private class Timeline<T>(clip: Clip<T>) {
        internal val animation: Animation = clip.animation
        internal val value: T = clip.value
        internal val size: AnimationSize = animation.size
        internal val frameCount: Int = animation.frameCount
        internal val frameDuration: Duration = animation.frameDuration
        internal val startAfter: Duration = clip.startAfter
        internal val endAt: Duration

        init {
            require(frameCount > 0) { "frameCount must be positive, was $frameCount" }
            require(frameDuration.isFinite() && frameDuration > ZERO) {
                "frameDuration must be finite and positive, was $frameDuration"
            }
            require(startAfter.isFinite() && startAfter >= ZERO) {
                "startAfter must be finite and non-negative, was $startAfter"
            }
            val totalDuration = frameDuration * frameCount
            require(totalDuration.isFinite()) {
                "animation duration overflowed to infinity"
            }
            endAt = startAfter + totalDuration
            require(endAt.isFinite()) {
                "animation end offset overflowed to infinity"
            }
        }

        internal fun frameIndexAt(elapsed: Duration): Int {
            var low = 0
            var high = frameCount - 1
            var result = 0

            while (low <= high) {
                val midpoint = low + (high - low) / 2
                val frameStart = startAfter + frameDuration * midpoint
                if (frameStart <= elapsed) {
                    result = midpoint
                    low = midpoint + 1
                } else {
                    high = midpoint - 1
                }
            }
            return result
        }
    }

    private companion object {
        private fun minPositive(current: Duration?, candidate: Duration): Duration =
            if (current == null || candidate < current) candidate else current
    }
}
