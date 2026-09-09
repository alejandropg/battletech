package tenter.view

import tenter.screen.Canvas

/** A width-constrained prepared content source. */
public interface ContentView : View {
    public fun layout(availableWidth: Int): ContentLayout

    override fun draw(canvas: Canvas) {
        layout(canvas.width).draw(canvas)
    }
}
