package battletech.tui.setup

import battletech.tactical.unit.MechModels
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.view.line
import tenter.view.render
import tenter.view.text
import tenter.widget.CheckState
import tenter.widget.checkboxIcon

internal class UnitListViewTest {

    @Test
    fun `plain mode with no variants shows the empty message`() {
        val buffer = render(
            UnitListView(variants = emptyList(), counts = { 0 }, cursorIndex = 0),
            width = 30,
            height = 4,
        )

        assertEquals("No mechs registered", buffer.line(0))
    }

    @Test
    fun `plain mode renders variant and right-aligned count with no header`() {
        val buffer = render(
            UnitListView(
                variants = listOf("AS7-D", "WHM-6R"),
                counts = { variant -> if (variant == "WHM-6R") 12 else 0 },
                cursorIndex = 0,
            ),
            width = 30,
            height = 4,
        )

        assertTrue(buffer.line(0).contains("AS7-D"))
        assertTrue(buffer.line(1).endsWith("12"))
        assertFalse(buffer.text().contains("TON"))
    }

    @Test
    fun `stats mode with no variants shows the empty message and no header`() {
        val buffer = render(
            UnitListView(variants = emptyList(), counts = { 0 }, cursorIndex = 0, mechFor = { null }),
            width = 30,
            height = 4,
        )

        assertEquals("No mechs registered", buffer.line(0))
    }

    @Test
    fun `stats mode draws a header that stays aligned with the stat columns, with the count next to the label`() {
        val as7d = MechModels["AS7-D"] // tonnage 100, walk 3, run 5, jump 0
        val whm6r = MechModels["WHM-6R"] // tonnage 70, walk 4, run 6, jump 0
        val models = mapOf(as7d.variant to as7d, whm6r.variant to whm6r)
        val width = 40
        val buffer = render(
            UnitListView(
                variants = listOf(as7d.variant, whm6r.variant),
                counts = { variant -> if (variant == whm6r.variant) 12 else 0 },
                cursorIndex = 0,
                mechFor = { models[it] },
            ),
            width = width,
            height = 4,
        )

        val headerRow = 0
        val as7dRow = 1
        val whm6rRow = 2

        assertTrue(buffer.line(headerRow).endsWith("TON WLK RUN JMP"))

        // The stats block is right-anchored at a fixed column offset from the panel's right edge —
        // always exactly 15 characters (4 fields + 3 separators) — independent of each row's own
        // label length. Spot-check that a handful of columns line up exactly between the header
        // and both data rows.
        val statsStart = width - 15
        assertEquals("T", buffer.get(statsStart, headerRow).char) // header's TON
        assertEquals("1", buffer.get(statsStart, as7dRow).char) // AS7-D tonnage "100"
        assertEquals(" ", buffer.get(statsStart, whm6rRow).char) // WHM-6R tonnage " 70"

        val jmpCol = width - 1 // last column of the JMP field, and of the panel
        assertEquals("P", buffer.get(jmpCol, headerRow).char)
        assertEquals(" ", buffer.get(jmpCol, as7dRow).char) // AS7-D can't jump
        assertEquals(" ", buffer.get(jmpCol, whm6rRow).char) // WHM-6R can't jump

        // The count sits right next to the variant id, ahead of the stat columns — not pushed
        // past them, and not shown at all when it's 0. Label text starts at column 4 (cursor
        // glyph + checkbox + one more space), regardless of which glyph the checkbox drew there.
        assertEquals("AS7-D", buffer.line(as7dRow, 4, statsStart - 4))
        assertEquals("WHM-6R  12", buffer.line(whm6rRow, 4, statsStart - 4))
    }

    @Test
    fun `stats mode leaves the jump column blank for a mech that can't jump, but shows it for one that can`() {
        val as7d = MechModels["AS7-D"] // jump 0
        val jumper = MechModels["STG-3R"] // jump 6
        val models = mapOf(as7d.variant to as7d, jumper.variant to jumper)
        val width = 40
        val buffer = render(
            UnitListView(
                variants = listOf(as7d.variant, jumper.variant),
                counts = { 0 },
                cursorIndex = 0,
                mechFor = { models[it] },
            ),
            width = width,
            height = 4,
        )

        val as7dRow = 1
        val jumperRow = 2
        val jmpCol = width - 1

        assertEquals(" ", buffer.get(jmpCol, as7dRow).char)
        assertEquals("6", buffer.get(jmpCol, jumperRow).char)
    }

    @Test
    fun `stats mode renders blank stat fields for a variant missing from the registry`() {
        val buffer = render(
            UnitListView(
                variants = listOf("unregistered"),
                counts = { 0 },
                cursorIndex = 0,
                mechFor = { null },
            ),
            width = 40,
            height = 3,
        )

        assertTrue(buffer.line(0).endsWith("TON WLK RUN JMP"))
        assertTrue(buffer.line(1).contains("unregistered"))
        assertFalse(buffer.line(1).any { it.isDigit() })
    }

    @Test
    fun `cursor and checkbox still render at their usual columns in stats mode`() {
        val as7d = MechModels["AS7-D"]
        val buffer = render(
            UnitListView(
                variants = listOf(as7d.variant),
                counts = { 1 },
                cursorIndex = 0,
                mechFor = { as7d },
            ),
            width = 40,
            height = 3,
        )

        assertEquals("▶", buffer.get(0, 1).char)
        assertEquals(checkboxIcon(CheckState.CHECKED), buffer.get(2, 1).char)
    }
}
