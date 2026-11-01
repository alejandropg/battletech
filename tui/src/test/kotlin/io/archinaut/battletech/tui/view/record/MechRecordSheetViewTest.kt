package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.battletech.tactical.unit.MechModels
import io.archinaut.battletech.tactical.unit.MovementThisTurn
import io.archinaut.battletech.tactical.unit.PublicWeapon
import io.archinaut.battletech.tactical.unit.UnitId
import io.archinaut.battletech.tactical.unit.WeaponMountId
import io.archinaut.battletech.tactical.unit.createUnit
import io.archinaut.battletech.tui.aGameMap
import io.archinaut.battletech.tui.aUnit
import io.archinaut.battletech.tui.anArmorLayout
import io.archinaut.battletech.tui.anInternalStructureLayout
import io.archinaut.battletech.tui.screen.BoardRole
import io.archinaut.battletech.tui.view.render
import io.archinaut.tenter.color.StandardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.terminal.CompletedFrame
import io.archinaut.tenter.screen.Point

/** [MechRecordSheetView] — subject dispatch, the width clamp, and the foreign-unit redaction guard. */
internal class MechRecordSheetViewTest {

    private fun atlas() = MechModels["AS7-D"].createUnit(
        id = UnitId("A1"),
        owner = PlayerId.PLAYER_1,
        position = HexCoordinates(0, 0),
        facing = HexDirection.N,
    )

    private fun aForeignUnit(): ForeignUnit = ForeignUnit(
        id = UnitId("H1"),
        owner = PlayerId.PLAYER_2,
        name = "Hunchback HBK-4G",
        tonnage = 50,
        position = HexCoordinates(1, 1),
        facing = HexDirection.N,
        torsoFacing = HexDirection.N,
        armor = anArmorLayout(),
        maxArmor = anArmorLayout(),
        walkingMP = 4,
        runningMP = 6,
        jumpMP = 0,
        weapons = listOf(PublicWeapon("AC/20", WeaponMountId(0))),
        isProne = false,
        isShutdown = false,
        isDestroyed = false,
        isPilotConscious = true,
        movementThisTurn = MovementThisTurn.Stationary,
    )

    private fun assertTwoBlankRowsBefore(buffer: CompletedFrame, row: Int, width: Int) {
        assertTrue(buffer.region(Rect(0, row - 1, width, 1)).line(0).isBlank())
        assertTrue(buffer.region(Rect(0, row - 2, width, 1)).line(0).isBlank())
        assertFalse(buffer.region(Rect(0, row - 3, width, 1)).line(0).isBlank())
    }

    @Test
    fun `no subject shows a placeholder`() {
        val buffer = render(MechRecordSheetView(null, aGameMap()), width = 200, height = 20)

        assertTrue(buffer.text().contains("No unit selected"))
    }

    @Test
    fun `an own unit renders the full sheet, including private sections`() {
        val buffer = render(MechRecordSheetView(atlas(), aGameMap()), width = 200, height = 300)
        val text = buffer.text()

        assertTrue(text.contains("WARRIOR DATA"))
        assertTrue(text.contains("HEAT SCALE"))
        assertTrue(text.contains("SYSTEM DAMAGE"))
        assertTrue(text.contains("INTERNAL STRUCTURE DIAGRAM"))
    }

    @Test
    fun `an own unit lays out cards in the requested grid`() {
        val buffer = render(MechRecordSheetView(atlas(), aGameMap()), width = 200, height = 300)

        val topRow = buffer.find("'MECH DATA")!!.y
        assertEquals(Point(0, topRow), buffer.find("── 'MECH DATA"))
        assertEquals(Point(30, topRow), buffer.find("── WARRIOR DATA"))
        assertEquals(Point(60, topRow), buffer.find("── WEAPONS & EQUIPMENT INVENTORY"))
        assertEquals(Point(120, topRow), buffer.find("── HEAT "))

        val diagramRow = buffer.find("ARMOR DIAGRAM")!!.y
        assertEquals(Point(0, diagramRow), buffer.find("── ARMOR DIAGRAM"))
        assertEquals(Point(60, diagramRow), buffer.find("── INTERNAL STRUCTURE DIAGRAM"))
        assertTwoBlankRowsBefore(buffer, diagramRow, SheetLayout.MAIN_CONTENT_WIDTH)
        assertFalse(buffer.region(Rect(120, diagramRow, SheetLayout.HEAT_WIDTH, 1)).line(0).isBlank())

        val heatScaleRow = buffer.find("HEAT SCALE")!!.y
        val criticalRow = buffer.find("CRITICAL HIT TABLE")!!.y
        assertEquals(Point(0, criticalRow), buffer.find("── CRITICAL HIT TABLE"))
        assertEquals("─", buffer.get(SheetLayout.SHEET_WIDTH - 1, criticalRow).symbol)
        assertEquals(" ", buffer.get(SheetLayout.SHEET_WIDTH, criticalRow).symbol)
        assertTrue(criticalRow > heatScaleRow + 31)
        assertTwoBlankRowsBefore(buffer, criticalRow, SheetLayout.SHEET_WIDTH)

        val systemRow = buffer.find("SYSTEM DAMAGE")!!.y
        assertEquals(Point(0, systemRow), buffer.find("── SYSTEM DAMAGE"))
        assertEquals(criticalRow + 17, systemRow)
        assertEquals("─", buffer.get(SheetLayout.SYSTEM_DAMAGE_WIDTH - 1, systemRow).symbol)
        assertEquals(" ", buffer.get(SheetLayout.SYSTEM_DAMAGE_WIDTH, systemRow).symbol)
    }

