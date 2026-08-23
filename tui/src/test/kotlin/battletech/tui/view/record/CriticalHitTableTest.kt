package battletech.tui.view.record

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.MechModels
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.createUnit
import battletech.tui.screen.BoardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.view.line
import tenter.view.render
import tenter.view.text

/** [CriticalHitTable], driven by a real Atlas AS7-D built through the production [MechModels] path. */
internal class CriticalHitTableTest {

    private fun atlas() = MechModels["AS7-D"].createUnit(
        id = UnitId("A1"),
        owner = PlayerId.PLAYER_1,
        position = HexCoordinates(0, 0),
        facing = HexDirection.N,
    )

    @Test
    fun `lists real slot contents by weapon name`() {
        val buffer = render(CriticalHitTable(atlas()), width = 400, height = 60)
        val text = buffer.text()

        assertTrue(text.contains("CRITICAL HIT TABLE"))
        assertTrue(text.contains("AC/20"))
    }

    @Test
    fun `a destroyed slot is struck through, an intact one is not`() {
        val unit = atlas().copy(criticalHits = mapOf(MechLocation.RIGHT_TORSO to setOf(0)))
        val buffer = render(CriticalHitTable(unit), width = 400, height = 60)

        val headerRow = (0 until buffer.height).first { buffer.line(it).contains("RIGHT TORSO") }
        val col = buffer.line(headerRow).indexOf("RIGHT TORSO")

        // Slot 0 (line "1. AC/20", right under the header) was destroyed above.
        assertEquals(BoardRole.DESTROYED, buffer.get(col, headerRow + 1).style.fg)
        assertTrue(buffer.get(col, headerRow + 1).style.strikethrough)
        // Slot 1 ("2. AC/20") was not.
        assertEquals(ChromeRole.TEXT_PRIMARY, buffer.get(col, headerRow + 2).style.fg)
        assertTrue(!buffer.get(col, headerRow + 2).style.strikethrough)
    }

    @Test
    fun `system damage lists engine gyro sensor and life support`() {
        val buffer = render(SystemDamageTable(atlas()), width = 400, height = 60)
        val text = buffer.text()

        assertTrue(text.contains("SYSTEM DAMAGE"))
        assertTrue(text.contains("Sensor"))
        assertTrue(text.contains("Life Support"))
    }
}
