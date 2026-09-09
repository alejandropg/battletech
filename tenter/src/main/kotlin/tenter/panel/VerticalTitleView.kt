package tenter.panel

import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.ContentLayout
import tenter.view.ContentView
import tenter.view.verticalTextContent

/**
 * [title], one letter per row, centered in whatever space it is given — content only, no border.
 * A minimized panel's stub content; [Panel.render] wraps it (and every other state) in its own
 * chrome, so this view never draws a border of its own — see `Bordered`'s KDoc.
 */
public class VerticalTitleView(private val title: String) : ContentView {
    override fun layout(availableWidth: Int): ContentLayout =
        verticalTextContent(title, TEXT_PRIMARY_STYLE).layout(availableWidth)

    private companion object {
        private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
