package tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ScrollGeometryTest {

    @Test
    fun `returns null when content fits in viewport`() {
        assertNull(ScrollGeometry.thumb(track = 10, contentLength = 5, viewportLength = 10, offset = 0))
        assertNull(ScrollGeometry.thumb(track = 10, contentLength = 10, viewportLength = 10, offset = 0))
    }

    @Test
    fun `returns null for degenerate track of zero or less`() {
        assertNull(ScrollGeometry.thumb(track = 0, contentLength = 20, viewportLength = 5, offset = 0))
        assertNull(ScrollGeometry.thumb(track = -1, contentLength = 20, viewportLength = 5, offset = 0))
    }

    @Test
    fun `minimum thumb size is 1 for huge content`() {
        val range = ScrollGeometry.thumb(track = 10, contentLength = 10000, viewportLength = 10, offset = 0)!!
        assertEquals(1, range.last - range.first + 1)
    }

    @Test
    fun `thumb is flush at top when offset is 0`() {
        val range = ScrollGeometry.thumb(track = 10, contentLength = 20, viewportLength = 10, offset = 0)!!
        assertEquals(0, range.first)
    }

    @Test
    fun `thumb is flush at bottom when offset equals maxOffset`() {
        val maxOffset = 10
        val range = ScrollGeometry.thumb(track = 10, contentLength = 20, viewportLength = 10, offset = maxOffset)!!
        assertEquals(9, range.last)
    }

    @Test
    fun `thumb is proportional in the middle`() {
        val track = 10
        val contentLength = 20
        val viewportLength = 10
        val maxOffset = contentLength - viewportLength
        val thumbSize = track * viewportLength / contentLength

        val range = ScrollGeometry.thumb(track, contentLength, viewportLength, offset = maxOffset / 2)!!
        val expectedStart = (maxOffset / 2 * (track - thumbSize) + maxOffset / 2) / maxOffset
        assertEquals(expectedStart, range.first)
        assertEquals(expectedStart + thumbSize - 1, range.last)

    }

    @Test
    fun `track of 1 row clamps thumb to that single row`() {
        val range = ScrollGeometry.thumb(track = 1, contentLength = 20, viewportLength = 5, offset = 0)!!
        assertEquals(0, range.first)
        assertEquals(0, range.last)
    }
}
