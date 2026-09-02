package tenter.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.ChromeRole
import tenter.screen.ScreenBuffer
import tenter.view.TextCursor

internal class SelectableRowTest {

    @Test
    fun `draws a highlighted row with its cursor and checkbox`() {
        val (content, buffer) = content()

        SelectableRow.draw(
            content = content,
            label = "host",
            checkState = CheckState.UNCHECKED,
            cursor = true,
        )

        assertEquals("▶", buffer.get(0, 0).char)
        assertEquals("host", rowText(buffer, 4, 4))
        assertEquals(ChromeRole.ACCENT, buffer.get(4, 0).style.fg)
        assertEquals(ChromeRole.ACCENT, buffer.get(2, 0).style.fg)
        assertEquals(1, content.row)
    }

    @Test
    fun `draws value rows and preserves custom disabled styling`() {
        val (content, buffer) = content()

        SelectableRow.draw(
            content = content,
            label = "AC/20",
            checkState = CheckState.INDETERMINATE,
            cursor = false,
            right = "58%",
            subLines = listOf("+2 range"),
            textColor = ChromeRole.DISABLED,
            checkboxColor = ChromeRole.DISABLED,
        )

        assertEquals(" ", buffer.get(0, 0).char)
        assertEquals("AC/20", rowText(buffer, 4, 5))
        assertEquals(ChromeRole.DISABLED, buffer.get(4, 0).style.fg)
        assertEquals(ChromeRole.DISABLED, buffer.get(2, 0).style.fg)
        assertEquals("+2 range", rowText(buffer, 4, 8, row = 1))
        assertEquals(2, content.row)
    }

    private fun content(width: Int = 32, height: Int = 5): Pair<TextCursor, ScreenBuffer> {
        val buffer = ScreenBuffer(width, height)
        return TextCursor(Canvas.of(buffer)) to buffer
    }

    private fun rowText(buffer: ScreenBuffer, start: Int, length: Int, row: Int = 0): String =
        (start until start + length).joinToString("") { buffer.get(it, row).char }
}
