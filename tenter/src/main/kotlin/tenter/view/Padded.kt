package tenter.view

import tenter.screen.Canvas
import tenter.screen.Insets

/** Decorates [content] with logical space on every requested edge. */
public class Padded(private val insets: Insets, private val content: View) : ContentView {
    override fun layout(availableWidth: Int): ContentLayout {
        require(availableWidth >= 0) { "available width must not be negative: $availableWidth" }
        val innerWidth = (availableWidth - insets.left - insets.right).coerceAtLeast(0)
        val child = (content as? ContentView)?.layout(innerWidth)
            ?: legacyContentLayout(innerWidth, content)
        val width = checkedAdd(checkedAdd(child.width, insets.left, "padded width"), insets.right, "padded width")
        val height = checkedAdd(checkedAdd(child.height, insets.top, "padded height"), insets.bottom, "padded height")
        return contentLayout(width, height) {
            place(insets.left, insets.top, child)
        }
    }

    override fun draw(canvas: Canvas) {
        content.draw(canvas.inset(insets))
    }

    private fun checkedAdd(left: Int, right: Int, description: String): Int =
        try {
            Math.addExact(left, right)
        } catch (_: ArithmeticException) {
            error("$description overflowed")
        }
}
