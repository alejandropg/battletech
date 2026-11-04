package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.unit.ForeignUnit
import io.archinaut.battletech.tactical.unit.MovementThisTurn
import io.archinaut.battletech.tactical.unit.PublicWeapon
import io.archinaut.battletech.tactical.unit.UnitId
import io.archinaut.battletech.tactical.unit.WeaponMountId
import io.archinaut.battletech.tui.anArmorLayout
import io.archinaut.battletech.tui.view.render
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Point

/**
 * [ForeignRecordSheetView] drawn directly, as its own [io.archinaut.tenter.widget.Widget] — not just through
 * [MechRecordSheetView]'s dispatch — since it stopped being a static `render(canvas, content,
 * unit)` function and became an ordinary View.
 */
internal class ForeignRecordSheetViewTest {

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
    fun `renders the public cards only, in the owner sheet's grid slots`() {
        val buffer = render(ForeignRecordSheetView(aForeignUnit()), width = 200, height = 60)
        val text = buffer.text()

        assertTrue(text.contains("'MECH DATA"))
        assertTrue(text.contains("ARMOR DIAGRAM"))
        assertTrue(text.contains("AC/20"))
        assertFalse(text.contains("WARRIOR DATA"))
        assertFalse(text.contains("INTERNAL STRUCTURE DIAGRAM"))
    }

    @Test
    fun `leaves a blank warrior-data-width column so the weapons column lines up with the owner sheet`() {
        val buffer = render(ForeignRecordSheetView(aForeignUnit()), width = 200, height = 60)
        val topRow = buffer.find("'MECH DATA")!!.y

        assertEquals(Point(60, topRow), buffer.find("─── WEAPONS & EQUIPMENT INVENTORY"))
    }
}
