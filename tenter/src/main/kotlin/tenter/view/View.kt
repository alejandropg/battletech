package tenter.view

import tenter.screen.Canvas

public interface View {
    public fun draw(canvas: Canvas)

    public companion object {
        /** Draws nothing — a placeholder for a grid slot ([Columns]/[Stack]) that stays empty. */
        public val None: View = object : View {
            override fun draw(canvas: Canvas) = Unit
        }
    }
}
