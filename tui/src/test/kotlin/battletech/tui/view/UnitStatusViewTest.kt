package battletech.tui.view

import battletech.tactical.unit.HeatSource
import battletech.tui.aUnit
import battletech.tui.anArmorLayout
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UnitStatusViewTest {

    @Test
    fun `renders unit name`() {
        val unit = aUnit(name = "Atlas")
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val text = (2 until 7).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("Atlas", text)
    }

    @Test
    fun `renders gunnery and piloting skills`() {
        val unit = aUnit()
        val view = UnitStatusView(unit)
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
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val line = (2 until 26).map { buffer.get(it, 12).char }.joinToString("")
        assertTrue(line.contains("[" + "░".repeat(22) + "]"))
    }

    @Test
    fun `renders heat value paired to bar fill without max`() {
        // 15 of 30 -> 10 filled cells. cx=2, first cell at col 5, last filled at col 14.
        val unit = aUnit().copy(currentHeat = 15)
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        // last digit sits under the last filled cell (col 14)
        assertEquals("1", buffer.get(12, 13).char)
        assertEquals("5", buffer.get(13, 13).char)
    }

    @Test
    fun `renders zero heat value under first bar cell`() {
        val unit = aUnit() // currentHeat = 0
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        // cx=2, "[" prefix -> first cell at col 3
        assertEquals("0", buffer.get(3, 13).char)
    }

    @Test
    fun `renders with no unit selected shows empty`() {
        val view = UnitStatusView(null)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val text = (2 until 18).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("No unit selected", text.trim())
    }

    @Test
    fun `renders box border`() {
        val view = UnitStatusView(null)
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
        val view = UnitStatusView(unit)
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
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val jumpRow = (2 until 26).map { buffer.get(it, 10).char }.joinToString("")
        assertFalse(jumpRow.contains("Jump"))
        val heatHeader = (2 until 26).map { buffer.get(it, 11).char }.joinToString("")
        assertTrue(heatHeader.contains("HEAT"))
    }

    @Test
    fun `renders heat bar in red when overheated`() {
        val unit = aUnit().copy(currentHeat = 22)
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        assertEquals(Color.RED, buffer.get(2, 12).fg)
    }

    @Test
    fun `renders armor section header when armor is present`() {
        val unit = aUnit(armor = anArmorLayout())
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // Shifted down by the projected-heat "End:" line and the standalone heat value line.
        val row16 = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(row16.contains("ARMOR"))
    }

    @Test
    fun `renders head armor value in cyan`() {
        val unit = aUnit(armor = anArmorLayout(head = 9))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // HD value row: cy=17, "HD: 9" starts at cx+9=11
        val line = (2 until 26).map { buffer.get(it, 17).char }.joinToString("")
        assertTrue(line.contains("HD"))
        assertTrue(line.contains("9"))
        assertEquals(Color.CYAN, buffer.get(11, 17).fg) // 'H' of "HD: 9"
    }

    @Test
    fun `renders center torso armor in bright yellow`() {
        val unit = aUnit(armor = anArmorLayout(centerTorso = 47))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // CT row: cy=18, "CT:47" starts at cx+9=11
        val line = (2 until 26).map { buffer.get(it, 18).char }.joinToString("")
        assertTrue(line.contains("CT"))
        assertTrue(line.contains("47"))
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(11, 18).fg) // 'C' of "CT:47"
    }

    @Test
    fun `renders torso rear values in default color`() {
        val unit = aUnit(armor = anArmorLayout(centerTorsoRear = 8))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // Rear row: cy=19, CT rear "r: 8" starts at cx+10=12
        val line = (2 until 26).map { buffer.get(it, 19).char }.joinToString("")
        assertTrue(line.contains("r"))
        assertEquals(Color.DEFAULT, buffer.get(12, 19).fg) // 'r' of CT rear
    }

    @Test
    fun `renders arm and leg armor values`() {
        val unit = aUnit(armor = anArmorLayout(leftArm = 34, rightArm = 34, leftLeg = 41, rightLeg = 41))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // Arms row: cy=20
        val armsRow = (2 until 26).map { buffer.get(it, 20).char }.joinToString("")
        assertTrue(armsRow.contains("LA"))
        assertTrue(armsRow.contains("RA"))
        // Legs row: cy=21
        val legsRow = (2 until 26).map { buffer.get(it, 21).char }.joinToString("")
        assertTrue(legsRow.contains("LL"))
        assertTrue(legsRow.contains("RL"))
        assertTrue(legsRow.contains("41"))
    }

    @Test
    fun `renders committed heat sources in default color`() {
        val unit = aUnit().copy(heatGeneratedThisTurn = listOf(HeatSource("Running", 2)))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 20)

        view.render(buffer, 0, 0, 28, 20)

        // Source line under the bar and the heat value line (row 14).
        val line = (2 until 26).map { buffer.get(it, 14).char }.joinToString("")
        assertTrue(line.contains("Running +2"))
        assertEquals(Color.DEFAULT, buffer.get(2, 14).fg)
    }

    @Test
    fun `renders pending heat preview in gray`() {
        val unit = aUnit()
        val view = UnitStatusView(unit, pendingHeat = listOf(HeatSource("Walking", 1)))
        val buffer = ScreenBuffer(28, 20)

        view.render(buffer, 0, 0, 28, 20)

        val line = (2 until 26).map { buffer.get(it, 14).char }.joinToString("")
        assertTrue(line.contains("Walking +1"))
        assertEquals(Color.GRAY, buffer.get(2, 14).fg)
    }

    @Test
    fun `renders projected end-of-turn heat`() {
        // current 12, +2 running, dissipates 10 -> projected 4
        val unit = aUnit().copy(currentHeat = 12, heatGeneratedThisTurn = listOf(HeatSource("Running", 2)))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 20)

        view.render(buffer, 0, 0, 28, 20)

        // bar(12), value(13), one source(14), End(15)
        val line = (2 until 26).map { buffer.get(it, 15).char }.joinToString("")
        assertTrue(line.contains("End: 4"))
    }
}
