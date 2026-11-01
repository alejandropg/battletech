package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tactical.model.MechLocation
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.unit.MechModels
import io.archinaut.battletech.tactical.unit.UnitId
import io.archinaut.battletech.tactical.unit.createUnit
import io.archinaut.battletech.tui.screen.BoardRole
import io.archinaut.battletech.tui.view.render
import io.archinaut.tenter.color.StandardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.screen.Point

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
        val buffer = render(CriticalHitTable(atlas()), width = SheetLayout.SHEET_WIDTH, height = 60)
        val text = buffer.text()

        assertTrue(text.contains("CRITICAL HIT TABLE"))
        assertTrue(text.contains("AC/20"))
    }

    @Test
    fun `a destroyed slot is struck through, an intact one is not`() {
        val unit = atlas().copy(criticalHits = mapOf(MechLocation.RIGHT_TORSO to setOf(0)))
        val buffer = render(CriticalHitTable(unit), width = SheetLayout.SHEET_WIDTH, height = 60)

        val (col, headerRow) = buffer.find("RIGHT TORSO")!!

        // Slot 0 (line "1. AC/20", right under the header) was destroyed above.
        assertEquals(BoardRole.DESTROYED, buffer.get(col, headerRow + 1).style.fg)
        assertTrue(buffer.get(col, headerRow + 1).style.strikethrough == true)
        // Slot 1 ("2. AC/20") was not.
        assertEquals(StandardRole.TEXT_PRIMARY, buffer.get(col, headerRow + 2).style.fg)
        assertTrue(buffer.get(col, headerRow + 2).style.strikethrough != true)
    }

    @Test
    fun `lays out locations and system damage in five thirty-column lanes`() {
        val buffer = render(CriticalHitTable(atlas()), width = SheetLayout.SHEET_WIDTH, height = 60)

        val criticalRow = buffer.find("CRITICAL HIT TABLE")!!.y
        val topLocationRow = criticalRow + 1
        assertEquals(Point(0, topLocationRow), buffer.find("LEFT ARM"))
        assertEquals(Point(30, topLocationRow), buffer.find("LEFT TORSO"))
        assertEquals(Point(60, topLocationRow), buffer.find("HEAD"))
        assertEquals(Point(90, topLocationRow), buffer.find("RIGHT TORSO"))
        assertEquals(Point(120, topLocationRow), buffer.find("RIGHT ARM"))

        assertEquals(Point(60, criticalRow + 9), buffer.find("CENTER TORSO"))
        assertEquals(Point(30, criticalRow + 15), buffer.find("LEFT LEG"))
        assertEquals(Point(90, criticalRow + 15), buffer.find("RIGHT LEG"))
        assertEquals("── SYSTEM DAMAGE ───", buffer.region(Rect(0, criticalRow + 17, SheetLayout.SYSTEM_DAMAGE_WIDTH, 1)).line(0))
    }

    @Test
    fun `empty slots leave the slot content blank`() {
        val buffer = render(CriticalHitTable(atlas()), width = SheetLayout.SHEET_WIDTH, height = 60)

        val headRow = buffer.find("HEAD")!!.y
        val emptyHeadSlot = buffer.region(Rect(60, headRow + 4, SheetLayout.CRIT_COLUMN_WIDTH, 1)).line(0)

        assertEquals(" 4.", emptyHeadSlot.trimEnd())
    }

    @Test
    fun `wraps complete critical lanes on narrower canvases`() {
        val buffer = render(CriticalHitTable(atlas()), width = 90, height = 80)

        val firstBandRow = buffer.find("LEFT ARM")!!.y
        val secondBandRow = (firstBandRow + 1 until buffer.height).first { buffer.line(it).contains("RIGHT TORSO") }

        assertEquals(Point(0, firstBandRow), buffer.find("LEFT ARM"))
        assertEquals(Point(30, firstBandRow), buffer.find("LEFT TORSO"))
        assertEquals(Point(60, firstBandRow), buffer.find("HEAD"))
        assertEquals(Point(0, secondBandRow), buffer.find("RIGHT TORSO"))
        assertEquals(Point(30, secondBandRow), buffer.find("RIGHT ARM"))
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
