package io.archinaut.battletech.tui

import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.session.GameSession
import io.archinaut.battletech.tactical.session.LogEntry
import io.archinaut.battletech.tactical.session.SessionNotice
import io.archinaut.battletech.tactical.session.SessionOpened
import io.archinaut.battletech.tui.setup.HostEndpoint

/**
 * The `host` subcommand's own seat, with [endpoint]'s join commands in its game log right after
 * the [SessionOpened] entry. The interactive path shows the same commands in the setup screen's
 * mode panel, but `host` has no setup screen, and anything printed before the alternate screen
 * opens stays hidden until the match is over. Only this seat sees them: the match log every seat
 * shares never carries the host's addresses.
 */
internal class EndpointAnnotatedSession(
    private val session: GameSession,
    private val endpoint: HostEndpoint,
) : GameSession by session {

    override fun logFor(viewer: PlayerId?): List<LogEntry> =
        session.logFor(viewer).flatMap { entry ->
            if (entry.event is SessionOpened) {
                listOf(entry) + endpoint.joinCommands.map { command -> LogEntry(entry.turn, SessionNotice(command)) }
            } else {
                listOf(entry)
            }
        }
}
