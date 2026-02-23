package battletech.tui.view

import battletech.tui.aUnit
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SidebarViewTest {

    @Test
    fun `renders unit name`() {
        val unit = aUnit(name = "Atlas")
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val text = (2 until 7).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("Atlas", text)
    }

    @Test
    fun `renders gunnery and piloting skills`() {
        val unit = aUnit()
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val gunnery = (2 until 26).map { buffer.get(it, 5).char }.joinToString("").trim()
        assertEquals("Gunnery  : 4", gunnery)
        val piloting = (2 until 26).map { buffer.get(it, 6).char }.joinToString("").trim()
        assertEquals("Piloting : 5", piloting)
    }

    @Test
    fun `renders heat bar`() {
        val unit = aUnit()
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val line = (2 until 26).map { buffer.get(it, 12).char }.joinToString("")
        assertTrue(line.contains("[░░░░░░░░░░]"))
    }

    @Test
    fun `renders with no unit selected shows empty`() {
        val view = SidebarView(null)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val text = (2 until 18).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("No unit selected", text.trim())
    }

    @Test
    fun `renders box border`() {
        val view = SidebarView(null)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(27, 0).char)
        assertEquals("╰", buffer.get(0, 13).char)
        assertEquals("╯", buffer.get(27, 13).char)
    }

    @Test
    fun `renders walk and run movement points`() {
        val unit = aUnit(walkingMP = 3, runningMP = 5)
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val line = (2 until 26).map { buffer.get(it, 9).char }.joinToString("")
        assertTrue(line.contains("Walk"))
        assertTrue(line.contains("Run"))
        assertTrue(line.contains("3"))
        assertTrue(line.contains("5"))
    }

    @Test
    fun `renders jump only when nonzero`() {
        val unit = aUnit()
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val jumpRow = (2 until 26).map { buffer.get(it, 10).char }.joinToString("")
        assertFalse(jumpRow.contains("Jump"))
        val heatHeader = (2 until 26).map { buffer.get(it, 11).char }.joinToString("")
        assertTrue(heatHeader.contains("HEAT"))
    }

    @Test
    fun `renders heat bar in red when overheated`() {
        val unit = aUnit().copy(currentHeat = 8)
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        assertEquals(Color.RED, buffer.get(2, 12).fg)
    }
}
