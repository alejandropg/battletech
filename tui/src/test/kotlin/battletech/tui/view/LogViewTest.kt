package battletech.tui.view

import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.session.Initiative
import battletech.tactical.session.InitiativeRolled
import battletech.tactical.session.LogEntry
import battletech.tactical.session.TurnEnded
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LogViewTest {

    private val emptyState = GameState(
        units = emptyList(),
        map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)))),
    )

    @Test
    fun `renders a box titled LOG`() {
        val view = LogView(entries = emptyList(), gameState = emptyState)
        val buffer = ScreenBuffer(28, 10)

        view.render(buffer, 0, 0, 28, 10)

        val topRow = (0 until 28).map { buffer.get(it, 0).char }.joinToString("")
        assertEquals('╭', topRow[0])
        assertEquals('╮', topRow[27])
        assert(topRow.contains("LOG")) { "Top border should contain 'LOG', got: $topRow" }
    }

    @Test
    fun `renders a single short entry at the top with turn prefix`() {
        val view = LogView(entries = listOf(LogEntry(turn = 2, event = TurnEnded(2))), gameState = emptyState)
        val buffer = ScreenBuffer(28, 10)

        view.render(buffer, 0, 0, 28, 10)

        // Inner content starts at x=2, y=1 (just inside the box).
        val firstLine = readLine(buffer, x = 2, y = 1, width = 24)
        assertEquals("[02] Turn 2 complete", firstLine)
    }

    @Test
    fun `turn numbers of 10 or more are not padded further`() {
        val view = LogView(entries = listOf(LogEntry(turn = 10, event = TurnEnded(10))), gameState = emptyState)
        val buffer = ScreenBuffer(28, 10)

        view.render(buffer, 0, 0, 28, 10)

        val firstLine = readLine(buffer, x = 2, y = 1, width = 24)
        assertEquals("[10] Turn 10 complete", firstLine)
    }

    @Test
    fun `renders multiple entries top-anchored in append order`() {
        val view = LogView(
            entries = listOf(
                LogEntry(1, TurnEnded(1)),
                LogEntry(1, TurnEnded(2)),
                LogEntry(1, TurnEnded(3)),
            ),
            gameState = emptyState,
        )
        val buffer = ScreenBuffer(28, 10)

        view.render(buffer, 0, 0, 28, 10)

        assertEquals("[01] Turn 1 complete", readLine(buffer, 2, 1, 24))
        assertEquals("[01] Turn 2 complete", readLine(buffer, 2, 2, 24))
        assertEquals("[01] Turn 3 complete", readLine(buffer, 2, 3, 24))
        // Below the last entry should be empty inside the box.
        assertEquals("", readLine(buffer, 2, 4, 24))
    }

    @Test
    fun `wraps long entries to multiple visual lines, continuation indented under the prefix`() {
        val initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(3, 3), PlayerId.PLAYER_2 to DiceRoll(1, 2)),
            loser = PlayerId.PLAYER_2,
            winner = PlayerId.PLAYER_1,
        )
        val view = LogView(
            entries = listOf(LogEntry(2, InitiativeRolled(initiative))),
            gameState = emptyState,
        )
        val buffer = ScreenBuffer(28, 10)

        view.render(buffer, 0, 0, 28, 10)

        // Inner width = 24. Prefix "[02] " = 5 chars. First wrap row gets up to 19 chars of text.
        // Continuation lines are indented by 5 spaces under the prefix.
        val line1 = readLine(buffer, 2, 1, 24)
        val line2 = readLine(buffer, 2, 2, 24)
        // Verify the first line starts with the prefix and contains a prefix of the text.
        assert(line1.startsWith("[02] ")) { "Line 1 missing prefix: '$line1'" }
        assert(line2.startsWith("     ")) { "Line 2 not indented: '$line2'" }
        // The text content should reassemble (whitespace flexible).
        val reassembled = (line1.removePrefix("[02] ") + " " + line2.trim()).replace(Regex("\\s+"), " ").trim()
        // Allow for further wrapping if necessary, but at minimum the first two visual lines together
        // should reproduce the start of the text.
        assert(reassembled.startsWith("Initiative: P1")) {
            "Reassembled text didn't start as expected: '$reassembled'"
        }
    }

    @Test
    fun `when content overflows, the most recent line is at the bottom of the panel`() {
        // Panel of height 6: inner height = 4 rows.
        val entries = (1..10).map { LogEntry(turn = 1, event = TurnEnded(it)) }
        val view = LogView(entries, gameState = emptyState)
        val buffer = ScreenBuffer(28, 6)

        view.render(buffer, 0, 0, 28, 6)

        // The bottom inner row (y = 4, since box bottom is y=5) should be the most recent entry.
        val bottomInnerRow = readLine(buffer, 2, 4, 24)
        assertEquals("[01] Turn 10 complete", bottomInnerRow)
        // The row above should be the previous entry.
        val secondFromBottom = readLine(buffer, 2, 3, 24)
        assertEquals("[01] Turn 9 complete", secondFromBottom)
        // And the top inner row should be entry 7 (showing the last 4 entries).
        val topInner = readLine(buffer, 2, 1, 24)
        assertEquals("[01] Turn 7 complete", topInner)
    }

    private fun readLine(buffer: ScreenBuffer, x: Int, y: Int, width: Int): String =
        (x until x + width).map { buffer.get(it, y).char }.joinToString("").trimEnd()
}
