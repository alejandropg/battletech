package tenter.view

import tenter.screen.Canvas

/**
 * Packs prepared children left-to-right, wrapping at [availableWidth]. Each [Child.width] is the
 * declared column width; band height comes from the children's logical layout heights. A child
 * wider than the available space retains that declared width and is clipped only when painted.
 * [draw] remains the legacy raw-view adapter until application callers migrate to [ContentView].
 */
public class Columns(
    private val children: List<Child>,
    private val gutter: Int = 2,
) : ContentView {

    init {
        require(gutter >= 0) { "gutter must not be negative: $gutter" }
        children.forEach { require(it.width >= 0) { "column width must not be negative: ${it.width}" } }
    }

    public data class Child(
        public val width: Int,
        public val view: View,
    )

    override fun layout(availableWidth: Int): ContentLayout {
        require(availableWidth >= 0) { "available width must not be negative: $availableWidth" }
        val prepared = children.map { child ->
            child to ((child.view as? ContentView)?.layout(child.width)
                ?: legacyContentLayout(child.width, child.view))
        }

        var x = 0
        var bandTop = 0
        var bandHeight = 0
        var bandWidth = 0
        var totalWidth = 0
        val placements = mutableListOf<Placement>()

        for ((child, layout) in prepared) {
            val gutterBefore = if (x == 0) 0 else gutter
            if (x != 0 && x.toLong() + gutterBefore + child.width > availableWidth.toLong()) {
                bandTop = checkedAdd(checkedAdd(bandTop, bandHeight, "columns height"), 1, "columns height")
                totalWidth = maxOf(totalWidth, bandWidth)
                x = 0
                bandHeight = 0
                bandWidth = 0
            }
            val childX = if (x == 0) 0 else checkedAdd(x, gutter, "columns placement")
            placements += Placement(childX, bandTop, layout)
            x = checkedAdd(childX, child.width, "columns placement")
            bandWidth = maxOf(bandWidth, x)
            bandHeight = maxOf(bandHeight, layout.height)
        }
        if (prepared.isNotEmpty()) {
            totalWidth = maxOf(totalWidth, bandWidth)
            bandTop = checkedAdd(bandTop, bandHeight, "columns height")
        }

        return contentLayout(totalWidth, bandTop) {
            placements.forEach { place(it.x, it.y, it.layout) }
        }
    }

    override fun draw(canvas: Canvas) {
        var x = 0
        var bandTop = 0
        var bandHeight = 0

        for (child in children) {
            val gutterBefore = if (x == 0) 0 else gutter
            if (x != 0 && x + gutterBefore + child.width > canvas.width) {
                bandTop += bandHeight + 1
                bandHeight = 0
                x = 0
            }
            val childX = if (x == 0) 0 else x + gutter

            val stream = Canvas.offscreen(child.width, canvas.height)
            child.view.draw(stream)
            val height = stream.contentHeight()
            canvas.blit(stream, 0, 0, childX, bandTop, child.width, height)

            bandHeight = maxOf(bandHeight, height)
            x = childX + child.width
        }
    }

    private data class Placement(val x: Int, val y: Int, val layout: ContentLayout)

    private fun checkedAdd(left: Int, right: Int, description: String): Int =
        try {
            Math.addExact(left, right)
        } catch (_: ArithmeticException) {
            error("$description overflowed")
        }
}
