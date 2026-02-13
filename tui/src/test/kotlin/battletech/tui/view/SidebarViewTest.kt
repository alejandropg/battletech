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
        val buffer = ScreenBuffer(20, 10)

        view.render(buffer, 0, 0, 20, 10)

        val text = (0 until 5).map { buffer.get(it, 0).char }.joinToString("")
        assertEquals("Atlas", text)
    }

    @Test
    fun `renders gunnery and piloting skills`() {
        val unit = aUnit()
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(20, 10)

        view.render(buffer, 0, 0, 20, 10)

        val line = (0 until 12).map { buffer.get(it, 1).char }.joinToString("")
        assertEquals("Pilot: 4 / 5", line.trim())
    }

    @Test
    fun `renders heat info`() {
        val unit = aUnit()
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(20, 10)

        view.render(buffer, 0, 0, 20, 10)

        val line = (0 until 12).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("Heat: 0 / 10", line.trim())
    }

    @Test
    fun `renders with no unit selected shows empty`() {
        val view = SidebarView(null)
        val buffer = ScreenBuffer(20, 10)

        view.render(buffer, 0, 0, 20, 10)

        val text = (0 until 16).map { buffer.get(it, 0).char }.joinToString("")
        assertEquals("No unit selected", text.trim())
    }
}
