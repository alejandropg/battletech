package tenter.view

import tenter.screen.Canvas
import tenter.screen.ScreenBuffer

/** Renders [view] into a fresh [width]x[height] buffer and returns it for assertions. */
public fun render(view: View, width: Int, height: Int): ScreenBuffer {
    val buffer = ScreenBuffer(width, height)
    view.render(Canvas.of(buffer))
    return buffer
}

/** Renders [content] inside the real [scrollingPanel] chrome — the pixel-parity regression guard. */
public fun renderInPanel(
    content: View,
    badge: Char = '0',
    title: String = "T",
    width: Int = 28,
    height: Int = 30,
    scrollOffset: Int? = 0,
): ScreenBuffer = render(
    scrollingPanel(
        title = title,
        badge = badge.toString(),
        content = content,
        extent = ContentExtent.Measured(),
        offset = scrollOffset?.let { ScrollOffset(0, it) } ?: ScrollOffset.ZERO,
    ),
    width,
    height,
)

/** Row [y], columns [x] until [x] + [width], right-trimmed. */
public fun ScreenBuffer.line(y: Int, x: Int = 0, width: Int = this.width - x): String =
    (x until x + width).joinToString("") { get(it, y).char }.trimEnd()

/** The whole buffer as newline-separated rows — for `contains` assertions. */
public fun ScreenBuffer.text(): String = (0 until height).joinToString("\n") { line(it) }
