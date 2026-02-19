package battletech.tui.view

import battletech.tui.aUnit
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SidebarViewTest {

    @Test
    fun `renders unit name`() {
        val unit = aUnit(name = "Atlas")
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(22, 12)

        view.render(buffer, 0, 0, 22, 12)

        val text = (1 until 6).map { buffer.get(it, 1).char }.joinToString("")
        assertEquals("Atlas", text)
    }

    @Test
    fun `renders gunnery and piloting skills`() {
        val unit = aUnit()
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(22, 12)

        view.render(buffer, 0, 0, 22, 12)

        val line = (1 until 14).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("Pilot: 4 / 5", line.trim())
    }

    @Test
    fun `renders heat info`() {
        val unit = aUnit()
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(22, 12)

        view.render(buffer, 0, 0, 22, 12)

        val line = (1 until 14).map { buffer.get(it, 3).char }.joinToString("")
        assertEquals("Heat: 0 / 10", line.trim())
    }

    @Test
    fun `renders with no unit selected shows empty`() {
        val view = SidebarView(null)
        val buffer = ScreenBuffer(22, 12)

        view.render(buffer, 0, 0, 22, 12)

        val text = (1 until 17).map { buffer.get(it, 1).char }.joinToString("")
        assertEquals("No unit selected", text.trim())
    }

    @Test
    fun `renders box border`() {
        val view = SidebarView(null)
        val buffer = ScreenBuffer(22, 12)

        view.render(buffer, 0, 0, 22, 12)

        assertEquals('╭', buffer.get(0, 0).char)
        assertEquals('╮', buffer.get(21, 0).char)
        assertEquals('╰', buffer.get(0, 11).char)
        assertEquals('╯', buffer.get(21, 11).char)
    }
}
