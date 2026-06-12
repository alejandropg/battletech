package battletech.tui.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ScrollbarTest {

    @Test
    fun `returns null when content fits in viewport`() {
        assertNull(Scrollbar.thumb(track = 10, contentHeight = 5, viewportHeight = 10, offset = 0))
        assertNull(Scrollbar.thumb(track = 10, contentHeight = 10, viewportHeight = 10, offset = 0))
    }

    @Test
    fun `returns null for degenerate track of zero or less`() {
        assertNull(Scrollbar.thumb(track = 0, contentHeight = 20, viewportHeight = 5, offset = 0))
        assertNull(Scrollbar.thumb(track = -1, contentHeight = 20, viewportHeight = 5, offset = 0))
    }

    @Test
    fun `minimum thumb size is 1 for huge content`() {
        val range = Scrollbar.thumb(track = 10, contentHeight = 10000, viewportHeight = 10, offset = 0)!!
        assertEquals(1, range.last - range.first + 1)
    }

    @Test
    fun `thumb is flush at top when offset is 0`() {
        val range = Scrollbar.thumb(track = 10, contentHeight = 20, viewportHeight = 10, offset = 0)!!
        assertEquals(0, range.first)
    }

    @Test
    fun `thumb is flush at bottom when offset equals maxOffset`() {
        val maxOffset = 10
        val range = Scrollbar.thumb(track = 10, contentHeight = 20, viewportHeight = 10, offset = maxOffset)!!
        assertEquals(9, range.last)
    }

    @Test
    fun `thumb is proportional in the middle`() {
        val track = 10
        val contentHeight = 20
        val viewportHeight = 10
        val maxOffset = contentHeight - viewportHeight
        val thumbSize = track * viewportHeight / contentHeight

        val range = Scrollbar.thumb(track, contentHeight, viewportHeight, offset = maxOffset / 2)!!
        val expectedStart = (maxOffset / 2 * (track - thumbSize) + maxOffset / 2) / maxOffset
        assertEquals(expectedStart, range.first)
        assertEquals(expectedStart + thumbSize - 1, range.last)

    }

    @Test
    fun `track of 1 row clamps thumb to that single row`() {
        val range = Scrollbar.thumb(track = 1, contentHeight = 20, viewportHeight = 5, offset = 0)!!
        assertEquals(0, range.first)
        assertEquals(0, range.last)
    }
}
