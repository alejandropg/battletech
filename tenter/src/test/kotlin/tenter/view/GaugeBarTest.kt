package tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer
import tenter.screen.UiRole

internal class GaugeBarTest {

    private fun content(width: Int = 28, height: Int = 5): Pair<ContentWriter, ScreenBuffer> {
        val buffer = ScreenBuffer(width, height)
        return ContentWriter(Canvas.of(buffer)) to buffer
    }

    @Test
    fun `draws empty bar with suffix`() {
        val widget = GaugeBar(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 0)

        val row0 = (2 until 26).joinToString("") { buffer.get(it, 0).char }
        assertTrue(row0.contains("[" + "░".repeat(20) + "]30"))
        assertEquals("0", buffer.get(3, 1).char)
    }

    @Test
    fun `draws proportional fill`() {
        val widget = GaugeBar(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 15)

        val row0 = (2 until 26).joinToString("") { buffer.get(it, 0).char }
        assertTrue(row0.contains("█".repeat(10) + "░".repeat(10)))
    }

    @Test
    fun `right-aligns value under last filled cell`() {
        val widget = GaugeBar(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 15)

        assertEquals("1", buffer.get(11, 1).char)
        assertEquals("5", buffer.get(12, 1).char)
    }

    @Test
    fun `advances two rows`() {
        val widget = GaugeBar(20, 30)
        val (content, _) = content()

        widget.draw(content, 2, 0)

        assertEquals(2, content.row)
    }

    @Test
    fun `colors danger at seventy percent of max`() {
        val widget = GaugeBar(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 21)

        assertEquals(UiRole.DANGER, buffer.get(2, 0).style.fg)
        // value "21" is 2 chars; filled = 21*20/30 = 14, anchorCol = 2+14 = 16
        // "21" written at (16 - 2 + 1, 1) = (15, 1)
        assertEquals(UiRole.DANGER, buffer.get(15, 1).style.fg)
    }

    @Test
    fun `colors warning at thirty percent of max`() {
        val widget = GaugeBar(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 9)

        assertEquals(UiRole.WARNING, buffer.get(2, 0).style.fg)
    }

    @Test
    fun `colors info (cool) below thirty percent`() {
        val widget = GaugeBar(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 8)

        assertEquals(UiRole.INFO, buffer.get(2, 0).style.fg)
    }

    @Test
    fun `renders custom suffix after closing bracket`() {
        val widget = GaugeBar(10, 20, "DTS 10(20)")
        val (content, buffer) = content()

        widget.draw(content, 2, 10)

        val row0 = (2 until 28).joinToString("") { buffer.get(it, 0).char }
        assertTrue(row0.contains("]DTS 10(20)"))
        assertTrue(row0.contains("█".repeat(5) + "░".repeat(5)))
    }

    @Test
    fun `renders empty bar when max is zero`() {
        val widget = GaugeBar(10, 0, "STS 0")
        val (content, buffer) = content()

        widget.draw(content, 2, 0)

        val row0 = (2 until 28).joinToString("") { buffer.get(it, 0).char }
        assertTrue(row0.contains("░".repeat(10)))
        assertEquals(UiRole.DANGER, buffer.get(2, 0).style.fg)
    }
}
