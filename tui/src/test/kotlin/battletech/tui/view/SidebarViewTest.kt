package battletech.tui.view

import battletech.tui.anArmorLayout
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

    @Test
    fun `does not render armor section when armor is null`() {
        val unit = aUnit(armor = null)
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // Row 14 is where ARMOR header would appear; it should be empty
        val row14 = (2 until 26).map { buffer.get(it, 14).char }.joinToString("")
        assertFalse(row14.contains("ARMOR"))
    }

    @Test
    fun `renders armor section header when armor is present`() {
        val unit = aUnit(armor = anArmorLayout())
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        val row14 = (2 until 26).map { buffer.get(it, 14).char }.joinToString("")
        assertTrue(row14.contains("ARMOR"))
    }

    @Test
    fun `renders head armor value in cyan`() {
        val unit = aUnit(armor = anArmorLayout(head = 9))
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // HD value row: cy=15, "HD: 9" starts at cx+9=11
        val line = (2 until 26).map { buffer.get(it, 15).char }.joinToString("")
        assertTrue(line.contains("HD"))
        assertTrue(line.contains("9"))
        assertEquals(Color.CYAN, buffer.get(11, 15).fg) // 'H' of "HD: 9"
    }

    @Test
    fun `renders center torso armor in bright yellow`() {
        val unit = aUnit(armor = anArmorLayout(centerTorso = 47))
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // CT row: cy=16, "CT:47" starts at cx+9=11
        val line = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(line.contains("CT"))
        assertTrue(line.contains("47"))
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(11, 16).fg) // 'C' of "CT:47"
    }

    @Test
    fun `renders torso rear values in default color`() {
        val unit = aUnit(armor = anArmorLayout(centerTorsoRear = 8))
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // Rear row: cy=17, CT rear "r: 8" starts at cx+10=12
        val line = (2 until 26).map { buffer.get(it, 17).char }.joinToString("")
        assertTrue(line.contains("r"))
        assertEquals(Color.DEFAULT, buffer.get(12, 17).fg) // 'r' of CT rear
    }

    @Test
    fun `renders arm and leg armor values`() {
        val unit = aUnit(armor = anArmorLayout(leftArm = 34, rightArm = 34, leftLeg = 41, rightLeg = 41))
        val view = SidebarView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // Arms row: cy=18
        val armsRow = (2 until 26).map { buffer.get(it, 18).char }.joinToString("")
        assertTrue(armsRow.contains("LA"))
        assertTrue(armsRow.contains("RA"))
        // Legs row: cy=19
        val legsRow = (2 until 26).map { buffer.get(it, 19).char }.joinToString("")
        assertTrue(legsRow.contains("LL"))
        assertTrue(legsRow.contains("RL"))
        assertTrue(legsRow.contains("41"))
    }
}
