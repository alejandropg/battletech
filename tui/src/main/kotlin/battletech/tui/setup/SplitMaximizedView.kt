package battletech.tui.setup

import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.view.ContentLayout
import io.archinaut.tenter.view.ContentView
import io.archinaut.tenter.view.RevealPreference
import io.archinaut.tenter.view.contentLayout
import io.archinaut.tenter.view.contentView

/** Prepared composition for a maximized setup panel. */
internal class SplitMaximizedView(
    private val leftWidth: Int,
    private val left: ContentView,
    private val detail: ContentView?,
) : ContentView {

    override fun layout(availableWidth: Int): ContentLayout {
        require(availableWidth >= 0) { "available width must not be negative: $availableWidth" }
        val leftLayout = left.layout(leftWidth)
        val detailAvailableWidth = (
            availableWidth - leftWidth - SIDE_GUTTER - DIVIDER_WIDTH - SIDE_GUTTER
        ).coerceAtLeast(0)
        val detailLayout = detail?.layout(detailAvailableWidth)
        val height = maxOf(leftLayout.height, detailLayout?.height ?: 0)
        val detailX = leftWidth + SIDE_GUTTER + if (detailLayout == null) 0 else DIVIDER_WIDTH + SIDE_GUTTER
        val naturalWidth = totalWidth(leftWidth, detailLayout?.width ?: 0)
        val divider = if (detailLayout == null) null else divider(height)

        return contentLayout(
            width = naturalWidth,
            height = height,
            revealPreference = RevealPreference.FIRST,
        ) {
            place(0, 0, leftLayout)
            divider?.let { place(leftWidth + SIDE_GUTTER, 0, it) }
            detailLayout?.let { place(detailX, 0, it) }
        }
    }

    private fun divider(height: Int): ContentLayout = contentView { cursor ->
        repeat(height) {
            cursor.write(0, "│", Cell.Style(ChromeRole.PANEL_BORDER))
            cursor.newLine()
        }
    }.layout(1)

    internal companion object {
        const val SIDE_GUTTER: Int = 4
        const val DIVIDER_WIDTH: Int = 1

        internal fun totalWidth(leftWidth: Int, detailWidth: Int): Int =
            leftWidth + SIDE_GUTTER + if (detailWidth > 0) DIVIDER_WIDTH + SIDE_GUTTER + detailWidth else 0
    }
}
