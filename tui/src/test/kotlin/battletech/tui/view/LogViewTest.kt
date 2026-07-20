package battletech.tui.view

import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.query.projectFor
import battletech.tactical.session.Initiative
import battletech.tactical.session.InitiativeRolled
import battletech.tactical.session.LogEntry
import battletech.tactical.session.UnitMoved
import battletech.tactical.session.UnitStoodUp
import battletech.tactical.unit.PilotingSkillRoll
import battletech.tactical.unit.UnitRoster
import battletech.tui.aUnit
import battletech.tui.hex.initiativeIcon
import battletech.tui.hex.unitStoodUpIcon
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LogViewTest {

    private val mUnit = aUnit(id = "m", name = "M")

    private val emptyState = GameState(
        units = UnitRoster(listOf(mUnit)),
        map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)))),
    ).projectFor(viewer = null, revealAll = true)

    private val passedPsr = PilotingSkillRoll(targetNumber = 5, roll = DiceRoll(4, 4), passed = true)

    private fun stoodUp(): UnitStoodUp =
        UnitStoodUp.Detailed(unitId = mUnit.id, psr = passedPsr, stoodUp = true)

    private fun movedTo(toCol: Int): UnitMoved = UnitMoved(
        unitId = mUnit.id,
        from = HexCoordinates(0, 0),
        to = HexCoordinates(toCol, 0),
        finalFacing = mUnit.facing,
        mode = MovementMode.WALK,
        mpSpent = 1,
    )

    /** Render via decorator — pixel-parity regression guard for box/border/coordinate assertions. */
    private fun renderDecorated(
        view: LogView,
        width: Int = 28,
        height: Int = 10,
        scrollOffset: Int? = null,
        anchorBottom: Boolean = false,
    ): ScreenBuffer {
        val decorated = ScrollablePanelView(
            index = LogView.INDEX,
            title = LogView.TITLE,
            content = view,
            scrollOffset = scrollOffset,
            anchorBottom = anchorBottom,
        )
        val buffer = ScreenBuffer(width, height)
        decorated.render(buffer, 0, 0, width, height)
        return buffer
    }

    @Test
    fun `renders a box titled LOG`() {
        val view = LogView(entries = emptyList(), state = emptyState)
        val buffer = renderDecorated(view)

        val topRow = (0 until 28).joinToString("") { buffer.get(it, 0).char }
        assertEquals('╭', topRow[0])
        assertEquals('╮', topRow[27])
        assert(topRow.contains("LOG")) { "Top border should contain 'LOG', got: $topRow" }
    }

    @Test
    fun `renders a single short entry under a turn header, with no turn prefix`() {
        val view = LogView(entries = listOf(LogEntry(turn = 2, event = stoodUp())), state = emptyState)
        val buffer = renderDecorated(view, scrollOffset = 0)

        // Inner content starts at x=2, y=1 (just inside the box).
        val headerLine = readLine(buffer, x = 2, y = 1, width = 24)
        assert(headerLine.startsWith("── TURN 2 ")) { "Expected turn header, got: '$headerLine'" }
        val entryLine = readLine(buffer, x = 2, y = 2, width = 24)
        assertEquals("${unitStoodUpIcon()} m stood up", entryLine)
    }

    @Test
    fun `header row foreground is cyan and a single turn produces exactly one header`() {
        val view = LogView(
            entries = listOf(
                LogEntry(2, movedTo(1)),
                LogEntry(2, movedTo(2)),
            ),
            state = emptyState,
        )
        val buffer = renderDecorated(view, scrollOffset = 0)

        assertEquals(Color.CYAN, buffer.get(2, 1).style.fg)
        // Only one header for the single turn: row 2 and row 3 are plain entries, not headers.
        val headerLine = readLine(buffer, 2, 1, 24)
        assert(headerLine.startsWith("── TURN 2 ")) { "Expected turn header, got: '$headerLine'" }
        assert(readLine(buffer, 2, 2, 24).contains("0101→0201")) { "Expected first move entry" }
        assert(readLine(buffer, 2, 3, 24).contains("0101→0301")) { "Expected second move entry" }
        assertEquals(Color.DEFAULT, buffer.get(2, 2).style.fg)
    }

    @Test
    fun `turn numbers of 10 or more render correctly in the header`() {
        val view = LogView(entries = listOf(LogEntry(turn = 10, event = stoodUp())), state = emptyState)
        val buffer = renderDecorated(view, scrollOffset = 0)

        val headerLine = readLine(buffer, x = 2, y = 1, width = 24)
        assertEquals("── TURN 10 ─────────────", headerLine)
        val entryLine = readLine(buffer, x = 2, y = 2, width = 24)
        assertEquals("${unitStoodUpIcon()} m stood up", entryLine)
    }

    @Test
    fun `renders multiple entries top-anchored in append order under a single header`() {
        val view = LogView(
            entries = listOf(
                LogEntry(1, movedTo(1)),
                LogEntry(1, movedTo(2)),
                LogEntry(1, movedTo(3)),
            ),
            state = emptyState,
        )
        val buffer = renderDecorated(view, scrollOffset = 0)

        assert(readLine(buffer, 2, 1, 24).startsWith("── TURN 1 ")) { "Expected header at row 1" }
        assert(readLine(buffer, 2, 2, 24).contains("0101→0201")) { "Expected first move entry" }
        assert(readLine(buffer, 2, 3, 24).contains("0101→0301")) { "Expected second move entry" }
        assert(readLine(buffer, 2, 4, 24).contains("0101→0401")) { "Expected third move entry" }
        // Below the last entry should be empty inside the box.
        assertEquals("", readLine(buffer, 2, 5, 24))
    }

    @Test
    fun `wraps long entries with continuation lines indented under the icon column`() {
        val initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(3, 3), PlayerId.PLAYER_2 to DiceRoll(1, 2)),
            loser = PlayerId.PLAYER_2,
            winner = PlayerId.PLAYER_1,
        )
        val view = LogView(
            entries = listOf(LogEntry(2, InitiativeRolled(initiative))),
            state = emptyState,
        )
        val buffer = renderDecorated(view, scrollOffset = 0)

        // Row 1 is the turn header; the entry text starts wrapping at row 2.
        assert(readLine(buffer, 2, 1, 24).startsWith("── TURN 2 ")) { "Expected header at row 1" }
        val line1 = readLine(buffer, 2, 2, 24)
        val line2 = readLine(buffer, 2, 3, 24)
        val icon = initiativeIcon()
        assert(line1.startsWith("$icon Initiative: P1")) { "Line 1 should start with the initiative icon: '$line1'" }
        // Continuation line is indented two columns to align under the text on line 1.
        assert(line2.startsWith("  ")) { "Line 2 should be indented under the icon column: '$line2'" }
        // The text content should reassemble (whitespace flexible).
        val reassembled = (line1.removePrefix("$icon ") + " " + line2.trim()).replace(Regex("\\s+"), " ").trim()
        assert(reassembled.startsWith("Initiative: P1")) {
            "Reassembled text didn't start as expected: '$reassembled'"
        }
    }

    @Test
    fun `when content overflows, the most recent line is at the bottom of the panel`() {
        // Panel of height 6: inner height = 4 rows. Decorated with anchorBottom=true.
        // Each entry is its own turn, so every entry is preceded by its own header row.
        val entries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val view = LogView(entries, state = emptyState)
        val buffer = renderDecorated(view, height = 6, scrollOffset = null, anchorBottom = true)

        // The bottom inner row (y = 4, since box bottom is y=5) should be the most recent entry.
        val bottomInnerRow = readLine(buffer, 2, 4, 24)
        assertEquals("${unitStoodUpIcon()} m stood up", bottomInnerRow)
        // The row above is that entry's own header.
        val secondFromBottom = readLine(buffer, 2, 3, 24)
        assert(secondFromBottom.startsWith("── TURN 10 ")) { "Expected header above last entry: '$secondFromBottom'" }
    }

    @Test
    fun `scrollOffset 0 with overflowing content shows the oldest entries`() {
        val entries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val view = LogView(entries, state = emptyState)
        // anchorBottom=true but scrollOffset=0 detaches scroll to top
        val buffer = renderDecorated(view, height = 6, scrollOffset = 0, anchorBottom = true)

        // With offset=0 the oldest entries are visible: header for turn 1, then its entry.
        val firstLine = readLine(buffer, 2, 1, 24)
        assert(firstLine.startsWith("── TURN 1 ")) { "Expected header at row 1: '$firstLine'" }
        val secondLine = readLine(buffer, 2, 2, 24)
        assertEquals("${unitStoodUpIcon()} m stood up", secondLine)
    }

    @Test
    fun `nerd-font dice icons do not push content past the panel border`() {
        // Mirrors the reported bug: an InitiativeRolled line containing four dice icons
        // must not overflow the LOG panel's right border. With width=40, inner width=36,
        // and no prefix, the first-line capacity is the full 36 visual cells.
        val initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(4, 5), PlayerId.PLAYER_2 to DiceRoll(3, 4)),
            loser = PlayerId.PLAYER_2,
            winner = PlayerId.PLAYER_1,
        )
        val view = LogView(
            entries = listOf(LogEntry(1, InitiativeRolled(initiative))),
            state = emptyState,
        )
        val buffer = renderDecorated(view, width = 40, height = 6, scrollOffset = 0)

        // Right border column must stay '│' on every row inside the panel —
        // no cell on the right border should have been overwritten by leaking content.
        for (y in 1..4) {
            assertEquals("│", buffer.get(39, y).char, "right border at row $y")
        }
        // Each dice icon must occupy exactly one cell as a full surrogate-pair string,
        // not a split half-surrogate per cell. Row 1 is the turn header; the entry is on row 2.
        val entryLine = (2 until 38).joinToString("") { buffer.get(it, 2).char }.trimEnd()
        val dice4 = String(Character.toChars(0xF01CD))
        assert(entryLine.contains(dice4)) { "entry line should contain dice_4 glyph: '$entryLine'" }
    }

    private fun readLine(buffer: ScreenBuffer, x: Int, y: Int, width: Int): String =
        (x until x + width).joinToString("") { buffer.get(it, y).char }.trimEnd()
}
