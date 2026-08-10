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
import battletech.tui.game.PanelId
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
    ): ScreenBuffer = renderInPanel(
        view,
        key = PanelId.LOG.key,
        title = LogView.TITLE,
        width = width,
        height = height,
        scrollOffset = scrollOffset,
    )

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

        // Inner content starts at x=2, y=2 (past the border and the padding spacer row).
        val headerLine = buffer.line(2, 2, 24)
        assert(headerLine.startsWith("── TURN 2 ")) { "Expected turn header, got: '$headerLine'" }
        val entryLine = buffer.line(3, 2, 24)
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

        assertEquals(Color.CYAN, buffer.get(2, 2).style.fg)
        // Only one header for the single turn: row 3 and row 4 are plain entries, not headers.
        val headerLine = buffer.line(2, 2, 24)
        assert(headerLine.startsWith("── TURN 2 ")) { "Expected turn header, got: '$headerLine'" }
        assert(buffer.line(3, 2, 24).contains("0101→0201")) { "Expected first move entry" }
        assert(buffer.line(4, 2, 24).contains("0101→0301")) { "Expected second move entry" }
        assertEquals(Color.DEFAULT, buffer.get(2, 3).style.fg)
    }

    @Test
    fun `turn numbers of 10 or more render correctly in the header`() {
        val view = LogView(entries = listOf(LogEntry(turn = 10, event = stoodUp())), state = emptyState)
        val buffer = renderDecorated(view, scrollOffset = 0)

        val headerLine = buffer.line(2, 2, 24)
        assertEquals("── TURN 10 ─────────────", headerLine)
        val entryLine = buffer.line(3, 2, 24)
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

        assert(buffer.line(2, 2, 24).startsWith("── TURN 1 ")) { "Expected header at row 2" }
        assert(buffer.line(3, 2, 24).contains("0101→0201")) { "Expected first move entry" }
        assert(buffer.line(4, 2, 24).contains("0101→0301")) { "Expected second move entry" }
        assert(buffer.line(5, 2, 24).contains("0101→0401")) { "Expected third move entry" }
        // Below the last entry should be empty inside the box.
        assertEquals("", buffer.line(6, 2, 24))
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

        // Row 2 is the turn header; the entry text starts wrapping at row 3.
        assert(buffer.line(2, 2, 24).startsWith("── TURN 2 ")) { "Expected header at row 2" }
        val line1 = buffer.line(3, 2, 24)
        val line2 = buffer.line(4, 2, 24)
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
        // Panel of height 6: content viewport is 3 rows. A fresh render has no previousFocus, so
        // LogView's last-row focus (see its KDoc) always follows into view — the same mechanism
        // TargetsView's cursor row uses, not a bespoke bottom-anchor.
        // Each entry is its own turn, so every entry is preceded by its own header row.
        // The bottom content row stays fixed at y=4 regardless of padding — only the top
        // of the viewport moved down — so this test's row numbers don't shift.
        val entries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val view = LogView(entries, state = emptyState)
        val buffer = renderDecorated(view, height = 6, scrollOffset = null)

        // The bottom inner row (y = 4, since box bottom is y=5) should be the most recent entry.
        val bottomInnerRow = buffer.line(4, 2, 24)
        assertEquals("${unitStoodUpIcon()} m stood up", bottomInnerRow)
        // The row above is that entry's own header.
        val secondFromBottom = buffer.line(3, 2, 24)
        assert(secondFromBottom.startsWith("── TURN 10 ")) { "Expected header above last entry: '$secondFromBottom'" }
    }

    @Test
    fun `a manual scroll to the top survives a re-render while the log is unchanged`() {
        // The panel-side half of the pan-snap-back defect (see PanelSlotTest's TARGETS
        // equivalent): once the user has wheeled to a position and the focus row (the last
        // line) hasn't moved since, that position must stick rather than re-following.
        val entries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val view = LogView(entries, state = emptyState)

        // First render: no previousFocus, so it follows to the bottom — establishes the focus.
        val first = ScrollableView(
            title = LogView.TITLE,
            badge = PanelId.LOG.key.toString(),
            content = view,
            extent = ContentExtent.Measured(),
        )
        render(first, 28, 6)
        val focus = first.scroll.focus!!

        // Second render: the user has wheeled back to the top; the log hasn't changed, so the
        // focus row is identical — the manual offset must be respected, not re-followed.
        val second = ScrollableView(
            title = LogView.TITLE,
            badge = PanelId.LOG.key.toString(),
            content = view,
            extent = ContentExtent.Measured(),
            offset = ScrollOffset(0, 0),
            previousFocus = focus,
        )
        val buffer = render(second, 28, 6)

        assertEquals(0, second.scroll.offset.y, "a manual scroll to the top must not snap back to the bottom")
        val firstLine = buffer.line(2, 2, 24)
        assert(firstLine.startsWith("── TURN 1 ")) { "Expected header at row 2: '$firstLine'" }
        val secondLine = buffer.line(3, 2, 24)
        assertEquals("${unitStoodUpIcon()} m stood up", secondLine)
    }

    @Test
    fun `a new entry re-sticks to the bottom even while manually scrolled away`() {
        // Accepted tradeoff of following the last-row focus like TargetsView's cursor: a growing
        // log always chases its newest line, the same as it would chase a moving cursor row —
        // there is no bespoke "let the reader linger on history" carve-out anymore.
        val tenEntries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val scrolledAway = ScrollableView(
            title = LogView.TITLE,
            badge = PanelId.LOG.key.toString(),
            content = LogView(tenEntries, state = emptyState),
            extent = ContentExtent.Measured(),
        )
        render(scrolledAway, 28, 6)
        val focusAtTen = scrolledAway.scroll.focus!!
        val manuallyScrolledUp = ScrollableView(
            title = LogView.TITLE,
            badge = PanelId.LOG.key.toString(),
            content = LogView(tenEntries, state = emptyState),
            extent = ContentExtent.Measured(),
            offset = ScrollOffset(0, 0),
            previousFocus = focusAtTen,
        )
        render(manuallyScrolledUp, 28, 6)
        assertEquals(0, manuallyScrolledUp.scroll.offset.y, "sanity check: scrolled away from the bottom")

        // An eleventh entry arrives; the focus row moves, so the view follows it to the bottom.
        val elevenEntries = tenEntries + LogEntry(turn = 11, event = stoodUp())
        val newEntryArrives = ScrollableView(
            title = LogView.TITLE,
            badge = PanelId.LOG.key.toString(),
            content = LogView(elevenEntries, state = emptyState),
            extent = ContentExtent.Measured(),
            offset = manuallyScrolledUp.scroll.offset,
            previousFocus = manuallyScrolledUp.scroll.focus,
        )
        val buffer = render(newEntryArrives, 28, 6)

        val bottomInnerRow = buffer.line(4, 2, 24)
        assertEquals("${unitStoodUpIcon()} m stood up", bottomInnerRow)
        val secondFromBottom = buffer.line(3, 2, 24)
        assert(secondFromBottom.startsWith("── TURN 11 ")) { "Expected header above last entry: '$secondFromBottom'" }
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
        // not a split half-surrogate per cell. Row 2 is the turn header; the entry is on row 3.
        val entryLine = (2 until 38).joinToString("") { buffer.get(it, 3).char }.trimEnd()
        val dice4 = String(Character.toChars(0xF01CD))
        assert(entryLine.contains(dice4)) { "entry line should contain dice_4 glyph: '$entryLine'" }
    }

}
