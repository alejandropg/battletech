package tenter.panel

import java.util.Collections
import tenter.screen.Rect
import tenter.view.Bordered
import tenter.view.ScrollState

/**
 * The content rect and every placed panel's immutable geometry from one completed frame.
 * Coordinates are local to the canvas supplied to [PanelSet.render].
 */
public class PanelLayout<K : PanelId> private constructor(
    /** The whole region below the reserved top rows — where a maximized panel goes. */
    public val content: Rect,
    /** The main panel's slot, or null for a uniform layout. */
    public val main: Slot<K>?,
    /** Uniform-layout slots in declaration order. */
    sides: List<Slot<K>>,
) {
    public val sides: List<Slot<K>> = Collections.unmodifiableList(ArrayList(sides))

    /** The whole region below the reserved top rows. */
    public val contentX: Int get() = content.x
    public val contentY: Int get() = content.y
    public val contentWidth: Int get() = content.width
    public val contentHeight: Int get() = content.height

    /** One panel's identity and settled placement from this frame. */
    public data class Slot<K : PanelId>(
        public val id: K,
        /** The complete panel rectangle, including its border. */
        public val outer: Rect,
        /** The rectangle painted by the scrolling viewport inside the border and gutters. */
        public val content: Rect,
        /** The settled viewport observation, or null when this slot had no drawable frame. */
        public val scroll: ScrollState?,
    ) {
        public val x: Int get() = outer.x
        public val y: Int get() = outer.y
        public val width: Int get() = outer.width
        public val height: Int get() = outer.height
    }

    /** The panel whose painted outer rectangle contains ([x], [y]), or null. */
    public fun panelAt(x: Int, y: Int): Slot<K>? = buildList {
        main?.let(::add)
        addAll(sides)
    }.firstOrNull { it.outer.contains(x, y) }

    internal fun withSettledScroll(scrollOf: (K) -> ScrollState?): PanelLayout<K> = PanelLayout(
        content = content,
        main = main?.withScroll(scrollOf(main.id)),
        sides = sides.map { it.withScroll(scrollOf(it.id)) },
    )

    private fun Slot<K>.withScroll(scroll: ScrollState?): Slot<K> = copy(scroll = scroll)

    public companion object {
        /**
         * Lays out [sides] and [main] over a [width]x[height] canvas, reserving [reservedTop]
         * rows above the content area. Side widths are reserved first; if they do not fit, the
         * main slot becomes zero-width and sides are clipped in declaration order.
         */
        internal fun <K : PanelId, I> compute(
            width: Int,
            height: Int,
            reservedTop: Int,
            main: Panel<K, I>,
            sides: List<Panel<K, I>>,
            widthOf: (Panel<K, I>) -> Int,
        ): PanelLayout<K> {
            validateCanvas(width, height, reservedTop)
            val content = contentRect(width, height, reservedTop)
            sides.firstOrNull { it.state == PanelState.MAXIMIZED }?.let { maximized ->
                return PanelLayout(
                    content = content,
                    main = null,
                    sides = listOf(slot(maximized.id, content.x, content.y, content.width, content.height, width)),
                )
            }
            val sideWidths = sides.map { panel -> checkedWidth(widthOf(panel), panel.id) }
            val totalSideWidth = sides.indices.sumOf { sideWidths[it].toLong() }
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            val mainWidth = (width - totalSideWidth).coerceAtLeast(0)
            val slots = buildList {
                var nextX = if (totalSideWidth <= width) mainWidth else 0
                for ((index, panel) in sides.withIndex()) {
                    val available = (width - nextX).coerceAtLeast(0)
                    val slotWidth = sideWidths[index].coerceAtMost(available)
                    add(slot(panel.id, nextX, content.y, slotWidth, content.height, width))
                    nextX += slotWidth
                }
            }
            return PanelLayout(
                content = content,
                main = slot(main.id, 0, content.y, mainWidth, content.height, width),
                sides = slots,
            )
        }

        /**
         * Lays [panels] out as proportional columns with an optional trailing fixed group.
         * Minimized panels are fixed-width, but remain at their declaration positions among the
         * proportional panels. Other fixed panels form a trailing group in declaration order.
         */
        internal fun <K : PanelId, I> computeUniform(
            width: Int,
            height: Int,
            reservedTop: Int,
            panels: List<Panel<K, I>>,
            columnCount: Int = panels.size,
            fixedWidthPanels: Set<K> = emptySet(),
            widthOf: (Panel<K, I>) -> Int,
        ): PanelLayout<K> {
            validateCanvas(width, height, reservedTop)
            require(columnCount >= 0) { "uniform column count must not be negative: $columnCount" }
            val content = contentRect(width, height, reservedTop)
            if (panels.isEmpty()) return PanelLayout(content, null, emptyList())

            panels.firstOrNull { it.state == PanelState.MAXIMIZED }?.let { maximized ->
                return PanelLayout(
                    content = content,
                    main = null,
                    sides = listOf(slot(maximized.id, content.x, content.y, content.width, content.height, width)),
                )
            }

            val fixed = panels.filter { it.id in fixedWidthPanels || it.state == PanelState.MINIMIZED }
            val proportional = panels.filter { it !in fixed }
            require(columnCount >= proportional.size || proportional.isEmpty()) {
                "A uniform layout needs at least one column per proportional panel"
            }
            val widths = fixed.associate { it.id to checkedWidth(widthOf(it), it.id) }
            val fixedWidth = fixed.sumOf { widths.getValue(it.id).toLong() }
            val proportionalWidth = (width.toLong() - fixedWidth)
                .coerceAtLeast(0)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            val columnWidths = proportionalWidths(proportionalWidth, columnCount, proportional.size)
            val minimizedWidth = fixed
                .filter { it.state == PanelState.MINIMIZED }
                .sumOf { widths.getValue(it.id).toLong() }
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()

            val slots = buildList {
                var proportionalIndex = 0
                var x = 0
                for (panel in panels) {
                    if (panel in proportional) {
                        val panelWidth = columnWidths[proportionalIndex]
                        add(slot(panel.id, x, content.y, panelWidth, content.height, width))
                        x += panelWidth
                        proportionalIndex++
                    } else if (panel.state == PanelState.MINIMIZED) {
                        val panelWidth = widths.getValue(panel.id).coerceAtMost((width - x).coerceAtLeast(0))
                        add(slot(panel.id, x, content.y, panelWidth, content.height, width))
                        x += panelWidth
                    }
                }
                x = proportionalWidth + minimizedWidth
                for (panel in fixed) {
                    if (panel.state != PanelState.MINIMIZED) {
                        val panelWidth = widths.getValue(panel.id).coerceAtMost((width - x).coerceAtLeast(0))
                        add(slot(panel.id, x, content.y, panelWidth, content.height, width))
                        x += panelWidth
                    }
                }
            }
            return PanelLayout(content, null, slots)
        }

        private fun <K : PanelId> slot(
            id: K,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            canvasWidth: Int,
        ): Slot<K> {
            val outer = Rect(x.coerceIn(0, canvasWidth), y, width, height)
            return Slot(
                id = id,
                outer = outer,
                content = outer.inset(Bordered.VIEWPORT_INSET),
                scroll = null,
            )
        }

        private fun proportionalWidths(width: Int, columnCount: Int, panelCount: Int): List<Int> {
            if (panelCount == 0) return emptyList()
            val base = if (columnCount == 0) 0 else width / columnCount
            val remainder = if (columnCount == 0) 0 else width % columnCount
            return List(panelCount) { index -> base + if (index < remainder) 1 else 0 }
        }

        private fun contentRect(width: Int, height: Int, reservedTop: Int): Rect = Rect(
            x = 0,
            y = reservedTop.coerceAtMost(height),
            width = width,
            height = height - reservedTop.coerceAtMost(height),
        )

        private fun validateCanvas(width: Int, height: Int, reservedTop: Int) {
            require(width >= 0) { "canvas width must not be negative: $width" }
            require(height >= 0) { "canvas height must not be negative: $height" }
            require(reservedTop >= 0) { "reserved top rows must not be negative: $reservedTop" }
        }

        private fun checkedWidth(width: Int, id: PanelId): Int {
            require(width >= 0) { "Panel $id width must not be negative: $width" }
            return width
        }
    }
}
