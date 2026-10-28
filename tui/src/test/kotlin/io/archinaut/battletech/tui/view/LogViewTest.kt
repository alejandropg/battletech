package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.dice.DiceRoll
import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.model.GameState
import io.archinaut.battletech.tactical.model.Hex
import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.MovementMode
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.query.projectFor
import io.archinaut.battletech.tactical.session.Initiative
import io.archinaut.battletech.tactical.session.InitiativeRolled
import io.archinaut.battletech.tactical.session.LogEntry
import io.archinaut.battletech.tactical.session.UnitMoved
import io.archinaut.battletech.tactical.session.UnitStoodUp
import io.archinaut.battletech.tactical.unit.PilotingSkillRoll
import io.archinaut.battletech.tactical.unit.UnitRoster
import io.archinaut.battletech.tui.aUnit
import io.archinaut.battletech.tui.icon.initiativeIcon
import io.archinaut.battletech.tui.icon.movementModeIcon
import io.archinaut.battletech.tui.icon.unitStoodUpIcon
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.widget.ViewportState
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

    /** Render in the LOG panel's chrome, stuck to the bottom as [GamePanels] declares it. */
    private fun renderDecorated(
        view: LogView,
        width: Int = 28,
        height: Int = 10,
        scrollOffset: Int = 0,
    ): Canvas = renderInPanel(
        view,
        badge = '9',
        title = LogView.TITLE,
        width = width,
        height = height,
        scrollOffset = scrollOffset,
        stickToBottom = true,
    )

    @Test
    fun `renders a box titled LOG`() {
        val view = LogView(entries = emptyList(), state = emptyState)
        val buffer = renderDecorated(view)

        val topRow = (0 until 28).joinToString("") { buffer.get(it, 0).symbol }
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
    fun `header row foreground is info and a single turn produces exactly one header`() {
        val view = LogView(
            entries = listOf(
                LogEntry(2, movedTo(1)),
                LogEntry(2, movedTo(2)),
            ),
            state = emptyState,
        )
        val buffer = renderDecorated(view, scrollOffset = 0)

        assertEquals(StandardRole.INFO, buffer.get(2, 2).style.fg)
        // Only one header for the single turn: row 3 and row 4 are plain entries, not headers.
        val headerLine = buffer.line(2, 2, 24)
        assert(headerLine.startsWith("── TURN 2 ")) { "Expected turn header, got: '$headerLine'" }
        assert(buffer.line(3, 2, 24).contains("0101→0201")) { "Expected first move entry" }
        assert(buffer.line(4, 2, 24).contains("0101→0301")) { "Expected second move entry" }
        assertEquals(null, buffer.get(2, 3).style.fg, "renders in the default color")
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
        // Panel of height 6: content viewport is 3 rows. A fresh panel stuck to the bottom shows
        // the last rows.
        // Each entry is its own turn, so every entry is preceded by its own header row.
        // The bottom content row stays fixed at y=4 regardless of padding — only the top
        // of the viewport moved down — so this test's row numbers don't shift.
        val entries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val view = LogView(entries, state = emptyState)
        val buffer = renderDecorated(view, height = 6)

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
        // equivalent): once the user has wheeled away from the bottom, that position must stick.
        val entries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val view = LogView(entries, state = emptyState)

        // First render: stuck to the bottom.
        val viewportState = ViewportState(stickToBottom = true)
        val first = panelViewport(
            title = LogView.TITLE,
            badge = "9",
            content = view,
        )
        render(first, viewportState, 28, 6)
        viewportState.scrollBy(0, -100)

        // Second render: the user has wheeled to the top, so the view no longer sticks to the bottom.
        val second = panelViewport(
            title = LogView.TITLE,
            badge = "9",
            content = view,
        )
        val buffer = render(second, viewportState, 28, 6)

        assertEquals(0, viewportState.settled?.offset?.y, "a manual scroll to the top must not snap back to the bottom")
        val firstLine = buffer.line(2, 2, 24)
        assert(firstLine.startsWith("── TURN 1 ")) { "Expected header at row 2: '$firstLine'" }
        val secondLine = buffer.line(3, 2, 24)
        assertEquals("${unitStoodUpIcon()} m stood up", secondLine)
    }

    @Test
    fun `a new entry stays in view while the reader is at the bottom`() {
        val tenEntries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val viewportState = ViewportState(stickToBottom = true)
        render(logViewport(tenEntries), viewportState, 28, 6)

        val buffer = render(logViewport(tenEntries + LogEntry(turn = 11, event = stoodUp())), viewportState, 28, 6)

        assertEquals("${unitStoodUpIcon()} m stood up", buffer.line(4, 2, 24))
        val secondFromBottom = buffer.line(3, 2, 24)
        assert(secondFromBottom.startsWith("── TURN 11 ")) { "Expected header above last entry: '$secondFromBottom'" }
    }

    @Test
    fun `a new entry leaves a reader who scrolled up on the history they are reading`() {
        val tenEntries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val viewportState = ViewportState(stickToBottom = true)
        render(logViewport(tenEntries), viewportState, 28, 6)
        viewportState.scrollBy(0, -100)
        render(logViewport(tenEntries), viewportState, 28, 6)

        render(logViewport(tenEntries + LogEntry(turn = 11, event = stoodUp())), viewportState, 28, 6)

        assertEquals(0, viewportState.settled?.offset?.y, "a new entry must not pull the reader away from history")
    }

    @Test
    fun `scrolling back to the bottom follows new entries again`() {
        val tenEntries = (1..10).map { LogEntry(turn = it, event = stoodUp()) }
        val viewportState = ViewportState(stickToBottom = true)
        render(logViewport(tenEntries), viewportState, 28, 6)
        viewportState.scrollBy(0, -100)
        render(logViewport(tenEntries), viewportState, 28, 6)
        viewportState.scrollBy(0, 100)
        render(logViewport(tenEntries), viewportState, 28, 6)

        val buffer = render(logViewport(tenEntries + LogEntry(turn = 11, event = stoodUp())), viewportState, 28, 6)

        val secondFromBottom = buffer.line(3, 2, 24)
        assert(secondFromBottom.startsWith("── TURN 11 ")) { "Expected header above last entry: '$secondFromBottom'" }
    }

    private fun logViewport(entries: List<LogEntry>) = panelViewport(
        title = LogView.TITLE,
        badge = "9",
        content = LogView(entries, state = emptyState),
    )

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
            assertEquals("│", buffer.get(39, y).symbol, "right border at row $y")
        }
        // Each dice icon must occupy exactly one cell as a full surrogate-pair string,
        // not a split half-surrogate per cell. Row 2 is the turn header; the entry is on row 3.
        val entryLine = (2 until 38).joinToString("") { buffer.get(it, 3).symbol }.trimEnd()
        val dice4 = String(Character.toChars(0xF01CD))
        assert(entryLine.contains(dice4)) { "entry line should contain dice_4 glyph: '$entryLine'" }
    }

    @Test
    fun `a unit name is painted in its owner's color while the surrounding prose stays default`() {
        val atlas = aUnit(id = "atlas", name = "Atlas", owner = PlayerId.PLAYER_1)
        val stateWithAtlas = GameState(
            units = UnitRoster(listOf(atlas)),
            map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)))),
        ).projectFor(viewer = null, revealAll = true)
        val moved = UnitMoved(
            unitId = atlas.id,
            from = HexCoordinates(0, 0),
            to = HexCoordinates(0, 1),
            finalFacing = atlas.facing,
            mode = MovementMode.WALK,
            mpSpent = 1,
        )
        val view = LogView(entries = listOf(LogEntry(1, moved)), state = stateWithAtlas)
        val buffer = renderDecorated(view, scrollOffset = 0)

        val entryLine = buffer.line(3, 2, 24)
        assertEquals("${movementModeIcon(MovementMode.WALK)} atlas (1 MP) 0101→0102", entryLine)

        // Layout: x=2 icon, x=3 space, x=4..8 "atlas", x=9 space before "(1 MP)".
        assertEquals(null, buffer.get(2, 3).style.fg, "icon stays default")
        assertEquals(playerColor(PlayerId.PLAYER_1), buffer.get(4, 3).style.fg, "'a' of atlas")
        assertEquals(playerColor(PlayerId.PLAYER_1), buffer.get(8, 3).style.fg, "'s' of atlas")
        assertEquals(null, buffer.get(9, 3).style.fg, "prose after the name stays default")
    }

    @Test
    fun `a name straddling a wrap point stays colored on both rows`() {
        val longNamed = aUnit(id = "longname", name = "Longname", owner = PlayerId.PLAYER_2)
        val stateWithUnit = GameState(
            units = UnitRoster(listOf(longNamed)),
            map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)))),
        ).projectFor(viewer = null, revealAll = true)
        val moved = UnitMoved(
            unitId = longNamed.id,
            from = HexCoordinates(0, 0),
            to = HexCoordinates(0, 1),
            finalFacing = longNamed.facing,
            mode = MovementMode.WALK,
            mpSpent = 1,
        )
        val view = LogView(entries = listOf(LogEntry(1, moved)), state = stateWithUnit)
        // Tall enough that every wrapped row (there are several, at this narrow a width) fits the
        // viewport — otherwise LogView's bottom-follow reveal (see its class KDoc) would scroll
        // past the rows this test wants to look at.
        val buffer = renderDecorated(view, width = 10, height = 14, scrollOffset = 0)

        // Inner content width = 10 - 4 = 6; prefix (icon + space) = 2; available = 4.
        // "longname" (8 cells) is a single unbroken word wider than capacity, so it hard-splits
        // into "long" then "name", each its own row.
        assertEquals("${movementModeIcon(MovementMode.WALK)} long", buffer.line(3, 2, 6))
        assertEquals("  name", buffer.line(4, 2, 6))

        val color = playerColor(PlayerId.PLAYER_2)
        assertEquals(color, buffer.get(4, 3).style.fg, "'l' of long")
        assertEquals(color, buffer.get(7, 3).style.fg, "'g' of long")
        assertEquals(color, buffer.get(4, 4).style.fg, "'n' of name")
        assertEquals(color, buffer.get(7, 4).style.fg, "'e' of name")
    }
}
