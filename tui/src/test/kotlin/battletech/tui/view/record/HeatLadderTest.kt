package battletech.tui.view.record

import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.HeatSource
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.screen.HeatScaleRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.screen.ColorRole
import tenter.screen.ScreenBuffer
import tenter.view.line
import tenter.view.render

/** [HeatLadder] — the current/projected heat gauges plus the 30-to-0 heat scale rungs. */
internal class HeatLadderTest {

    private fun rowForHeat(headerRow: Int, heat: Int): Int = headerRow + 1 + (30 - heat)

    private fun assertRowBackground(buffer: ScreenBuffer, row: Int, expected: ColorRole) {
        for (column in 0 until buffer.width) {
            assertEquals(expected, buffer.get(column, row).style.bg, "column $column on row $row")
        }
    }

    @Test
    fun `marks current heat solid and a different projected heat as draft`() {
        // heat sink STS 10 -> dissipation 10, so heat 9 (no pending/committed sources) projects
        // straight down to 0: two distinct rungs to mark, one current, one projected.
        val unit = aUnit(currentHeat = 9)
        val buffer = render(HeatLadder(unit, aGameMap(), emptyList()), width = 40, height = 60)

        val headerRow = (0 until buffer.height).first { buffer.line(it).contains("HEAT SCALE") }

        val currentRow = rowForHeat(headerRow, 9)
        assertTrue(buffer.line(currentRow).contains("◀"))
        assertEquals(ChromeRole.TEXT_PRIMARY, buffer.get(0, currentRow).style.fg)
        assertRowBackground(buffer, currentRow, HeatScaleRole.CURRENT_BG)

        val projectedRow = rowForHeat(headerRow, 0)
        assertTrue(buffer.line(projectedRow).contains("▶"))
        assertEquals(ChromeRole.DRAFT, buffer.get(0, projectedRow).style.fg)
        assertRowBackground(buffer, projectedRow, HeatScaleRole.COOLING_BG)
        assertRowBackground(buffer, rowForHeat(headerRow, 5), HeatScaleRole.COOLING_BG)
        assertEquals(ChromeRole.TEXT_MUTED, buffer.get(0, rowForHeat(headerRow, 5)).style.fg)
        assertRowBackground(buffer, rowForHeat(headerRow, 10), ChromeRole.DEFAULT)
    }

    @Test
    fun `highlights a projected heat increase through its endpoint`() {
        val unit = aUnit(
            currentHeat = 5,
            heatSink = HeatSink(HeatSinkType.STS, 0),
        )
        val buffer = render(
            HeatLadder(unit, aGameMap(), listOf(HeatSource("Weapons", 5))),
            width = 40,
            height = 60,
        )

        val headerRow = (0 until buffer.height).first { buffer.line(it).contains("HEAT SCALE") }

        assertRowBackground(buffer, rowForHeat(headerRow, 5), HeatScaleRole.CURRENT_BG)
        for (heat in 6..10) {
            assertRowBackground(buffer, rowForHeat(headerRow, heat), HeatScaleRole.HEATING_BG)
        }
        assertEquals(ChromeRole.TEXT_MUTED, buffer.get(0, rowForHeat(headerRow, 7)).style.fg)
        assertEquals(ChromeRole.DRAFT, buffer.get(0, rowForHeat(headerRow, 10)).style.fg)
        assertRowBackground(buffer, rowForHeat(headerRow, 4), ChromeRole.DEFAULT)
        assertRowBackground(buffer, rowForHeat(headerRow, 11), ChromeRole.DEFAULT)
    }

    @Test
    fun `uses only the current background when projected heat is unchanged`() {
        val unit = aUnit(
            currentHeat = 7,
            heatSink = HeatSink(HeatSinkType.STS, 0),
        )
        val buffer = render(HeatLadder(unit, aGameMap(), emptyList()), width = 40, height = 60)

        val headerRow = (0 until buffer.height).first { buffer.line(it).contains("HEAT SCALE") }
        val currentRow = rowForHeat(headerRow, 7)

        assertTrue(buffer.line(currentRow).contains("◀▶"))
        assertRowBackground(buffer, currentRow, HeatScaleRole.CURRENT_BG)
        assertRowBackground(buffer, rowForHeat(headerRow, 6), ChromeRole.DEFAULT)
        assertRowBackground(buffer, rowForHeat(headerRow, 8), ChromeRole.DEFAULT)
    }

    @Test
    fun `clips a projected heat increase to the visible scale`() {
        val unit = aUnit(
            currentHeat = 28,
            heatSink = HeatSink(HeatSinkType.STS, 0),
        )
        val buffer = render(
            HeatLadder(unit, aGameMap(), listOf(HeatSource("Weapons", 5))),
            width = 40,
            height = 60,
        )

        val headerRow = (0 until buffer.height).first { buffer.line(it).contains("HEAT SCALE") }

        assertRowBackground(buffer, rowForHeat(headerRow, 28), HeatScaleRole.CURRENT_BG)
        assertRowBackground(buffer, rowForHeat(headerRow, 29), HeatScaleRole.HEATING_BG)
        assertRowBackground(buffer, rowForHeat(headerRow, 30), HeatScaleRole.HEATING_BG)
        assertRowBackground(buffer, rowForHeat(headerRow, 27), ChromeRole.DEFAULT)
    }

    @Test
    fun `a rule's text prints only on the rung where it first applies`() {
        val unit = aUnit(currentHeat = 0)
        val buffer = render(HeatLadder(unit, aGameMap(), emptyList()), width = 40, height = 60)

        val headerRow = (0 until buffer.height).first { buffer.line(it).contains("HEAT SCALE") }
        // Movement penalty first kicks in at heat 5 (-1 MP); heat 6, 7 repeat the same rule
        // and must NOT restate it.
        assertTrue(buffer.line(rowForHeat(headerRow, 5)).contains("MP"))
        assertTrue(!buffer.line(rowForHeat(headerRow, 6)).contains("MP"))
        assertTrue(!buffer.line(rowForHeat(headerRow, 7)).contains("MP"))
    }
}
