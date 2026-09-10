package tenter.view

import tenter.screen.Canvas
import tenter.screen.Insets

/** Insets raw painting within its allocated canvas. Use [prepared] for intrinsic composition. */
public class Padded(
    private val insets: Insets,
    private val content: View,
) : View {
    public override fun draw(canvas: Canvas) {
        content.draw(canvas.inset(insets))
    }

    public companion object {
        /** Adds logical padding to prepared content, including otherwise blank space. */
        public fun prepared(insets: Insets, content: ContentView): ContentView = object : ContentView {
            public override fun layout(availableWidth: Int): ContentLayout {
                require(availableWidth >= 0) { "available width must not be negative: $availableWidth" }
                val innerWidth = (availableWidth.toLong() - insets.left - insets.right).coerceAtLeast(0).toInt()
                val child = content.layout(innerWidth)
                val width = Math.addExact(Math.addExact(child.width, insets.left), insets.right)
                val height = Math.addExact(Math.addExact(child.height, insets.top), insets.bottom)
                return contentLayout(width, height) {
                    place(insets.left, insets.top, child)
                }
            }
        }
    }
}
