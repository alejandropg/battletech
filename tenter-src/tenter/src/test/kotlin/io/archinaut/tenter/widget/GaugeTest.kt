package io.archinaut.tenter.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.ScreenBuffer
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.view.TextCursor

internal class GaugeTest {

    @Test
    fun `clamps out-of-range values before narrowing the scaled fill`() {
        val widget = Gauge(2, 1)
        val (cursor, buffer) = content()

        widget.draw(cursor, 0, Int.MAX_VALUE)
        widget.draw(cursor, 0, Int.MIN_VALUE)

        assertEquals("█", buffer.get(1, 0).char)
        assertEquals("█", buffer.get(2, 0).char)
        assertEquals("░", buffer.get(1, 2).char)
        assertEquals("░", buffer.get(2, 2).char)
    }

    private fun content(width: Int = 28, height: Int = 5): Pair<TextCursor, ScreenBuffer> {
        val buffer = ScreenBuffer(width, height)
        return TextCursor(Canvas.of(buffer)) to buffer
    }

    @Test
    fun `draws empty bar with suffix`() {
        val widget = Gauge(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 0)

        val row0 = (2 until 26).joinToString("") { buffer.get(it, 0).char }
        assertTrue(row0.contains("[" + "░".repeat(20) + "]30"))
        assertEquals("0", buffer.get(3, 1).char)
    }

    @Test
    fun `draws proportional fill`() {
        val widget = Gauge(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 15)

        val row0 = (2 until 26).joinToString("") { buffer.get(it, 0).char }
        assertTrue(row0.contains("█".repeat(10) + "░".repeat(10)))
    }

    @Test
    fun `right-aligns value under last filled cell`() {
        val widget = Gauge(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 15)

        assertEquals("1", buffer.get(11, 1).char)
        assertEquals("5", buffer.get(12, 1).char)
    }

    @Test
    fun `advances two rows`() {
        val widget = Gauge(20, 30)
        val (content, _) = content()

        widget.draw(content, 2, 0)

        assertEquals(2, content.row)
    }

    @Test
    fun `colors danger at seventy percent of max`() {
        val widget = Gauge(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 21)

        assertEquals(ChromeRole.DANGER, buffer.get(2, 0).style.fg)
        // value "21" is 2 chars; filled = 21*20/30 = 14, anchorCol = 2+14 = 16
        // "21" written at (16 - 2 + 1, 1) = (15, 1)
        assertEquals(ChromeRole.DANGER, buffer.get(15, 1).style.fg)
    }

    @Test
    fun `colors warning at thirty percent of max`() {
        val widget = Gauge(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 9)

        assertEquals(ChromeRole.WARNING, buffer.get(2, 0).style.fg)
    }

    @Test
    fun `colors info (cool) below thirty percent`() {
        val widget = Gauge(20, 30)
        val (content, buffer) = content()

        widget.draw(content, 2, 8)

        assertEquals(ChromeRole.INFO, buffer.get(2, 0).style.fg)
    }

    @Test
    fun `renders custom suffix after closing bracket`() {
        val widget = Gauge(10, 20, "DTS 10(20)")
        val (content, buffer) = content()

        widget.draw(content, 2, 10)

        val row0 = (2 until 28).joinToString("") { buffer.get(it, 0).char }
        assertTrue(row0.contains("]DTS 10(20)"))
        assertTrue(row0.contains("█".repeat(5) + "░".repeat(5)))
    }

    @Test
    fun `renders empty bar when max is zero`() {
        val widget = Gauge(10, 0, "STS 0")
        val (content, buffer) = content()

        widget.draw(content, 2, 0)

        val row0 = (2 until 28).joinToString("") { buffer.get(it, 0).char }
        assertTrue(row0.contains("░".repeat(10)))
        assertEquals(ChromeRole.DANGER, buffer.get(2, 0).style.fg)
    }

    @Test
    fun `rejects negative sizing parameters`() {
        assertThrows(IllegalArgumentException::class.java) { Gauge(-1, 20) }
        assertThrows(IllegalArgumentException::class.java) { Gauge(10, -1) }
    }

    @Test
    fun `accepts an explicit color policy`() {
        val widget = Gauge(10, 20, colorFor = { ChromeRole.SUCCESS })
        val (content, buffer) = content()

        widget.draw(content, 2, 20)

        assertEquals(ChromeRole.SUCCESS, buffer.get(2, 0).style.fg)
    }

    @Test
    fun `uses wide arithmetic when scaling a large value`() {
        val widget = Gauge(100, Int.MAX_VALUE)
        val (content, buffer) = content(width = 110)

        widget.draw(content, 2, Int.MAX_VALUE)

        assertEquals(ChromeRole.DANGER, buffer.get(2, 0).style.fg)
        assertEquals("█", buffer.get(101, 0).char)
    }
}
