package battletech.tui.view.record

import battletech.tactical.model.MechLocation
import battletech.tui.aUnit
import battletech.tui.anArmorLayout
import battletech.tui.anInternalStructureLayout
import battletech.tui.icon.emptyCircleIcon
import battletech.tui.icon.filledCircleIcon
import battletech.tui.screen.BoardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import tenter.screen.ScreenBuffer
import tenter.view.line
import tenter.view.render
import tenter.view.text
import kotlin.math.abs

/**
 * [LocationDiagram] via its [LocationDiagram.armor]/[LocationDiagram.internalStructure] factories
 * — the shared configurable paper doll used by the maximized record sheet.
 */
internal class LocationDiagramTest {

    private val diagramWidth = 80
    private val diagramHeight = 80
    private val exampleArmor = anArmorLayout(
        head = 9,
        centerTorso = 22,
        centerTorsoRear = 8,
        leftTorso = 16,
        leftTorsoRear = 5,
        rightTorso = 16,
        rightTorsoRear = 5,
        leftArm = 14,
        rightArm = 14,
        leftLeg = 18,
        rightLeg = 18,
    )

    /** (row, column) of the first caption beginning with [label]. */
    private fun ScreenBuffer.locate(label: String): Pair<Int, Int> {
        for (row in 0 until height) {
            val col = line(row).indexOf(label)
            if (col >= 0) return row to col
        }
        error("label '$label' not found in buffer")
    }

    private fun countGlyph(text: String, glyph: String): Int =
        if (glyph.isEmpty()) 0 else text.split(glyph).size - 1

    private fun assertCentered(buffer: ScreenBuffer, outline: String) {
        val (_, column) = buffer.locate(outline)
        val leftMargin = column
        val rightMargin = buffer.width - column - outline.length

        assertTrue(abs(leftMargin - rightMargin) <= 1)
    }

    private fun armorPipTotal(): Int = with(anArmorLayout()) {
        head + centerTorso + centerTorsoRear +
            leftTorso + leftTorsoRear + rightTorso + rightTorsoRear +
            leftArm + rightArm + leftLeg + rightLeg
    }

    @Test
    fun `undamaged armor renders every point as an empty pip`() {
        val unit = aUnit(armor = anArmorLayout())

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val text = buffer.text()

        assertTrue(text.contains("Head 9/9"))
        assertTrue(text.contains("47/47"))
        assertTrue(text.contains("╌╌REAR╌╌╌"))
        assertEquals(0, countGlyph(text, filledCircleIcon()))
        assertEquals(armorPipTotal(), countGlyph(text, emptyCircleIcon()))
    }

    @Test
    fun `damage fills one pip per lost armor point`() {
        val unit = aUnit(armor = anArmorLayout()).copy(
            armor = anArmorLayout().with(MechLocation.CENTER_TORSO, 20),
        )

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val text = buffer.text()

        assertTrue(text.contains("20/47"))
        assertEquals(27, countGlyph(text, filledCircleIcon()))
        assertEquals(armorPipTotal() - 27, countGlyph(text, emptyCircleIcon()))
    }

    @Test
    fun `destroyed location value and contour use destroyed styling`() {
        val unit = aUnit(
            armor = anArmorLayout(),
            internalStructure = anInternalStructureLayout(),
        ).copy(internalStructure = anInternalStructureLayout(centerTorso = 0))

        val buffer = render(LocationDiagram.internalStructure(unit), width = diagramWidth, height = diagramHeight)
        val (valueRow, valueCol) = buffer.locate("0/31")
        val contour = buffer.get(38, 7)

        assertEquals(BoardRole.DESTROYED, buffer.get(valueCol, valueRow).style.fg)
        assertTrue(buffer.get(valueCol, valueRow).style.strikethrough)
        assertEquals(BoardRole.DESTROYED, contour.style.fg)
        assertTrue(contour.style.strikethrough)
    }

    @Test
    fun `intact location is not struck through`() {
        val unit = aUnit(
            armor = anArmorLayout(),
            internalStructure = anInternalStructureLayout(),
        )

        val buffer = render(LocationDiagram.internalStructure(unit), width = diagramWidth, height = diagramHeight)
        val (row, col) = buffer.locate("31/31")

        assertFalse(buffer.get(col, row).style.strikethrough)
    }

