package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tui.aUnit
import battletech.tui.screen.BoardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.screen.ScreenBuffer
import tenter.view.render

internal class StatusBarViewTest {

    @Test
    fun `renders all status information on one bordered line`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Jump (5 MP) (+3 to-hit)", PlayerId.PLAYER_2)

        val buffer = render(view, 120, Workspace.STATUS_BAR_HEIGHT)

        assertEquals(3, Workspace.STATUS_BAR_HEIGHT)
        assertEquals("╭" + "─".repeat(118) + "╮", row(buffer, 0))
        assertEquals("   MOVEMENT    ", row(buffer).substring(4, 19))
        assertEquals("   |   ", row(buffer).substring(19, 26))
        assertEquals("Player 2", row(buffer).substring(26, 34))
        assertEquals("   |   ", row(buffer).substring(34, 41))
        assertEquals("Jump (5 MP) (+3 to-hit)", row(buffer).substring(41, 64))
        assertEquals("⌥h : help", row(buffer).substring(109, 118))
        assertEquals("╰" + "─".repeat(118) + "╯", row(buffer, 2))
    }

    @Test
    fun `formats compound phases without moving separators`() {
        val shortPhase = render(
            StatusBarView(TurnPhase.END, "Complete", PlayerId.PLAYER_1),
            120,
            Workspace.STATUS_BAR_HEIGHT,
        )
        val longPhase = render(
            StatusBarView(TurnPhase.PHYSICAL_ATTACK, "Select attack", PlayerId.PLAYER_1),
            120,
            Workspace.STATUS_BAR_HEIGHT,
        )

        assertEquals("      END      ", row(shortPhase).substring(4, 19))
        assertEquals("PHYSICAL ATTACK", row(longPhase).substring(4, 19))
        assertEquals("|", row(shortPhase).substring(22, 23))
        assertEquals("|", row(longPhase).substring(22, 23))
        assertEquals("|", row(shortPhase).substring(37, 38))
        assertEquals("|", row(longPhase).substring(37, 38))
    }

    @Test
    fun `renders player label in that player's color`() {
        val playerOne = render(
            StatusBarView(TurnPhase.MOVEMENT, "Select destination", PlayerId.PLAYER_1),
            120,
            Workspace.STATUS_BAR_HEIGHT,
        )
        val playerTwo = render(
            StatusBarView(TurnPhase.MOVEMENT, "Select destination", PlayerId.PLAYER_2),
            120,
            Workspace.STATUS_BAR_HEIGHT,
        )

        for (column in 26 until 34) {
            assertEquals(BoardRole.PLAYER_1, playerOne.get(column, 1).style.fg)
            assertEquals(BoardRole.PLAYER_2, playerTwo.get(column, 1).style.fg)
        }
    }

    @Test
    fun `reserves a blank fixed-width player field when no player is active`() {
        val view = StatusBarView(TurnPhase.END, "All phases complete")

        val buffer = render(view, 120, Workspace.STATUS_BAR_HEIGHT)

        assertEquals(" ".repeat(8), row(buffer).substring(26, 34))
        assertEquals("|", row(buffer).substring(22, 23))
        assertEquals("|", row(buffer).substring(37, 38))
        assertEquals("All phases complete", row(buffer).substring(41, 60))
    }

    @Test
    fun `leaves prompt unchanged when it has no player prefix`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Jump (5 MP)", PlayerId.PLAYER_2)

        val buffer = render(view, 120, Workspace.STATUS_BAR_HEIGHT)

        assertEquals("Jump (5 MP)", row(buffer).substring(41, 52))
    }

    @Test
    fun `prefixes action prompt with selected unit using message color`() {
        val unit = aUnit(id = "W1", name = "Wolverine WVR-6R")
        val view = StatusBarView(
            phase = TurnPhase.WEAPON_ATTACK,
            prompt = "Declare weapon fire",
            activePlayer = PlayerId.PLAYER_1,
            actionUnit = unit,
        )

        val buffer = render(view, 120, Workspace.STATUS_BAR_HEIGHT)

        val expected = "W1: Wolverine WVR-6R ┆ Declare weapon fire"
        assertEquals(expected, row(buffer).substring(41, 41 + expected.length))
        for (column in 41 until 41 + expected.length) {
            assertEquals(ChromeRole.TEXT_PRIMARY, buffer.get(column, 1).style.fg)
        }
    }

    @Test
    fun `aligns help hint to the right at different widths`() {
        for (width in listOf(100, 120, 140)) {
            val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination", PlayerId.PLAYER_1)

            val buffer = render(view, width, Workspace.STATUS_BAR_HEIGHT)

            assertEquals("⌥h : help", row(buffer).substring(width - 11, width - 2))
            assertEquals(" ", buffer.get(width - 2, 1).char)
            assertEquals("│", buffer.get(width - 1, 1).char)
        }
    }

    @Test
    fun `removes hunk padding when padded content does not fit`() {
        val view = StatusBarView(TurnPhase.MOVEMENT, "Select destination", PlayerId.PLAYER_1)

        val buffer = render(view, 70, Workspace.STATUS_BAR_HEIGHT)

        assertEquals("MOVEMENT | Player 1 | Select destination", row(buffer).substring(2, 42))
        assertEquals("⌥h : help", row(buffer).substring(59, 68))
        assertEquals("│", buffer.get(69, 1).char)
    }

    @Test
    fun `does not reserve absent player padding in compact layout`() {
        val view = StatusBarView(TurnPhase.END, "All phases complete and the match is over")

        val buffer = render(view, 50, Workspace.STATUS_BAR_HEIGHT)

        assertEquals("END |  | All phases complete and th…", row(buffer).substring(2, 38))
        assertEquals("⌥h : help", row(buffer).substring(39, 48))
    }

    @Test
    fun `ellipsizes long unit-prefixed message before help hint and preserves border`() {
        val unit = aUnit(id = "W1", name = "Wolverine WVR-6R")
        val view = StatusBarView(
            phase = TurnPhase.MOVEMENT,
            prompt = "x".repeat(100),
            activePlayer = PlayerId.PLAYER_1,
            actionUnit = unit,
        )

        val buffer = render(view, 80, Workspace.STATUS_BAR_HEIGHT)

        val expected = "W1: Wolverine WVR-6R ┆ ${"x".repeat(20)}…"
        assertEquals(expected, row(buffer).substring(24, 68))
        assertEquals(" ", buffer.get(68, 1).char)
        assertEquals("⌥h : help", row(buffer).substring(69, 78))
        assertEquals("│", buffer.get(79, 1).char)
    }

    private fun row(buffer: ScreenBuffer, index: Int = 1): String =
        (0 until buffer.width).joinToString("") { buffer.get(it, index).char }
}
