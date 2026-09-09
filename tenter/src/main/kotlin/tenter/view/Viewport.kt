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
    private var previousViewportWidth: Int? = null
    private var previousViewportHeight: Int? = null
    private var settledSnapshot: ScrollState? = null

    /** The last completed drawable frame, or `null` before the first such frame. */
    public val settled: ScrollState? get() = settledSnapshot

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
            previousViewportWidth == null ||
            previousViewportHeight == null ||
            previousViewportWidth != viewportWidth ||
            previousViewportHeight != viewportHeight
        )

    internal fun recenterRequested(): Boolean = pendingRecenter

    internal fun settle(snapshot: ScrollState) {
        requestedOffset = snapshot.offset
        previousReveal = snapshot.revealed
        previousViewportWidth = snapshot.viewportWidth
        previousViewportHeight = snapshot.viewportHeight
        pendingRecenter = false
        settledSnapshot = snapshot
    }

    private fun saturatingAdd(left: Int, right: Int): Int =
        left.toLong().plus(right.toLong()).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
}

/**
 * How much legacy [View] content a [Viewport] must accommodate. This temporary path remains for
 * existing consumers until stage 05; new code should pass [ContentView] and [ViewportState].
 */
public sealed interface ContentExtent {
    /** Content is exactly viewport-wide; height is discovered up to [maxHeight]. */
    public data class Measured(public val maxHeight: Int = 512) : ContentExtent {
        init {
            require(maxHeight >= 0) { "maximum measured height must not be negative: $maxHeight" }
        }
    }

    /** Content's size is already known — no render-then-measure pass. */
    public data class Fixed(
        public val width: Int,
        public val height: Int,
    ) : ContentExtent {
        init {
            require(width >= 0) { "content width must not be negative: $width" }
            require(height >= 0) { "content height must not be negative: $height" }
        }
    }
}

/**
 * Scrolls prepared content in an exactly sized offscreen stream. The legacy constructor remains
 * as an explicit compatibility adapter; it retains the old measure-then-paint behavior until
 * application migration in stage 05.
 */
public class Viewport private constructor(
    private val content: View,
    private val prepared: Boolean,
    private val legacyExtent: ContentExtent?,
    private val state: ViewportState?,
) : View {

    /** Prepares [content] at each frame's actual viewport width and owns scrolling through [state]. */
    public constructor(content: ContentView, state: ViewportState = ViewportState()) : this(
        content = content,
        prepared = true,
        legacyExtent = null,
        state = state,
    )

    /** Temporary legacy adapter; use the prepared constructor for new consumers. */
    public constructor(
        content: View,
        extent: ContentExtent,
        offset: ScrollOffset = ScrollOffset.ZERO,
        previousReveal: RevealRect? = null,
        recenter: Boolean = false,
    ) : this(
        content = content,
        prepared = false,
        legacyExtent = extent,
        state = null,
    ) {
        legacyOffset = offset
        legacyPreviousReveal = previousReveal
        legacyRecenter = recenter
    }

    private var legacyScroll: ScrollState = ScrollState.NONE
    private var legacyOffset: ScrollOffset = ScrollOffset.ZERO
    private var legacyPreviousReveal: RevealRect? = null
    private var legacyRecenter: Boolean = false

    /** The completed frame observation, with [ScrollState.NONE] retained for the legacy surface. */
    public val scroll: ScrollState
        get() = if (prepared) state?.settled ?: ScrollState.NONE else legacyScroll

    override fun draw(canvas: Canvas) {
        if (prepared) {
            drawPrepared(canvas, state ?: error("prepared viewport has no state"))
        } else {
            drawLegacy(canvas)
        }
    }

    private fun drawPrepared(canvas: Canvas, viewportState: ViewportState) {
        if (canvas.width <= 0 || canvas.height <= 0) return

        val layout = (content as ContentView).layout(canvas.width)
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

    private fun drawLegacy(canvas: Canvas) {
        if (canvas.width <= 0 || canvas.height <= 0) {
            legacyScroll = ScrollState.NONE
            return
        }

        val extent = legacyExtent ?: error("legacy viewport has no content extent")
        val streamWidth = when (extent) {
            is ContentExtent.Measured -> canvas.width
            is ContentExtent.Fixed -> extent.width
        }
        val allocatedHeight = when (extent) {
            is ContentExtent.Measured -> extent.maxHeight
            is ContentExtent.Fixed -> extent.height
        }

        val stream = Canvas.offscreen(streamWidth, allocatedHeight)
        content.draw(stream)
        val streamHeight = when (extent) {
            is ContentExtent.Measured -> stream.contentHeight()
            is ContentExtent.Fixed -> allocatedHeight
        }
        val maxOffsetX = (streamWidth - canvas.width).coerceAtLeast(0)
        val maxOffsetY = (streamHeight - canvas.height).coerceAtLeast(0)
        val reveal = stream.revealRect()
        val shouldFollow = reveal != null && reveal != legacyPreviousReveal
        val offsetX = resolveAxis(legacyOffset.x, reveal?.let { it.x to it.x + it.width }, canvas.width, maxOffsetX, shouldFollow, legacyRecenter)
        val offsetY = resolveAxis(legacyOffset.y, reveal?.let { it.y to it.y + it.height }, canvas.height, maxOffsetY, shouldFollow, legacyRecenter)
        canvas.blit(stream, offsetX, offsetY, 0, 0, canvas.width, canvas.height)
        legacyScroll = ScrollState(ScrollOffset(offsetX, offsetY), ScrollOffset(maxOffsetX, maxOffsetY), reveal)
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
