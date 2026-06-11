package battletech.tui.view

import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
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

        val line = (2 until 26).map { buffer.get(it, 13).char }.joinToString("")
        assertTrue(line.contains("[" + "░".repeat(20) + "]30"))
    }

    @Test
    fun `renders STS heat sink capacity on row above bar`() {
        val unit = aUnit(heatSink = HeatSink(HeatSinkType.STS, 10))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val line = (2 until 26).map { buffer.get(it, 12).char }.joinToString("").trim()
        assertTrue(line.contains("STS: 10"))
    }

    @Test
    fun `renders DTS heat sink capacity with units and dissipation`() {
        val unit = aUnit(heatSink = HeatSink(HeatSinkType.DTS, 10))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 14)

        view.render(buffer, 0, 0, 28, 14)

        val line = (2 until 26).map { buffer.get(it, 12).char }.joinToString("").trim()
        assertTrue(line.contains("DTS: 10(20)"))
    }

    @Test
    fun `renders heat value paired to bar fill without max`() {
        // 15 of 30 -> barWidth=20 -> 10 filled cells. cx=2, anchorCol=12, "15" starts at col 11.
        val unit = aUnit().copy(currentHeat = 15)
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 15)

        view.render(buffer, 0, 0, 28, 15)

        // last digit sits under the last filled cell (col 12)
        assertEquals("1", buffer.get(11, 14).char)
        assertEquals("5", buffer.get(12, 14).char)
    }

    @Test
    fun `renders zero heat value under first bar cell`() {
        val unit = aUnit() // currentHeat = 0
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 15)

        view.render(buffer, 0, 0, 28, 15)

        // cx=2, "[" prefix -> first cell at col 3
        assertEquals("0", buffer.get(3, 14).char)
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

        assertEquals(Color.RED, buffer.get(2, 13).fg)
    }

    @Test
    fun `renders armor section header when armor is present`() {
        val unit = aUnit(armor = anArmorLayout())
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // Shifted down by the heat-sink row, the projected-heat "End:" line, and the standalone heat value line.
        val row17 = (2 until 26).map { buffer.get(it, 17).char }.joinToString("")
        assertTrue(row17.contains("ARMOR"))
    }

    @Test
    fun `renders head armor value in cyan`() {
        val unit = aUnit(armor = anArmorLayout(head = 9))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // HD value row: cy=18, "HD: 9" starts at cx+9=11
        val line = (2 until 26).map { buffer.get(it, 18).char }.joinToString("")
        assertTrue(line.contains("HD"))
        assertTrue(line.contains("9"))
        assertEquals(Color.CYAN, buffer.get(11, 18).fg) // 'H' of "HD: 9"
    }

    @Test
    fun `renders center torso armor in bright yellow`() {
        val unit = aUnit(armor = anArmorLayout(centerTorso = 47))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // CT row: cy=19, "CT:47" starts at cx+9=11
        val line = (2 until 26).map { buffer.get(it, 19).char }.joinToString("")
        assertTrue(line.contains("CT"))
        assertTrue(line.contains("47"))
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(11, 19).fg) // 'C' of "CT:47"
    }

    @Test
    fun `renders torso rear values in default color`() {
        val unit = aUnit(armor = anArmorLayout(centerTorsoRear = 8))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // Rear row: cy=20, CT rear "r: 8" starts at cx+10=12
        val line = (2 until 26).map { buffer.get(it, 20).char }.joinToString("")
        assertTrue(line.contains("r"))
        assertEquals(Color.DEFAULT, buffer.get(12, 20).fg) // 'r' of CT rear
    }

    @Test
    fun `renders arm and leg armor values`() {
        val unit = aUnit(armor = anArmorLayout(leftArm = 34, rightArm = 34, leftLeg = 41, rightLeg = 41))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        // Arms row: cy=21
        val armsRow = (2 until 26).map { buffer.get(it, 21).char }.joinToString("")
        assertTrue(armsRow.contains("LA"))
        assertTrue(armsRow.contains("RA"))
        // Legs row: cy=22
        val legsRow = (2 until 26).map { buffer.get(it, 22).char }.joinToString("")
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

        // Source line under the bar and the heat value line (row 15).
        val line = (2 until 26).map { buffer.get(it, 15).char }.joinToString("")
        assertTrue(line.contains("Running +2"))
        assertEquals(Color.DEFAULT, buffer.get(2, 15).fg)
    }

    @Test
    fun `renders pending heat preview in gray`() {
        val unit = aUnit()
        val view = UnitStatusView(unit, pendingHeat = listOf(HeatSource("Walking", 1)))
        val buffer = ScreenBuffer(28, 20)

        view.render(buffer, 0, 0, 28, 20)

        val line = (2 until 26).map { buffer.get(it, 15).char }.joinToString("")
        assertTrue(line.contains("Walking +1"))
        assertEquals(Color.GRAY, buffer.get(2, 15).fg)
    }

    @Test
    fun `renders projected end-of-turn heat`() {
        // current 12, +2 running, dissipates 10 -> projected 4
        val unit = aUnit().copy(currentHeat = 12, heatGeneratedThisTurn = listOf(HeatSource("Running", 2)))
        val view = UnitStatusView(unit)
        val buffer = ScreenBuffer(28, 20)

        view.render(buffer, 0, 0, 28, 20)

        // heatSink(12), bar(13), value(14), one source(15), End(16)
        val line = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(line.contains("End: 4"))
    }
}
