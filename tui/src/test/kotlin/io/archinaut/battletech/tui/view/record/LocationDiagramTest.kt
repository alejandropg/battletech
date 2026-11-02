package io.archinaut.battletech.tui.view.record

import io.archinaut.battletech.tactical.model.MechLocation
import io.archinaut.battletech.tui.aUnit
import io.archinaut.battletech.tui.anArmorLayout
import io.archinaut.battletech.tui.anInternalStructureLayout
import io.archinaut.battletech.tui.icon.emptyCircleIcon
import io.archinaut.battletech.tui.icon.filledCircleIcon
import io.archinaut.battletech.tui.screen.BoardRole
import io.archinaut.battletech.tui.view.render
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.terminal.CompletedFrame

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

    private fun countGlyph(text: String, glyph: String): Int =
        if (glyph.isEmpty()) 0 else text.split(glyph).size - 1

    private fun assertCentered(buffer: CompletedFrame, outline: String) {
        val (column, _) = buffer.find(outline)!!
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
        val (valueCol, valueRow) = buffer.find("0/31")!!
        val contour = buffer.get(38, 7)

        assertEquals(BoardRole.DESTROYED, buffer.get(valueCol, valueRow).fg)
        assertTrue(buffer.get(valueCol, valueRow).strikethrough)
        assertEquals(BoardRole.DESTROYED, contour.fg)
        assertTrue(contour.strikethrough)
    }

    @Test
    fun `intact location is not struck through`() {
        val unit = aUnit(
            armor = anArmorLayout(),
            internalStructure = anInternalStructureLayout(),
        )

        val buffer = render(LocationDiagram.internalStructure(unit), width = diagramWidth, height = diagramHeight)
        val (col, row) = buffer.find("31/31")!!

        assertFalse(buffer.get(col, row).strikethrough)
    }

    @Test
    fun `armor diagram honors the caller-supplied destroyed predicate`() {
        val unit = aUnit(armor = anArmorLayout())

        val buffer = render(
            LocationDiagram.armor(unit) { it == MechLocation.CENTER_TORSO },
            width = diagramWidth,
            height = diagramHeight,
        )
        val (valueCol, valueRow) = buffer.find("47/47")!!

        assertEquals(BoardRole.DESTROYED, buffer.get(valueCol, valueRow).fg)
        assertTrue(buffer.get(valueCol, valueRow).strikethrough)
    }

    @Test
    fun `armor uses fixed-width boxes with rear armor embedded in the torso`() {
        val unit = aUnit(armor = exampleArmor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val text = buffer.text()
        val (_, headRow) = buffer.find("Head 9/9")!!
        val (_, torsoRow) = buffer.find("╭──────╮╭─────────╮╭──────╮")!!
        val (_, rearRow) = buffer.find("│╌╌╌╌││╌╌REAR╌╌╌││╌╌╌╌│")!!
        val (_, legRow) = buffer.find("Leg")!!

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
    fun `armor side labels flank the first head row before the torso heading`() {
        val unit = aUnit(armor = exampleArmor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val (_, headTopRow) = buffer.find("╭─────╮")!!
        val (_, headBottomRow) = buffer.find("╰─────╯")!!
        val (leftCol, leftRow) = buffer.find("Left")!!
        val (_, rightRow) = buffer.find("Right")!!
        val (_, torsoHeadingRow) = buffer.find("Torso")!!

        assertEquals(headTopRow + 1, leftRow)
        assertEquals(headTopRow + 1, rightRow)
        assertEquals(23, leftCol)
        assertEquals("Left          │", buffer.region(Rect(leftCol, leftRow, 15, 1)).line(0))
        assertEquals("│          Right", buffer.region(Rect(43, rightRow, 16, 1)).line(0))
        assertEquals(headBottomRow + 1, torsoHeadingRow)
    }

    @Test
    fun `left torso values align one column inside their outer box borders`() {
        val unit = aUnit()

        val armor = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val internal = render(LocationDiagram.internalStructure(unit), width = diagramWidth, height = diagramHeight)
        val (armorBorderColumn, _) = armor.find("╭──────╮╭─────────╮╭──────╮")!!
        val (internalBorderColumn, _) = internal.find("╭────╮╭─────╮╭────╮")!!
        val (armorValueColumn, _) = armor.find("32/32")!!
        val (internalValueColumn, _) = internal.find("21/21")!!

        assertEquals(armorBorderColumn + 1, armorValueColumn)
        assertEquals(internalBorderColumn + 1, internalValueColumn)
    }

    @Test
    fun `right torso values align one column inside their outer box borders`() {
        val unit = aUnit(armor = exampleArmor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)
        val (_, frontValueRow) = buffer.find("16/16")!!
        val (_, rearValueRow) = buffer.find("5/5")!!

        assertEquals("16/16", buffer.region(Rect(48, frontValueRow, 5, 1)).line(0))
        assertEquals("╮", buffer.get(53, frontValueRow + 1).symbol)
        assertEquals("5/5", buffer.region(Rect(48, rearValueRow, 3, 1)).line(0))
        assertEquals("╯", buffer.get(51, rearValueRow - 1).symbol)
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
        val (_, rearValueRow) = buffer.find(rearValue)!!

        assertEquals(" $rearValue", buffer.region(Rect(29, rearValueRow, rearValue.length + 1, 1)).line(0))
        assertEquals("╰", buffer.get(29, rearValueRow - 1).symbol)
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
        val (_, rearValueRow) = buffer.find("8/8")!!

        assertEquals("╭───╮", buffer.region(Rect(33, rearValueRow + 1, 5, 1)).line(0))
        assertEquals("╭───╮", buffer.region(Rect(43, rearValueRow + 1, 5, 1)).line(0))
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
        val (_, rearValueRow) = buffer.find("8/8")!!
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

        assertEquals(expectedTop, buffer.region(Rect(leftLegX, legTop, expectedWidth + 2, 1)).line(0))
        assertEquals(expectedTop, buffer.region(Rect(43, legTop, expectedWidth + 2, 1)).line(0))
        assertEquals(expectedFinalRow, buffer.region(Rect(leftLegX, legTop + legRows, expectedWidth + 2, 1)).line(0))
        assertEquals(expectedFinalRow, buffer.region(Rect(43, legTop + legRows, expectedWidth + 2, 1)).line(0))
        assertEquals(expectedFoot, buffer.region(Rect(leftLegX - 2, footRow, footWidth + 2, 1)).line(0))
        assertEquals(expectedFoot, buffer.region(Rect(43, footRow, footWidth + 2, 1)).line(0))
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
        val (_, rearValueRow) = buffer.find("8/8")!!
        val legTop = rearValueRow + 1

        assertEquals("╮     ╭", buffer.region(Rect(37, legTop, 7, 1)).line(0))
        assertEquals("Leg", buffer.region(Rect(29, legTop + 3, 3, 1)).line(0))
        assertEquals("Leg", buffer.region(Rect(51, legTop + 2, 3, 1)).line(0))
    }

    @Test
    fun `side tapers and feet start on the row after their final circles`() {
        val unit = aUnit(armor = exampleArmor)

        val buffer = render(LocationDiagram.armor(unit) { false }, width = diagramWidth, height = diagramHeight)

        assertEquals("╲", buffer.get(28, 12).symbol)
        assertEquals("╱", buffer.get(52, 12).symbol)
        assertEquals("╱", buffer.get(24, 15).symbol)
        assertEquals("╲", buffer.get(56, 15).symbol)
        assertEquals("╱", buffer.get(31, 24).symbol)
        assertEquals("╲", buffer.get(49, 24).symbol)
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
        val (_, torsoTop) = buffer.find("╭──────╮╭─────────╮╭──────╮")!!

        assertEquals("╰────╯", buffer.region(Rect(29, torsoTop + 8, 6, 1)).line(0))
        assertEquals("╰─────────╯", buffer.region(Rect(35, torsoTop + 8, 11, 1)).line(0))
        assertEquals("╰────╯", buffer.region(Rect(46, torsoTop + 5, 6, 1)).line(0))
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
        assertEquals("╭───╮", internal.region(Rect(38, 2, 5, 1)).line(0))
        assertEquals("│${pip.repeat(3)}│", internal.region(Rect(38, 3, 5, 1)).line(0))
        assertEquals("╭────╮╭─────╮╭────╮", internal.region(Rect(31, 7, 19, 1)).line(0))
        assertEquals(
            "│${pip.repeat(4)}││${pip.repeat(5)}││${pip.repeat(4)}│",
            internal.region(Rect(31, 8, 19, 1)).line(0),
        )
        assertEquals("│$pip   │", internal.region(Rect(31, 13, 6, 1)).line(0))
        assertEquals("│$pip    │", internal.region(Rect(37, 14, 7, 1)).line(0))
        assertEquals("╭───╮", internal.region(Rect(25, 9, 5, 1)).line(0))
        assertEquals("│${pip.repeat(3)}│", internal.region(Rect(25, 10, 5, 1)).line(0))
        assertEquals("│${pip.repeat(2)} │", internal.region(Rect(25, 15, 5, 1)).line(0))
        assertEquals("╭────╮", internal.region(Rect(32, 17, 6, 1)).line(0))
        assertEquals("│${pip.repeat(4)}│", internal.region(Rect(32, 18, 6, 1)).line(0))
        assertEquals("│$pip   │", internal.region(Rect(32, 23, 6, 1)).line(0))
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
