package tenter.view

import tenter.screen.Canvas
import tenter.screen.RevealRect

/** A 2D scroll offset, in characters. */
public data class ScrollOffset(
    public val x: Int = 0,
    public val y: Int = 0,
) {
    public operator fun plus(other: ScrollOffset): ScrollOffset = ScrollOffset(x + other.x, y + other.y)

    public companion object {
        public val ZERO: ScrollOffset = ScrollOffset()
    }
}

/**
 * An immutable observation of a completed viewport frame. [revealed] is in content coordinates;
 * dimensions are in cells and describe the exact prepared content and destination used for the
 * frame.
 */
public data class ScrollState(
    public val offset: ScrollOffset,
    public val maxOffset: ScrollOffset,
    public val revealed: RevealRect? = null,
    public val viewportWidth: Int = 0,
    public val viewportHeight: Int = 0,
    public val contentWidth: Int = 0,
    public val contentHeight: Int = 0,
) {
    public companion object {
        public val NONE: ScrollState = ScrollState(ScrollOffset.ZERO, ScrollOffset.ZERO)
    }
}

/**
 * Mutable scrolling intent and the last immutable frame observation for one viewport. Commands
 * are queued until a drawable frame; they never rewrite [settled]. A state belongs to one
 * viewport and is confined to the application's rendering context.
 */
public class ViewportState(initialOffset: ScrollOffset = ScrollOffset.ZERO) {
    private var requestedOffset: ScrollOffset = initialOffset
    private var pendingRecenter: Boolean = false
    private var previousReveal: RevealRect? = null
    private var previousViewportHeight: Int? = null
    private var previousViewportWidth: Int? = null
    private var settledSnapshot: ScrollState? = null

    /** The last completed drawable frame, or `null` before the first such frame. */
    public val settled: ScrollState? get() = settledSnapshot

    /** Stages a managed frame without consuming the owner's queued intent. */
    internal fun fork(): ViewportState = ViewportState().also { it.adopt(this) }

    internal fun adopt(candidate: ViewportState) {
        requestedOffset = candidate.requestedOffset
        pendingRecenter = candidate.pendingRecenter
        previousReveal = candidate.previousReveal
        previousViewportHeight = candidate.previousViewportHeight
        previousViewportWidth = candidate.previousViewportWidth
        settledSnapshot = candidate.settledSnapshot
    }

    /** Queues a relative scroll request for the next drawable frame. */
    public fun scrollBy(dx: Int, dy: Int) {
        requestedOffset = ScrollOffset(
            saturatingAdd(requestedOffset.x, dx),
            saturatingAdd(requestedOffset.y, dy),
        )
    }

    /** Queues a one-shot request to center the current reveal target on the next frame. */
    public fun requestRecenter() {
        pendingRecenter = true
    }

    internal fun requestedOffset(): ScrollOffset = requestedOffset

    internal fun shouldFollow(reveal: RevealRect?, viewportWidth: Int, viewportHeight: Int): Boolean = reveal != null && (
        reveal != previousReveal ||
            previousViewportHeight == null ||
            previousViewportHeight != viewportHeight || previousViewportWidth != viewportWidth
        )

    internal fun recenterRequested(): Boolean = pendingRecenter

    internal fun settle(snapshot: ScrollState) {
        requestedOffset = snapshot.offset
        previousReveal = snapshot.revealed
        previousViewportHeight = snapshot.viewportHeight
        previousViewportWidth = snapshot.viewportWidth
        pendingRecenter = false
        settledSnapshot = snapshot
    }

    private fun saturatingAdd(left: Int, right: Int): Int =
        left.toLong().plus(right.toLong()).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/** Scrolls prepared content in an exactly sized offscreen stream. */
public class Viewport(
    private val content: ContentView,
    private val state: ViewportState = ViewportState(),
) : View {

    internal val settled: ScrollState? get() = state.settled

    /** The completed frame observation, or [ScrollState.NONE] before a drawable frame. */
    public val scroll: ScrollState
        get() = state.settled ?: ScrollState.NONE

    override fun draw(canvas: Canvas) {
        drawPrepared(canvas, state)
    }

    private fun drawPrepared(canvas: Canvas, viewportState: ViewportState) {
        if (canvas.width <= 0 || canvas.height <= 0) return

        val layout = content.layout(canvas.width)
        val stream = Canvas.offscreen(layout.width, layout.height)
        layout.draw(stream)

        val maxOffsetX = (layout.width - canvas.width).coerceAtLeast(0)
        val maxOffsetY = (layout.height - canvas.height).coerceAtLeast(0)
        val reveal = stream.revealRect()
        val follow = viewportState.shouldFollow(reveal, canvas.width, canvas.height)
        val base = viewportState.requestedOffset()
        val recenter = viewportState.recenterRequested()
        val offsetX = resolveAxis(
            base = base.x,
            revealRange = reveal?.let { it.x to it.x + it.width },
            viewportSize = canvas.width,
            maxOffset = maxOffsetX,
            shouldFollow = follow,
            recenter = recenter,
        )
        val offsetY = resolveAxis(
            base = base.y,
            revealRange = reveal?.let { it.y to it.y + it.height },
            viewportSize = canvas.height,
            maxOffset = maxOffsetY,
            shouldFollow = follow,
            recenter = recenter,
        )

        canvas.blit(stream, offsetX, offsetY, 0, 0, canvas.width, canvas.height)
        viewportState.settle(
            ScrollState(
                offset = ScrollOffset(offsetX, offsetY),
                maxOffset = ScrollOffset(maxOffsetX, maxOffsetY),
                revealed = reveal,
                viewportWidth = canvas.width,
                viewportHeight = canvas.height,
                contentWidth = layout.width,
                contentHeight = layout.height,
            ),
        )
    }

    private fun resolveAxis(
        base: Int,
        revealRange: Pair<Int, Int>?,
        viewportSize: Int,
        maxOffset: Int,
        shouldFollow: Boolean,
        recenter: Boolean,
    ): Int = when {
        revealRange == null -> base.coerceIn(0, maxOffset)
        recenter -> ScrollGeometry.center(revealRange.first, revealRange.second, viewportSize, maxOffset)
        shouldFollow -> ScrollGeometry.follow(base, revealRange.first, revealRange.second, viewportSize, maxOffset)
        else -> base.coerceIn(0, maxOffset)
    }
}
