package battletech.tui.view

import tenter.view.ContentLayout
import tenter.view.ContentView
import tenter.view.TextCursor
import tenter.view.contentView

/** Shared prepared-content adapter for views whose layout is entirely TextCursor instructions. */
internal abstract class PreparedTextView : ContentView {
    final override fun layout(availableWidth: Int): ContentLayout =
        contentView { render(it) }.layout(availableWidth)

    protected abstract fun render(content: TextCursor)
}
