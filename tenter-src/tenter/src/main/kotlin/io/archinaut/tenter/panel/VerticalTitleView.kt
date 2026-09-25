package io.archinaut.tenter.panel

import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.view.ContentLayout
import io.archinaut.tenter.view.ContentView
import io.archinaut.tenter.view.verticalTextContent

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
