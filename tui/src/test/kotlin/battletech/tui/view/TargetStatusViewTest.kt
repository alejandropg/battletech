package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.PublicWeapon
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.WeaponMountId
import battletech.tui.anArmorLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.screen.ScreenBuffer
import tenter.view.renderInPanel

internal class TargetStatusViewTest {

    private fun aForeignUnit(
        name: String = "Hunchback",
        walkingMP: Int = 4,
        runningMP: Int = 6,
        jumpMP: Int = 0,
        weapons: List<PublicWeapon> = listOf(PublicWeapon("AC/20", WeaponMountId(0))),
        isProne: Boolean = false,
        isShutdown: Boolean = false,
        isDestroyed: Boolean = false,
        isPilotConscious: Boolean = true,
    ): ForeignUnit = ForeignUnit(
        id = UnitId("u1"),
        owner = PlayerId.PLAYER_1,
        name = name,
        tonnage = 50,
        position = HexCoordinates(0, 0),
        facing = HexDirection.N,
        torsoFacing = HexDirection.N,
        armor = anArmorLayout(),
        maxArmor = anArmorLayout(),
        walkingMP = walkingMP,
        runningMP = runningMP,
        jumpMP = jumpMP,
        weapons = weapons,
        isProne = isProne,
        isShutdown = isShutdown,
        isDestroyed = isDestroyed,
        isPilotConscious = isPilotConscious,
        movementThisTurn = MovementThisTurn.Stationary,
    )

    /** Render via decorator at (0,0) — pixel-parity regression guard for box/coordinates. */
    private fun renderDecorated(view: TargetStatusView, width: Int = 28, height: Int = 30): ScreenBuffer =
        renderInPanel(view, badge = '4', title = TargetStatusView.TITLE, width = width, height = height)

    @Test
    fun `renders box border with title TARGET STATUS`() {
        val view = TargetStatusView(aForeignUnit())
        val buffer = renderDecorated(view)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(27, 0).char)
        assertEquals("╰", buffer.get(0, 29).char)
        assertEquals("╯", buffer.get(27, 29).char)
        val prefix = (2 until 6).joinToString("") { buffer.get(it, 0).char }
        assertEquals("[4] ", prefix)
        val title = (6 until 19).joinToString("") { buffer.get(it, 0).char }
        assertEquals("TARGET STATUS", title)
    }

    @Test
    fun `renders unit name in ACCENT`() {
        val unit = aForeignUnit()
        val view = TargetStatusView(unit)
        val buffer = renderDecorated(view)

        val line = (2 until 15).joinToString("") { buffer.get(it, 2).char }
        assertEquals("u1: Hunchback", line)
        assertEquals(ChromeRole.ACCENT, buffer.get(2, 2).style.fg)
    }

    @Test
    fun `renders public special statuses between the unit name and movement section`() {
        val unit = aForeignUnit(isDestroyed = true, isPilotConscious = false)
        val buffer = renderDecorated(TargetStatusView(unit))

        val destroyedRow = rowContaining(buffer, "DESTROYED")
        val unconsciousRow = rowContaining(buffer, "PILOT UNCONSCIOUS")
        assertEquals(destroyedRow + 1, unconsciousRow)
        assertTrue(destroyedRow > rowContaining(buffer, "u1: Hunchback"))
        assertTrue(unconsciousRow < rowContaining(buffer, "MOVEMENT"))
        assertEquals(ChromeRole.DANGER, buffer.get(2, destroyedRow).style.fg)
        assertEquals(ChromeRole.DANGER, buffer.get(2, unconsciousRow).style.fg)
    }

    @Test
    fun `renders MOVEMENT section with walk and run values`() {
        val unit = aForeignUnit()
        val view = TargetStatusView(unit)
        val buffer = renderDecorated(view)

        val headerRow = (2 until 26).joinToString("") { buffer.get(it, 4).char }
        assertTrue(headerRow.contains("MOVEMENT"))
        val walkRunRow = (2 until 26).joinToString("") { buffer.get(it, 5).char }
        assertTrue(walkRunRow.contains("Walk"))
        assertTrue(walkRunRow.contains("Run"))
        assertTrue(walkRunRow.contains("4"))
        assertTrue(walkRunRow.contains("6"))
    }

    @Test
    fun `renders ARMOR section with HD CT and LL values`() {
        val unit = aForeignUnit()
        val view = TargetStatusView(unit)
        val buffer = renderDecorated(view)

        val armorHeader = (2 until 26).joinToString("") { buffer.get(it, 7).char }
        assertTrue(armorHeader.contains("ARMOR"))
        val hdRow = (2 until 26).joinToString("") { buffer.get(it, 8).char }
        assertTrue(hdRow.contains("HD"))
        assertTrue(hdRow.contains("9"))
        val ctRow = (2 until 26).joinToString("") { buffer.get(it, 9).char }
        assertTrue(ctRow.contains("CT"))
        assertTrue(ctRow.contains("47"))
        val llRow = (2 until 26).joinToString("") { buffer.get(it, 12).char }
        assertTrue(llRow.contains("LL"))
        assertTrue(llRow.contains("41"))
    }

    @Test
    fun `renders WEAPONS section with weapon names`() {
        val unit = aForeignUnit(weapons = listOf(PublicWeapon("AC/20", WeaponMountId(0)), PublicWeapon("Medium Laser", WeaponMountId(1))))
        val view = TargetStatusView(unit)
        val buffer = renderDecorated(view)

        val weaponsHeader = (2 until 26).joinToString("") { buffer.get(it, 14).char }
        assertTrue(weaponsHeader.contains("WEAPONS"))
        val weapon1Row = (2 until 26).joinToString("") { buffer.get(it, 15).char }
        assertTrue(weapon1Row.contains("AC/20"))
        val weapon2Row = (2 until 26).joinToString("") { buffer.get(it, 16).char }
        assertTrue(weapon2Row.contains("Medium Laser"))
    }

    @Test
    fun `does not render PILOT section`() {
        val unit = aForeignUnit()
        val view = TargetStatusView(unit)
        val buffer = renderDecorated(view)

        val allText = (0 until 30).flatMap { row ->
            (0 until 28).map { col -> buffer.get(col, row).char }
        }.joinToString("")
        assertFalse(allText.contains("Gunnery"))
        assertFalse(allText.contains("Piloting"))
    }

    private fun rowContaining(buffer: ScreenBuffer, text: String): Int {
        for (row in 0 until buffer.height) {
            val line = (2 until 26).joinToString("") { buffer.get(it, row).char }
            if (line.contains(text)) return row
        }
        error("No row contains \"$text\"")
    }
}
