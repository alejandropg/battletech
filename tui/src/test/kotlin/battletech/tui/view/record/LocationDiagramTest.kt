package battletech.tui.view.record

import battletech.tactical.model.MechLocation
import battletech.tui.aUnit
import battletech.tui.anArmorLayout
import battletech.tui.anInternalStructureLayout
import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import battletech.tui.screen.BoardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.ScreenBuffer
import tenter.view.line
import tenter.view.render
import tenter.view.text

/**
 * [LocationDiagram] via [RecordSheetDiagrams.armor]/[RecordSheetDiagrams.internalStructure] — the
 * ARMOR/STRUCTURE cards, laid out as a 5-column body grid (LA | LT | CT | RT | RA).
 */
internal class LocationDiagramTest {

    private val diagramWidth = 60

    /** (row, column) of the first `"$label "` occurrence anywhere in the buffer — a block's own label/caption. */
    private fun ScreenBuffer.locate(label: String): Pair<Int, Int> {
        for (row in 0 until height) {
            val col = line(row).indexOf("$label ")
            if (col >= 0) return row to col
        }
        error("label '$label' not found in buffer")
    }

    private fun countGlyph(text: String, glyph: String): Int = if (glyph.isEmpty()) 0 else text.split(glyph).size - 1

    @Test
    fun `undamaged location prints remaining over max and no damage pips`() {
        val unit = aUnit(armor = anArmorLayout())
        val buffer = render(RecordSheetDiagrams.armor(unit), width = diagramWidth, height = 40)

        val (row, col) = buffer.locate("CT")
        assertTrue(buffer.line(row).contains("CT 47/47"))

        // The first 10 damage pips land directly below the label, at the label's own column —
        // CT's grid slot, not spilling into LT's or RT's neighboring slot.
        val pipRow = buffer.line(row + 1, x = col, width = 10)
        assertEquals(0, countGlyph(pipRow, filledCircleIcon()))
        assertEquals(10, countGlyph(pipRow, emptyCircleIcon()))
    }

    @Test
    fun `damage taken fills one pip per point, remainder stays empty`() {
        // Damage always flows through .copy(armor = ...) in production, leaving maxArmor at
        // whatever it was set to at createUnit time — reproduce that here rather than passing
        // an already-damaged layout straight to aUnit(), which would make maxArmor damaged too.
        val unit = aUnit(armor = anArmorLayout()).copy(armor = anArmorLayout().with(MechLocation.CENTER_TORSO, 20))
        val buffer = render(RecordSheetDiagrams.armor(unit), width = diagramWidth, height = 40)

        val (row, col) = buffer.locate("CT")
        assertTrue(buffer.line(row).contains("CT 20/47"))

        // damage = 47 - 20 = 27: first two pip rows (20 pips) fully filled, third row 7 filled + 3 empty.
        val row1 = buffer.line(row + 1, x = col, width = 10)
        val row2 = buffer.line(row + 2, x = col, width = 10)
        val row3 = buffer.line(row + 3, x = col, width = 10)
        assertEquals(10, countGlyph(row1, filledCircleIcon()))
        assertEquals(10, countGlyph(row2, filledCircleIcon()))
        assertEquals(7, countGlyph(row3, filledCircleIcon()))
        assertEquals(3, countGlyph(row3, emptyCircleIcon()))
    }

    @Test
    fun `a destroyed location renders strikethrough`() {
        val unit = aUnit(
            armor = anArmorLayout(),
            internalStructure = anInternalStructureLayout(centerTorso = 0),
        )
        val diagram = RecordSheetDiagrams.internalStructure(unit)
        val buffer = render(diagram, width = diagramWidth, height = 40)

        val (row, col) = buffer.locate("CT")
        assertEquals(BoardRole.DESTROYED, buffer.get(col, row).style.fg)
        assertTrue(buffer.get(col, row).style.strikethrough)
    }

    @Test
    fun `an intact location is not struck through`() {
        val unit = aUnit(armor = anArmorLayout(), internalStructure = anInternalStructureLayout())
        val diagram = RecordSheetDiagrams.internalStructure(unit)
        val buffer = render(diagram, width = diagramWidth, height = 40)

        val (row, col) = buffer.locate("CT")
        assertTrue(!buffer.get(col, row).style.strikethrough)
    }

    @Test
    fun `rear torso facets are captioned and pipped independently of the front value`() {
        val unit = aUnit(armor = anArmorLayout())
        val buffer = render(RecordSheetDiagrams.armor(unit), width = diagramWidth, height = 40)

        assertTrue(buffer.text().contains("LT-R 10/10"))
        assertTrue(buffer.text().contains("CT-R 14/14"))
        assertTrue(buffer.text().contains("RT-R 10/10"))
    }

    @Test
    fun `arms flank the torso row instead of stacking above it`() {
        val unit = aUnit(armor = anArmorLayout())
        val buffer = render(RecordSheetDiagrams.armor(unit), width = diagramWidth, height = 40)

        val (laRow, laCol) = buffer.locate("LA")
        val (ctRow, ctCol) = buffer.locate("CT")
        val (raRow, raCol) = buffer.locate("RA")

        assertEquals(ctRow, laRow, "LA should be on the same row as CT")
        assertEquals(ctRow, raRow, "RA should be on the same row as CT")
        assertTrue(laCol < ctCol, "LA should sit left of CT")
        assertTrue(raCol > ctCol, "RA should sit right of CT")
    }

    @Test
    fun `the head sits above the torso row, centered over CT`() {
        val unit = aUnit(armor = anArmorLayout())
        val buffer = render(RecordSheetDiagrams.armor(unit), width = diagramWidth, height = 40)

        val (hdRow, hdCol) = buffer.locate("HD")
        val (ctRow, ctCol) = buffer.locate("CT")

        assertTrue(hdRow < ctRow, "HD should render above the torso row")
        assertEquals(ctCol, hdCol, "HD should share CT's column")
    }

    @Test
    fun `legs sit below the torso row, flanking center`() {
        val unit = aUnit(armor = anArmorLayout())
        val buffer = render(RecordSheetDiagrams.armor(unit), width = diagramWidth, height = 40)

        val (ctRow, _) = buffer.locate("CT")
        val (llRow, llCol) = buffer.locate("LL")
        val (rlRow, rlCol) = buffer.locate("RL")

        assertTrue(llRow > ctRow, "LL should render below the torso row")
        assertEquals(llRow, rlRow, "LL and RL should share a row")
        assertTrue(llCol < rlCol, "LL should sit left of RL")
    }
}
