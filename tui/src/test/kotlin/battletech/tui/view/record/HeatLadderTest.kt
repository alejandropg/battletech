package battletech.tui.view.record

import battletech.tui.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.view.line
import tenter.view.render

/** [HeatLadder] — the current/projected heat gauges plus the 30-to-0 heat scale rungs. */
internal class HeatLadderTest {

    private fun rowForHeat(headerRow: Int, heat: Int): Int = headerRow + 1 + (30 - heat)

    @Test
    fun `marks current heat solid and a different projected heat as draft`() {
        // heat sink STS 10 -> dissipation 10, so heat 9 (no pending/committed sources) projects
        // straight down to 0: two distinct rungs to mark, one current, one projected.
        val unit = aUnit(currentHeat = 9)
        val buffer = render(HeatLadder(unit, emptyList()), width = 40, height = 60)

        val headerRow = (0 until buffer.height).first { buffer.line(it).contains("HEAT SCALE") }

        val currentRow = rowForHeat(headerRow, 9)
        assertTrue(buffer.line(currentRow).contains("◀"))
        assertEquals(ChromeRole.TEXT_PRIMARY, buffer.get(0, currentRow).style.fg)

        val projectedRow = rowForHeat(headerRow, 0)
        assertTrue(buffer.line(projectedRow).contains("▶"))
        assertEquals(ChromeRole.DRAFT, buffer.get(0, projectedRow).style.fg)
    }

    @Test
    fun `a rule's text prints only on the rung where it first applies`() {
        val unit = aUnit(currentHeat = 0)
        val buffer = render(HeatLadder(unit, emptyList()), width = 40, height = 60)

        val headerRow = (0 until buffer.height).first { buffer.line(it).contains("HEAT SCALE") }
        // Movement penalty first kicks in at heat 5 (-1 MP); heat 6, 7 repeat the same rule
        // and must NOT restate it.
        assertTrue(buffer.line(rowForHeat(headerRow, 5)).contains("MP"))
        assertTrue(!buffer.line(rowForHeat(headerRow, 6)).contains("MP"))
        assertTrue(!buffer.line(rowForHeat(headerRow, 7)).contains("MP"))
    }
}
