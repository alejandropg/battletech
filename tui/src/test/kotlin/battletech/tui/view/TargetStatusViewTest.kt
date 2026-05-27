package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tactical.query.PublicUnit
import battletech.tactical.query.PublicWeapon
import battletech.tactical.unit.UnitId
import battletech.tui.anArmorLayout
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TargetStatusViewTest {

    private fun aPublicUnit(
        name: String = "Hunchback",
        walkingMP: Int = 4,
        runningMP: Int = 6,
        jumpMP: Int = 0,
        weapons: List<PublicWeapon> = listOf(PublicWeapon("AC/20")),
    ): PublicUnit = PublicUnit(
        id = UnitId("u1"),
        owner = PlayerId.PLAYER_1,
        name = name,
        walkingMP = walkingMP,
        runningMP = runningMP,
        jumpMP = jumpMP,
        armor = anArmorLayout(),
        weapons = weapons,
    )

    @Test
    fun `renders box border with title TARGET STATUS`() {
        val view = TargetStatusView(null)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(27, 0).char)
        assertEquals("╰", buffer.get(0, 29).char)
        assertEquals("╯", buffer.get(27, 29).char)
        val prefix = (2 until 6).map { buffer.get(it, 0).char }.joinToString("")
        assertEquals("[4] ", prefix)
        val title = (6 until 19).map { buffer.get(it, 0).char }.joinToString("")
        assertEquals("TARGET STATUS", title)
    }

    @Test
    fun `renders unit name in BRIGHT_YELLOW`() {
        val unit = aPublicUnit(name = "Hunchback")
        val view = TargetStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        val line = (2 until 11).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("Hunchback", line)
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(2, 2).fg)
    }

    @Test
    fun `renders No target selected when unit is null`() {
        val view = TargetStatusView(null)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        val line = (2 until 20).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("No target selected", line.trim())
    }

    @Test
    fun `renders MOVEMENT section with walk and run values`() {
        val unit = aPublicUnit(walkingMP = 4, runningMP = 6)
        val view = TargetStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        val headerRow = (2 until 26).map { buffer.get(it, 4).char }.joinToString("")
        assertTrue(headerRow.contains("MOVEMENT"))
        val walkRunRow = (2 until 26).map { buffer.get(it, 5).char }.joinToString("")
        assertTrue(walkRunRow.contains("Walk"))
        assertTrue(walkRunRow.contains("Run"))
        assertTrue(walkRunRow.contains("4"))
        assertTrue(walkRunRow.contains("6"))
    }

    @Test
    fun `renders ARMOR section with HD CT and LL values`() {
        val unit = aPublicUnit()
        val view = TargetStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        val armorHeader = (2 until 26).map { buffer.get(it, 7).char }.joinToString("")
        assertTrue(armorHeader.contains("ARMOR"))
        val hdRow = (2 until 26).map { buffer.get(it, 8).char }.joinToString("")
        assertTrue(hdRow.contains("HD"))
        assertTrue(hdRow.contains("9"))
        val ctRow = (2 until 26).map { buffer.get(it, 9).char }.joinToString("")
        assertTrue(ctRow.contains("CT"))
        assertTrue(ctRow.contains("47"))
        val llRow = (2 until 26).map { buffer.get(it, 12).char }.joinToString("")
        assertTrue(llRow.contains("LL"))
        assertTrue(llRow.contains("41"))
    }

    @Test
    fun `renders WEAPONS section with weapon names`() {
        val unit = aPublicUnit(weapons = listOf(PublicWeapon("AC/20"), PublicWeapon("Medium Laser")))
        val view = TargetStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        val weaponsHeader = (2 until 26).map { buffer.get(it, 14).char }.joinToString("")
        assertTrue(weaponsHeader.contains("WEAPONS"))
        val weapon1Row = (2 until 26).map { buffer.get(it, 15).char }.joinToString("")
        assertTrue(weapon1Row.contains("AC/20"))
        val weapon2Row = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(weapon2Row.contains("Medium Laser"))
    }

    @Test
    fun `does not render PILOT section`() {
        val unit = aPublicUnit()
        val view = TargetStatusView(unit)
        val buffer = ScreenBuffer(28, 30)

        view.render(buffer, 0, 0, 28, 30)

        val allText = (0 until 30).flatMap { row ->
            (0 until 28).map { col -> buffer.get(col, row).char }
        }.joinToString("")
        assertFalse(allText.contains("Gunnery"))
        assertFalse(allText.contains("Piloting"))
    }
}
