package battletech.tui.view.record

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.unit.MechModels
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.PublicWeapon
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.WeaponMountId
import battletech.tactical.unit.createUnit
import battletech.tui.aUnit
import battletech.tui.anArmorLayout
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.view.render
import tenter.view.text

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

    @Test
    fun `no subject shows a placeholder`() {
        val buffer = render(MechRecordSheetView(null), width = 200, height = 20)

        assertTrue(buffer.text().contains("No unit selected"))
    }

    @Test
    fun `an own unit renders the full sheet, including private sections`() {
        val buffer = render(MechRecordSheetView(atlas()), width = 200, height = 300)
        val text = buffer.text()

        assertTrue(text.contains("WARRIOR DATA"))
        assertTrue(text.contains("HEAT SCALE"))
        assertTrue(text.contains("SYSTEM DAMAGE"))
        assertTrue(text.contains("INTERNAL STRUCTURE DIAGRAM"))
    }

    @Test
    fun `a foreign unit renders only public sections`() {
        val buffer = render(MechRecordSheetView(aForeignUnit()), width = 200, height = 60)
        val text = buffer.text()

        assertTrue(text.contains("ARMOR DIAGRAM"))
        assertTrue(text.contains("AC/20"))
        assertFalse(text.contains("WARRIOR DATA"))
        assertFalse(text.contains("HEAT SCALE"))
        assertFalse(text.contains("SYSTEM DAMAGE"))
        assertFalse(text.contains("INTERNAL STRUCTURE DIAGRAM"))
        assertFalse(text.contains("Gunnery"))
    }

    @Test
    fun `content never writes past the sheet's column budget regardless of panel width`() {
        val buffer = render(MechRecordSheetView(atlas()), width = 400, height = 300)

        for (row in 0 until buffer.height) {
            for (col in SheetLayout.SHEET_WIDTH until buffer.width) {
                val cell = buffer.get(col, row)
                assertTrue(cell.char == " ", "unexpected '${cell.char}' at ($col,$row), past the ${SheetLayout.SHEET_WIDTH}-column budget")
            }
        }
    }
}
