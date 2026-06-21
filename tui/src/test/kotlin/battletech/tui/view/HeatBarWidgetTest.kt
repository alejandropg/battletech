package battletech.tui.view

import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HeatBarWidgetTest {

    @Test
    fun `draws empty bar with suffix`() {
        val widget = HeatBarWidget(20, 30)
        val buffer = ScreenBuffer(28, 5)

        widget.draw(buffer, 2, 0, 0)

        val row0 = (2 until 26).map { buffer.get(it, 0).char }.joinToString("")
        assertTrue(row0.contains("[" + "░".repeat(20) + "]30"))
        assertEquals("0", buffer.get(3, 1).char)
    }

    @Test
    fun `draws proportional fill`() {
        val widget = HeatBarWidget(20, 30)
        val buffer = ScreenBuffer(28, 5)

        widget.draw(buffer, 2, 0, 15)

        val row0 = (2 until 26).map { buffer.get(it, 0).char }.joinToString("")
        assertTrue(row0.contains("█".repeat(10) + "░".repeat(10)))
    }

    @Test
    fun `right-aligns value under last filled cell`() {
        val widget = HeatBarWidget(20, 30)
        val buffer = ScreenBuffer(28, 5)

        widget.draw(buffer, 2, 0, 15)

        assertEquals("1", buffer.get(11, 1).char)
        assertEquals("5", buffer.get(12, 1).char)
    }

    @Test
    fun `returns next free row`() {
        val widget = HeatBarWidget(20, 30)
        val buffer = ScreenBuffer(28, 5)

        val drawResult = widget.draw(buffer, 2, 0, 0)

        assertEquals(2, drawResult)
    }

    @Test
    fun `colors red at seventy percent of max`() {
        val widget = HeatBarWidget(20, 30)
        val buffer = ScreenBuffer(28, 5)

        widget.draw(buffer, 2, 0, 21)

        assertEquals(Color.RED, buffer.get(2, 0).fg)
        // value "21" is 2 chars; filled = 21*20/30 = 14, anchorCol = 2+14 = 16
        // "21" written at (16 - 2 + 1, 1) = (15, 1)
        assertEquals(Color.RED, buffer.get(15, 1).fg)
    }

    @Test
    fun `colors yellow at thirty percent of max`() {
        val widget = HeatBarWidget(20, 30)
        val buffer = ScreenBuffer(28, 5)

        widget.draw(buffer, 2, 0, 9)

        assertEquals(Color.YELLOW, buffer.get(2, 0).fg)
    }

    @Test
    fun `colors light blue below thirty percent`() {
        val widget = HeatBarWidget(20, 30)
        val buffer = ScreenBuffer(28, 5)

        widget.draw(buffer, 2, 0, 8)

        assertEquals(Color.LIGHT_BLUE, buffer.get(2, 0).fg)
    }

    @Test
    fun `renders custom suffix after closing bracket`() {
        val widget = HeatBarWidget(10, 20, "DTS 10(20)")
        val buffer = ScreenBuffer(28, 5)

        widget.draw(buffer, 2, 0, 10)

        val row0 = (2 until 28).map { buffer.get(it, 0).char }.joinToString("")
        assertTrue(row0.contains("]DTS 10(20)"))
        assertTrue(row0.contains("█".repeat(5) + "░".repeat(5)))
    }

    @Test
    fun `renders empty bar when max is zero`() {
        val widget = HeatBarWidget(10, 0, "STS 0")
        val buffer = ScreenBuffer(28, 5)

        widget.draw(buffer, 2, 0, 0)

        val row0 = (2 until 28).map { buffer.get(it, 0).char }.joinToString("")
        assertTrue(row0.contains("░".repeat(10)))
        assertEquals(Color.RED, buffer.get(2, 0).fg)
    }
}
