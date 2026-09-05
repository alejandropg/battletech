package battletech.tui.animation

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.view.View

/**
 * Draws one frame of [panel]'s animation into its intrinsic-size canvas — every cell, space
 * included, so the panel is fully opaque over whatever the board painted underneath it, and over
 * any earlier panel it overlaps.
 *
 * The frame carries its own palette (see [Glyphs]), so this never has to consult the animation
 * that produced it.
 */
internal class AnimationView(private val panel: AnimationPanel) : View {
    override fun draw(canvas: Canvas) {
        val glyphs = panel.animation.frame(panel.frameIndex)
        for (y in 0 until glyphs.height) {
            for (x in 0 until glyphs.width) {
                canvas.set(x, y, Cell(glyphs.get(x, y).toString(), glyphs.styleAt(x, y)))
            }
        }
    }
}
