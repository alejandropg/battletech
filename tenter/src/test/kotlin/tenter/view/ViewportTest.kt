package tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer

internal class ViewportTest {

    private val viewportWidth = 26
    private val viewportHeight = 8

    private fun content(lines: Int, revealRow: Int? = null): ContentView = contentView {
        repeat(lines) { row ->
            it.writeLine("line$row")
            if (row == revealRow) it.markRevealAt(row)
        }
    }

    private fun render(
        view: ContentView,
        state: ViewportState = ViewportState(),
        width: Int = viewportWidth,
        height: Int = viewportHeight,
    ): Viewport {
        Viewport(view, state).draw(Canvas.offscreen(width, height))
        return Viewport(view, state)
    }

    @Test
    fun `prepared content renders flush at viewport origin`() {
        val buffer = ScreenBuffer(viewportWidth, viewportHeight)
        Viewport(content(5)).draw(Canvas.of(buffer))

        assertEquals("line0", buffer.line(0, width = 10))
        assertEquals("line1", buffer.line(1, width = 10))
    }

    @Test
    fun `explicit offset shifts visible window`() {
        val state = ViewportState(ScrollOffset(y = 3))
        val buffer = ScreenBuffer(viewportWidth, viewportHeight)
        val viewport = Viewport(content(20), state)
        viewport.draw(Canvas.of(buffer))

        assertEquals("line3", buffer.line(0, width = 10))
        assertEquals(12, viewport.scroll.maxOffset.y)
    }

    @Test
    fun `prepared layout reaches rows beyond the old measurement ceiling`() {
        val state = ViewportState(ScrollOffset(y = 999))
        val viewport = Viewport(content(600), state)
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))

        assertEquals(592, viewport.scroll.maxOffset.y)
        assertEquals(592, viewport.scroll.offset.y)
    }

    @Test
    fun `fixed content preserves horizontal and vertical dimensions`() {
        val raw = fixedContent(50, 40, View.None)
        val viewport = Viewport(raw)
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))

        assertEquals(24, viewport.scroll.maxOffset.x)
        assertEquals(32, viewport.scroll.maxOffset.y)
    }

    @Test
    fun `first render follows a reveal target below the window`() {
        val viewport = Viewport(content(20, revealRow = 15))
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))

        assertTrue(15 in viewport.scroll.offset.y until viewport.scroll.offset.y + viewportHeight)
    }

    @Test
    fun `manual scroll survives an unchanged reveal target`() {
        val state = ViewportState()
        val viewport = Viewport(content(40, revealRow = 15), state)
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))
        state.scrollBy(0, -5)
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))

        assertEquals(3, viewport.scroll.offset.y)
    }

    @Test
    fun `moved reveal target follows from the current offset`() {
        var revealRow = 19
        val view = object : ContentView {
            override fun layout(availableWidth: Int): ContentLayout = content(40, revealRow).layout(availableWidth)
        }
        val state = ViewportState()
        val viewport = Viewport(view, state)
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))
        state.scrollBy(0, -5)
        revealRow = 20
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))

        assertTrue(20 in viewport.scroll.offset.y until viewport.scroll.offset.y + viewportHeight)
    }

    @Test
    fun `recenter centers a reveal target`() {
        val state = ViewportState()
        val viewport = Viewport(content(40, revealRow = 20), state)
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))
        state.requestRecenter()
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))

        assertEquals((20 - (viewportHeight - 1) / 2).coerceIn(0, viewport.scroll.maxOffset.y), viewport.scroll.offset.y)
    }

    @Test
    fun `zero-sized destination preserves the previous settled observation`() {
        val state = ViewportState()
        val viewport = Viewport(content(20), state)
        viewport.draw(Canvas.offscreen(viewportWidth, viewportHeight))
        val settled = state.settled
        viewport.draw(Canvas.offscreen(30, 0))

        assertEquals(settled, state.settled)
    }
}