    @Test
    fun `armor diagram honors the caller-supplied destroyed predicate`() {
        val unit = aUnit(armor = anArmorLayout())

        val buffer = render(
            LocationDiagram.armor(unit) { it == MechLocation.CENTER_TORSO },
            width = diagramWidth,
            height = diagramHeight,
        )
        val (valueRow, valueCol) = buffer.locate("47/47")

        assertEquals(BoardRole.DESTROYED, buffer.get(valueCol, valueRow).style.fg)
        assertTrue(buffer.get(valueCol, valueRow).style.strikethrough)
    }

    @Test
    fun `armor uses fixed-width boxes with rear armor embedded in the torso`() {
        val unit = aUnit(armor = exampleArmor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val text = buffer.text()
        val (headRow, _) = buffer.locate("Head 9/9")
        val (torsoRow, _) = buffer.locate("╭──────╮╭─────────╮╭──────╮")
        val (rearRow, _) = buffer.locate("│╌╌╌╌││╌╌REAR╌╌╌││╌╌╌╌│")
        val (legRow, _) = buffer.locate("Leg")

        assertTrue(headRow < torsoRow)
        assertTrue(torsoRow < rearRow)
        assertTrue(rearRow < legRow)
        assertFalse(text.contains("REAR ARMOR"))
        assertFalse(text.contains("Torso Rear"))
        assertEquals(145, countGlyph(text, emptyCircleIcon()))
    }

    @Test
    fun `armor silhouette is centered in its record sheet section`() {
        val unit = aUnit(armor = exampleArmor)

        val buffer = render(
            LocationDiagram.armor(unit) { false },
            width = SheetLayout.ARMOR_DIAGRAM_WIDTH,
            height = diagramHeight,
        )

        assertCentered(buffer, "╭──────╮╭─────────╮╭──────╮")
    }

    @Test
    fun `internal structure silhouette is centered in its record sheet section`() {
        val unit = aUnit(internalStructure = anInternalStructureLayout())

        val buffer = render(
            LocationDiagram.internalStructure(unit),
            width = SheetLayout.INTERNAL_STRUCTURE_DIAGRAM_WIDTH,
            height = diagramHeight,
        )

        assertCentered(buffer, "╭────╮╭─────╮╭────╮")
    }

    @Test
    fun `armor side labels flank the final head row before the torso heading`() {
        val unit = aUnit(armor = exampleArmor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val (headBottomRow, _) = buffer.locate("╰─────╯")
        val (leftRow, leftCol) = buffer.locate("Left")
        val (rightRow, _) = buffer.locate("Right")
        val (torsoHeadingRow, _) = buffer.locate("Torso")

        assertEquals(headBottomRow - 1, leftRow)
        assertEquals(headBottomRow - 1, rightRow)
        assertEquals(23, leftCol)
        assertEquals("Left          │", buffer.line(leftRow, x = leftCol, width = 15))
        assertEquals("│          Right", buffer.line(rightRow, x = 43, width = 16))
        assertEquals(headBottomRow + 1, torsoHeadingRow)
    }

    @Test
    fun `left torso values align one column inside their outer box borders`() {
        val unit = aUnit()

        val armor = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val internal = render(LocationDiagram.internalStructure(unit), width = diagramWidth, height = diagramHeight)
        val (_, armorBorderColumn) = armor.locate("╭──────╮╭─────────╮╭──────╮")
        val (_, internalBorderColumn) = internal.locate("╭────╮╭─────╮╭────╮")
        val (_, armorValueColumn) = armor.locate("32/32")
        val (_, internalValueColumn) = internal.locate("21/21")

        assertEquals(armorBorderColumn + 1, armorValueColumn)
        assertEquals(internalBorderColumn + 1, internalValueColumn)
    }

    @Test
    fun `right torso values align one column inside their outer box borders`() {
        val unit = aUnit(armor = exampleArmor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val (frontValueRow, _) = buffer.locate("16/16")
        val (rearValueRow, _) = buffer.locate("5/5")

        assertEquals("16/16", buffer.line(frontValueRow, x = 48, width = 5))
        assertEquals("╮", buffer.get(53, frontValueRow + 1).char)
        assertEquals("5/5", buffer.line(rearValueRow, x = 48, width = 3))
        assertEquals("╯", buffer.get(51, rearValueRow - 1).char)
    }

    @ParameterizedTest
    @ValueSource(ints = [4, 10])
    fun `left rear torso value keeps one column inside its box border`(rearMax: Int) {
        val armor = anArmorLayout(
            centerTorsoRear = 8,
            leftTorsoRear = rearMax,
            rightTorsoRear = 5,
        )
        val unit = aUnit(armor = armor)
        val rearValue = "$rearMax/$rearMax"

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val (rearValueRow, _) = buffer.locate(rearValue)

        assertEquals(" $rearValue", buffer.line(rearValueRow, x = 29, width = rearValue.length + 1))
        assertEquals("╰", buffer.get(29, rearValueRow - 1).char)
    }

    @Test
    fun `armor legs begin immediately after rear values even when the arms extend lower`() {
        val armor = anArmorLayout(
            centerTorso = 18,
            centerTorsoRear = 8,
            leftTorso = 12,
            leftTorsoRear = 4,
            rightTorso = 12,
            rightTorsoRear = 4,
            leftArm = 14,
            rightArm = 14,
            leftLeg = 4,
            rightLeg = 4,
        )
        val unit = aUnit(armor = armor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val (rearValueRow, _) = buffer.locate("8/8")

        assertEquals("╭───╮", buffer.line(rearValueRow + 1, x = 33, width = 5))
        assertEquals("╭───╮", buffer.line(rearValueRow + 1, x = 43, width = 5))
    }

    @ParameterizedTest
    @CsvSource(
        "16, 3",
        "17, 4",
        "23, 4",
        "24, 5",
    )
    fun `armor leg width follows maximum armor`(legMax: Int, expectedWidth: Int) {
        val armor = anArmorLayout(
            centerTorso = 18,
            centerTorsoRear = 8,
            leftTorso = 12,
            leftTorsoRear = 4,
            rightTorso = 12,
            rightTorsoRear = 4,
            leftArm = 4,
            rightArm = 4,
            leftLeg = legMax,
            rightLeg = legMax,
        )
        val unit = aUnit(armor = armor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val (rearValueRow, _) = buffer.locate("8/8")
        val legTop = rearValueRow + 1
        val leftLegX = 37 - expectedWidth - 1
        val legRows = (legMax + expectedWidth - 1) / expectedWidth
        val finalRowPips = legMax - (legRows - 1) * expectedWidth
        val expectedTop = "╭" + "─".repeat(expectedWidth) + "╮"
        val expectedFinalRow = "│" + emptyCircleIcon().repeat(finalRowPips) +
            " ".repeat(expectedWidth - finalRowPips) + "│"
        val footWidth = expectedWidth + 2
        val expectedFoot = "╰" + "─".repeat(footWidth) + "╯"
        val footRow = legTop + legRows + 2

        assertEquals(expectedTop, buffer.line(legTop, x = leftLegX, width = expectedWidth + 2))
        assertEquals(expectedTop, buffer.line(legTop, x = 43, width = expectedWidth + 2))
        assertEquals(expectedFinalRow, buffer.line(legTop + legRows, x = leftLegX, width = expectedWidth + 2))
        assertEquals(expectedFinalRow, buffer.line(legTop + legRows, x = 43, width = expectedWidth + 2))
        assertEquals(expectedFoot, buffer.line(footRow, x = leftLegX - 2, width = footWidth + 2))
        assertEquals(expectedFoot, buffer.line(footRow, x = 43, width = footWidth + 2))
    }

    @Test
    fun `unequal armor legs retain their inner gap and caption spacing`() {
        val armor = anArmorLayout(
            centerTorso = 18,
            centerTorsoRear = 8,
            leftTorso = 12,
            leftTorsoRear = 4,
            rightTorso = 12,
            rightTorsoRear = 4,
            leftArm = 4,
            rightArm = 4,
            leftLeg = 16,
            rightLeg = 24,
        )
        val unit = aUnit(armor = armor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val (rearValueRow, _) = buffer.locate("8/8")
        val legTop = rearValueRow + 1

        assertEquals("╮     ╭", buffer.line(legTop, x = 37, width = 7))
        assertEquals("Leg", buffer.line(legTop + 3, x = 29, width = 3))
        assertEquals("Leg", buffer.line(legTop + 2, x = 51, width = 3))
    }

    @Test
    fun `side tapers and feet start on the row after their final circles`() {
        val unit = aUnit(armor = exampleArmor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)

        assertEquals("╲", buffer.get(28, 12).char)
        assertEquals("╱", buffer.get(52, 12).char)
        assertEquals("╱", buffer.get(24, 15).char)
        assertEquals("╲", buffer.get(56, 15).char)
        assertEquals("╱", buffer.get(31, 24).char)
        assertEquals("╲", buffer.get(49, 24).char)
    }

    @Test
    fun `center torso is never shorter than either side torso`() {
        val unevenArmor = anArmorLayout(
            centerTorso = 9,
            centerTorsoRear = 1,
            leftTorso = 18,
            leftTorsoRear = 8,
            rightTorso = 6,
            rightTorsoRear = 4,
            leftArm = 4,
            rightArm = 4,
            leftLeg = 4,
            rightLeg = 4,
        )
        val unit = aUnit(armor = unevenArmor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val (torsoTop, _) = buffer.locate("╭──────╮╭─────────╮╭──────╮")

        assertEquals("╰────╯", buffer.line(torsoTop + 8, x = 29, width = 6))
        assertEquals("╰─────────╯", buffer.line(torsoTop + 8, x = 35, width = 11))
        assertEquals("╰────╯", buffer.line(torsoTop + 5, x = 46, width = 6))
    }

    @Test
    fun `internal structure uses configured widths without rear tracks`() {
        val unit = aUnit(
            armor = anArmorLayout(),
            internalStructure = anInternalStructureLayout(),
        )

        val internal = render(LocationDiagram.internalStructure(unit), width = diagramWidth, height = diagramHeight)
        val text = internal.text()
        val pip = emptyCircleIcon()

        assertTrue(text.contains("Head 3/3"))
        assertTrue(text.contains("31/31"))
        assertFalse(text.contains("REAR"))
        assertEquals("╭───╮", internal.line(2, x = 38, width = 5))
        assertEquals("│${pip.repeat(3)}│", internal.line(3, x = 38, width = 5))
        assertEquals("╭────╮╭─────╮╭────╮", internal.line(7, x = 31, width = 19))
        assertEquals(
            "│${pip.repeat(4)}││${pip.repeat(5)}││${pip.repeat(4)}│",
            internal.line(8, x = 31, width = 19),
        )
        assertEquals("│$pip   │", internal.line(13, x = 31, width = 6))
        assertEquals("│$pip    │", internal.line(14, x = 37, width = 7))
        assertEquals("╭───╮", internal.line(9, x = 25, width = 5))
        assertEquals("│${pip.repeat(3)}│", internal.line(10, x = 25, width = 5))
        assertEquals("│${pip.repeat(2)} │", internal.line(15, x = 25, width = 5))
        assertEquals("╭────╮", internal.line(17, x = 32, width = 6))
        assertEquals("│${pip.repeat(4)}│", internal.line(18, x = 32, width = 6))
        assertEquals("│$pip   │", internal.line(23, x = 32, width = 6))
        assertEquals(152, countGlyph(text, pip))
    }

    @Test
    fun `internal structure damage fills shared diagram pips`() {
        val unit = aUnit(
            armor = anArmorLayout(),
            internalStructure = anInternalStructureLayout(),
        ).copy(internalStructure = anInternalStructureLayout(centerTorso = 29))

        val internal = render(LocationDiagram.internalStructure(unit), width = diagramWidth, height = diagramHeight)
        val text = internal.text()

        assertEquals(2, countGlyph(text, filledCircleIcon()))
        assertEquals(150, countGlyph(text, emptyCircleIcon()))
    }
}
