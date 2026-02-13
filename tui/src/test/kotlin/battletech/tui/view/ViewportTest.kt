package battletech.tui.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ViewportTest {

    @Test
    fun `visible hex range at origin`() {
        val viewport = Viewport(scrollCol = 0, scrollRow = 0, widthChars = 32, heightChars = 16)

        val (colRange, rowRange) = viewport.visibleHexRange()

        assertEquals(0, colRange.first)
        assertEquals(3, colRange.last) // 32 / 8 = 4 cols (0..3)
        assertEquals(0, rowRange.first)
        assertEquals(3, rowRange.last) // 16 / 4 = 4 rows (0..3)
    }

    @Test
    fun `visible hex range with scroll offset`() {
        val viewport = Viewport(scrollCol = 2, scrollRow = 1, widthChars = 24, heightChars = 12)

        val (colRange, rowRange) = viewport.visibleHexRange()

        assertEquals(2, colRange.first)
        assertEquals(4, colRange.last) // 2 + 24/8 - 1 = 4
        assertEquals(1, rowRange.first)
        assertEquals(3, rowRange.last) // 1 + 12/4 - 1 = 3
    }

    @Test
    fun `visible hex range includes extra row for odd column offset`() {
        val viewport = Viewport(scrollCol = 0, scrollRow = 0, widthChars = 16, heightChars = 8)

        val (_, rowRange) = viewport.visibleHexRange()

        // Need extra row because odd columns shift down by 2 chars
        assertEquals(0, rowRange.first)
        assertEquals(1, rowRange.last) // 8 / 4 = 2 rows (0..1)
    }
}
