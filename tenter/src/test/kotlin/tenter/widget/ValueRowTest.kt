package tenter.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.ChromeRole
import tenter.screen.ScreenBuffer
import tenter.view.TextCursor

internal class ValueRowTest {

    @Test
    fun `places a right value using display width after a wide left value`() {
        val buffer = ScreenBuffer(10, 1)
        val content = TextCursor(Canvas.of(buffer))

        ValueRow.draw(content, left = "中", right = "R", subLines = emptyList(), color = ChromeRole.TEXT_PRIMARY)

        assertEquals("中", buffer.get(0, 0).char)
        assertEquals("R", buffer.get(9, 0).char)
        assertEquals(1, content.row)
    }
}
