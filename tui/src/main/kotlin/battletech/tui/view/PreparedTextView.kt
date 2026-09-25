package battletech.tui.view

import io.archinaut.tenter.view.ContentLayout
import io.archinaut.tenter.view.ContentView
import io.archinaut.tenter.view.TextCursor
import io.archinaut.tenter.view.contentView

/** Shared prepared-content adapter for views whose layout is entirely TextCursor instructions. */
internal abstract class PreparedTextView : ContentView {
    final override fun layout(availableWidth: Int): ContentLayout =
        contentView { render(it) }.layout(availableWidth)

    protected abstract fun render(content: TextCursor)
}
