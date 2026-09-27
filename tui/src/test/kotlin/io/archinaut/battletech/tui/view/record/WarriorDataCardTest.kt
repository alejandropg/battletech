package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.unit.PILOT_DEATH_THRESHOLD
import io.archinaut.battletech.tui.aUnit
import io.archinaut.battletech.tui.icon.emptyCircleIcon
import io.archinaut.battletech.tui.icon.filledCircleIcon
import io.archinaut.battletech.tui.icon.pilotDeadIcon
import io.archinaut.battletech.tui.view.line
import io.archinaut.battletech.tui.view.render
import io.archinaut.battletech.tui.view.text
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [WarriorDataCard] — gunnery/piloting, the Hits Taken track, and the Consciousness# row. */
internal class WarriorDataCardTest {

    private fun countGlyph(text: String, glyph: String) = text.split(glyph).size - 1

    @Test
    fun `undamaged pilot shows six empty hit boxes`() {
        val unit = aUnit()
        val buffer = render(WarriorDataCard(unit), width = 36, height = 10)
        val text = buffer.text()

        assertEquals(0, countGlyph(text, filledCircleIcon()))
        assertEquals(PILOT_DEATH_THRESHOLD, countGlyph(text, emptyCircleIcon()))
    }

    @Test
    fun `the sixth hit renders a skull instead of a filled dot`() {
        val unit = aUnit().copy(pilotHits = PILOT_DEATH_THRESHOLD)
        val buffer = render(WarriorDataCard(unit), width = 36, height = 10)
        val text = buffer.text()

        assertEquals(1, countGlyph(text, pilotDeadIcon()))
        assertEquals(PILOT_DEATH_THRESHOLD - 1, countGlyph(text, filledCircleIcon()))
        assertEquals(0, countGlyph(text, emptyCircleIcon()))
    }

    @Test
    fun `prints the consciousness target row without restating the rule table`() {
        val buffer = render(WarriorDataCard(aUnit()), width = 36, height = 10)
        val labelRow = (0 until buffer.height).first { buffer.line(it).contains("Consciousness#") }
        val valuesRow = buffer.line(labelRow + 1)

        for (target in listOf("3", "5", "7", "10", "11", "Dead")) {
            assertTrue(valuesRow.contains(target), "expected consciousness row to contain '$target': $valuesRow")
        }
    }
}
