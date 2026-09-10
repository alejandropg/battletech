package tenter.view

import tenter.screen.Canvas

/** A width-constrained prepared content source. */
public interface ContentView : View {
    /**
     * Prepares content for a nonnegative available width. Logical width may exceed that width
     * for fixed content. Capture frame data here; do not use painting as a measurement pass.
     * The returned layout may be painted repeatedly (see [ContentLayout]).
     */
    public fun layout(availableWidth: Int): ContentLayout

    override fun draw(canvas: Canvas) {
        layout(canvas.width).draw(canvas)
    }
}
