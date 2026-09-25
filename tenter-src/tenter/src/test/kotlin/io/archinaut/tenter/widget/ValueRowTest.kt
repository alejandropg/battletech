package io.archinaut.tenter.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.screen.ScreenBuffer
import io.archinaut.tenter.view.TextCursor

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