    @Test
    fun `an own unit does not repeat the unit label above the grid — MechDataCard already shows it`() {
        val buffer = render(MechRecordSheetView(atlas(), aGameMap()), width = 200, height = 300)
        val topRow = buffer.find("'MECH DATA")!!.y

        assertEquals(0, topRow)
        assertEquals(1, buffer.text().split("A1:").size - 1)
    }

    @Test
    fun `an own unit renders each special status on a separate line`() {
        val unit = atlas().copy(isDestroyed = true, isPilotConscious = false)
        val buffer = render(MechRecordSheetView(unit, aGameMap()), width = 200, height = 300)

        val destroyedRow = buffer.find("DESTROYED")!!.y
        val unconsciousRow = buffer.find("PILOT UNCONSCIOUS")!!.y
        assertEquals(destroyedRow + 1, unconsciousRow)
        assertEquals(StandardRole.DANGER, buffer.get(0, destroyedRow).style.fg)
        assertEquals(StandardRole.DANGER, buffer.get(0, unconsciousRow).style.fg)
    }

    @Test
    fun `an own unit's armor diagram strikes through a location whose internal structure is destroyed`() {
        val unit = aUnit(internalStructure = anInternalStructureLayout(centerTorso = 0))
        val buffer = render(MechRecordSheetView(unit, aGameMap()), width = 200, height = 300)

        // Center torso ARMOR value (unaffected by the internal-structure override): 47/47.
        val (col, row) = buffer.find("47/47")!!

        assertEquals(BoardRole.DESTROYED, buffer.get(col, row).style.fg)
        assertTrue(buffer.get(col, row).style.strikethrough == true)
    }

    @Test
    fun `a foreign unit renders only public sections`() {
        val buffer = render(MechRecordSheetView(aForeignUnit(), aGameMap()), width = 200, height = 60)
        val text = buffer.text()

        assertTrue(text.contains("ARMOR DIAGRAM"))
        assertTrue(text.contains("AC/20"))
        assertFalse(text.contains("WARRIOR DATA"))
        assertFalse(text.contains("HEAT SCALE"))
        assertFalse(text.contains("SYSTEM DAMAGE"))
        assertFalse(text.contains("INTERNAL STRUCTURE DIAGRAM"))
        assertFalse(text.contains("Gunnery"))

        val topRow = buffer.find("'MECH DATA")!!.y
        assertEquals(Point(0, topRow), buffer.find("── 'MECH DATA"))
        assertEquals(Point(60, topRow), buffer.find("── WEAPONS & EQUIPMENT INVENTORY"))

        val diagramRow = buffer.find("ARMOR DIAGRAM")!!.y
        assertTwoBlankRowsBefore(buffer, diagramRow, SheetLayout.MAIN_CONTENT_WIDTH)
    }

    @Test
    fun `a foreign unit does not repeat the unit label above the grid — MechDataCard already shows it`() {
        val buffer = render(MechRecordSheetView(aForeignUnit(), aGameMap()), width = 200, height = 60)
        val topRow = buffer.find("'MECH DATA")!!.y

        assertEquals(0, topRow)
        assertEquals(1, buffer.text().split("H1:").size - 1)
    }

    @Test
    fun `content never writes past the sheet's column budget regardless of panel width`() {
        val buffer = render(MechRecordSheetView(atlas(), aGameMap()), width = 400, height = 300)

        for (row in 0 until buffer.height) {
            for (col in SheetLayout.SHEET_WIDTH until buffer.width) {
                val cell = buffer.get(col, row)
                assertTrue(cell.symbol == " ", "unexpected '${cell.symbol}' at ($col,$row), past the ${SheetLayout.SHEET_WIDTH}-column budget")
            }
        }
    }
}
