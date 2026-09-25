package tenter.view

import tenter.screen.Canvas

/** Explicitly dimensioned empty content for layout tests. */
internal object EmptyView : View {
    public override fun draw(canvas: Canvas): Unit = Unit
}
