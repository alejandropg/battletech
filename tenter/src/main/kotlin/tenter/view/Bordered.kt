package tenter.view

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.ColorRole
import tenter.screen.Insets
import tenter.text.CellWidth

/**
 * Decorates [content] with a box border and an optional title/badge in the top border. Scrollbar
 * thumbs are a scrolling-panel concern and are painted by [ScrollingPanel] from its settled
 * [ScrollState], so a reusable border has no scrolling knowledge.
 * [gutters] is extra inset consumed between the border and [content], beyond the 1-cell border
 * itself (e.g. the horizontal breathing room a scrolling panel's viewport wants); pass
 * [Insets.NONE] for a border with nothing but the border between it and content.
 *
 * The single place every framed view gets its box from — no view should draw its own border.
 */
public class Bordered(
    private val content: View,
    private val title: String = "",
    private val badge: String? = null,
    private val gutters: Insets = Insets.NONE,
    private val borderColor: ColorRole = ChromeRole.PANEL_BORDER,
    private val titleColor: ColorRole = ChromeRole.ACCENT,
) : View {

    init {
        require(gutters.left >= 0 && gutters.top >= 0 && gutters.right >= 0 && gutters.bottom >= 0) {
            "border gutters must not be negative: $gutters"
        }
    }

    public override fun draw(canvas: Canvas) {
        drawBorder(canvas)
        content.draw(canvas.inset(BORDER + gutters))
    }

    /** No-op below 2x2. */
    private fun drawBorder(canvas: Canvas) {
        val width = canvas.width
        val height = canvas.height
        if (width < 2 || height < 2) return

        canvas.set(0, 0, Cell("╭", Cell.Style(borderColor)))
        canvas.set(width - 1, 0, Cell("╮", Cell.Style(borderColor)))
        canvas.set(0, height - 1, Cell("╰", Cell.Style(borderColor)))
        canvas.set(width - 1, height - 1, Cell("╯", Cell.Style(borderColor)))

        for (i in 1 until width - 1) {
            canvas.set(i, 0, Cell("─", Cell.Style(borderColor)))
            canvas.set(i, height - 1, Cell("─", Cell.Style(borderColor)))
        }

        for (i in 1 until height - 1) {
            canvas.set(0, i, Cell("│", Cell.Style(borderColor)))
            canvas.set(width - 1, i, Cell("│", Cell.Style(borderColor)))
        }

        val titleWidth = CellWidth.of(title)
        val badgeWidth = badge?.let(CellWidth::of) ?: 0
        val badgeRunWidth = badge?.let { CellWidth.of("[$it]") } ?: 0
        if (title.isNotEmpty()) {
            if (badge != null && width > titleWidth + badgeWidth + 7) {
                canvas.writeString(2, 0, "[$badge] $title", Cell.Style(titleColor))
                canvas.set(5 + badgeWidth + titleWidth, 0, Cell(style = Cell.Style(borderColor)))
            } else if (badge == null && width > titleWidth + 6) {
                canvas.set(3, 0, Cell(style = Cell.Style(borderColor)))
                canvas.writeString(4, 0, title, Cell.Style(titleColor))
                canvas.set(4 + titleWidth, 0, Cell(style = Cell.Style(borderColor)))
            } else if (badge != null && width > 2 + badgeRunWidth) {
                // The full "[badge] title" run doesn't fit — e.g. a minimized stub too narrow for
                // its full title. The badge alone is still worth showing.
                canvas.writeString(2, 0, "[$badge]", Cell.Style(titleColor))
            }
        } else if (badge != null && width > 2 + badgeRunWidth) {
            // No title to anchor a "[badge] title" run to. The badge alone is still worth showing.
            canvas.writeString(2, 0, "[$badge]", Cell.Style(titleColor))
        }
    }

    /**
     * Draws a scrollbar thumb on the right border ([ScrollState.maxOffset]`.y > 0`) and/or bottom
     * border (`.x > 0`), at the ranges [ScrollGeometry.thumb] computes from [scroll]'s settled
     * values and this box's own viewport size — the same region [content] was just rendered into.
     */
    internal fun drawThumbs(canvas: Canvas, scroll: ScrollState) {
        val inset = BORDER + gutters
        val viewportWidth = canvas.width - inset.left - inset.right
        val viewportHeight = canvas.height - inset.top - inset.bottom

        val thumbStyle = Cell.Style(borderColor)

        ScrollGeometry.thumb(
            track = viewportHeight,
            contentLength = scroll.maxOffset.y + viewportHeight,
            viewportLength = viewportHeight,
            offset = scroll.offset.y,
        )?.forEach { i -> canvas.set(canvas.width - 1, inset.top + i, Cell("▐", thumbStyle)) }

        ScrollGeometry.thumb(
            track = viewportWidth,
            contentLength = scroll.maxOffset.x + viewportWidth,
            viewportLength = viewportWidth,
            offset = scroll.offset.x,
        )?.forEach { i -> canvas.set(inset.left + i, canvas.height - 1, Cell("▬", thumbStyle)) }
    }

    public companion object {
        /** Composes an intrinsic border around prepared content; raw constructors paint allocated canvases. */
        public fun prepared(
            content: ContentView,
            title: String = "",
            badge: String? = null,
            gutters: Insets = Insets.NONE,
            borderColor: ColorRole = ChromeRole.PANEL_BORDER,
            titleColor: ColorRole = ChromeRole.ACCENT,
        ): ContentView = object : ContentView {
            public override fun layout(availableWidth: Int): ContentLayout {
                require(availableWidth >= 0) { "available width must not be negative: $availableWidth" }
                val outer = BORDER + gutters
                val innerWidth = (availableWidth.toLong() - outer.left - outer.right).coerceAtLeast(0).toInt()
                val child = content.layout(innerWidth)
                val width = checkedAdd(checkedAdd(child.width, outer.left, BORDERED), outer.right, BORDERED)
                val height = checkedAdd(checkedAdd(child.height, outer.top, BORDERED), outer.bottom, BORDERED)
                return ContentLayout.raw(
                    width, height, Bordered(child, title, badge, gutters, borderColor, titleColor),
                )
            }
        }

        private const val BORDERED: String = "bordered size"

        /** One cell on each side, consumed by every [Bordered] box. */
        public val BORDER: Insets = Insets.all(1)

        /**
         * Breathing room between the border and the content. `top = 1` is the blank spacer row
         * under the title, uniform with the left/right gutters; `bottom = 0` leaves content
         * flush against the bottom border.
         *
         * The horizontal gutters are frame — fixed for a scrolling panel's lifetime — but [top]
         * is NOT: it is prepended to the content stream (see [scrollingPanel]) rather than baked
         * into the viewport, so it is visible at rest and the content reclaims its row the moment
         * the user scrolls.
         */
        internal val PADDING: Insets = Insets(left = 1, top = 1, right = 1)

        /**
         * A scrolling panel's viewport: border plus the horizontal gutters, at full inner height.
         * Deliberately not public: a caller translating a screen click into content-stream
         * coordinates asks [tenter.panel.PanelSet.hitTest], which reads the completed frame's own
         * geometry, rather than re-deriving it from this constant and a scroll offset.
         */
        internal val VIEWPORT_INSET: Insets = BORDER + PADDING.horizontal()
    }
}
