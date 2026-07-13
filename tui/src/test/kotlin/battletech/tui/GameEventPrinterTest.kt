package battletech.tui

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.HeatDissipated
import battletech.tactical.session.PhaseChanged
import battletech.tactical.session.SessionNotice
import battletech.tui.hex.sessionNoticeIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GameEventPrinterTest {

    private val emptyState = GameState(
        units = emptyList(),
        map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)))),
    )

    // HeatDissipated with no units in heatBefore renders one LogLine with a null icon —
    // used below to exercise the ">" fallback.
    private val noIconEvent = HeatDissipated(heatBefore = emptyMap(), heatAfter = emptyMap())

    @Test
    fun `prints a turn header only when the turn number changes`() {
        val out = StringBuilder()
        val printer = GameEventPrinter(out)

        printer.print(noIconEvent, emptyState, turnNumber = 1)
        printer.print(noIconEvent, emptyState, turnNumber = 1)
        printer.print(noIconEvent, emptyState, turnNumber = 2)

        val headerCount = out.lines().count { it == "== TURN 1 ==" }
        assertThat(headerCount).isEqualTo(1)
        assertThat(out.toString()).contains("== TURN 2 ==")
        assertThat(out.lines().count { it == "== TURN 2 ==" }).isEqualTo(1)
    }

    @Test
    fun `falls back to the greater-than icon when the log line has no icon`() {
        val out = StringBuilder()
        val printer = GameEventPrinter(out)

        printer.print(noIconEvent, emptyState, turnNumber = 1)

        assertThat(out.toString()).contains("> Heat: no heat to dissipate")
    }

    @Test
    fun `SessionNotice renders with the lan-connect icon`() {
        val out = StringBuilder()
        val printer = GameEventPrinter(out)

        printer.print(SessionNotice("Session ID: ABC123"), emptyState, turnNumber = 1)

        assertThat(out.toString()).contains("${sessionNoticeIcon()} Session ID: ABC123")
    }

    @Test
    fun `PhaseChanged prints nothing, including no turn header`() {
        val out = StringBuilder()
        val printer = GameEventPrinter(out)

        printer.print(PhaseChanged(TurnPhase.INITIATIVE, TurnPhase.MOVEMENT), emptyState, turnNumber = 1)

        assertThat(out.toString()).isEmpty()
    }
}
