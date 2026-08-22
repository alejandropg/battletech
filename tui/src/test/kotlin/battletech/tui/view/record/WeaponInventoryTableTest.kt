package battletech.tui.view.record

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.MechModels
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.createUnit
import battletech.tui.hex.ammoIcon
import battletech.tui.hex.infinityIcon
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.view.line
import tenter.view.render
import tenter.view.text

/** [WeaponInventoryTable], driven by a real Atlas AS7-D built through the production [MechModels] path. */
internal class WeaponInventoryTableTest {

    private fun atlas() = MechModels["AS7-D"].createUnit(
        id = UnitId("A1"),
        owner = PlayerId.PLAYER_1,
        position = HexCoordinates(0, 0),
        facing = HexDirection.N,
    )

    @Test
    fun `groups identical weapons into one qty-tallied row`() {
        val buffer = render(WeaponInventoryTable(atlas()), width = 120, height = 20)
        val text = buffer.text()

        // Atlas AS7-D mounts two Medium Lasers in each arm (4 total, split 2/2 by location).
        val row = (0 until buffer.height).map { buffer.line(it) }.first { it.contains("Medium Laser") }
        assertTrue(row.trimStart().startsWith("2"))
    }

    @Test
    fun `prints heat damage and range columns for a known weapon`() {
        val buffer = render(WeaponInventoryTable(atlas()), width = 120, height = 20)
        val row = (0 until buffer.height).map { buffer.line(it) }.first { it.contains("AC/20") }

        // AC/20: heat 7, damage 20, min 3, short 3, medium 6, long 9.
        for (value in listOf("7", "20", "3", "3", "6", "9")) {
            assertTrue(row.contains(value), "expected AC/20 row to contain '$value': $row")
        }
    }

    @Test
    fun `an infinite-ammo weapon shows the infinity glyph, an ammo-fed one shows a shot count`() {
        val buffer = render(WeaponInventoryTable(atlas()), width = 120, height = 20)
        val text = buffer.text()

        assertTrue(text.contains(infinityIcon()))
        assertTrue(text.contains(ammoIcon()))
    }
}
