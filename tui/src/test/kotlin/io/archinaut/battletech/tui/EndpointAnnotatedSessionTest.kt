package io.archinaut.battletech.tui

import io.archinaut.battletech.network.server.GameServer
import io.archinaut.battletech.tactical.model.content.ContentCatalog
import io.archinaut.battletech.tactical.session.SessionNotice
import io.archinaut.battletech.tactical.session.SessionOpened
import io.archinaut.battletech.tui.setup.HostEndpoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class EndpointAnnotatedSessionTest {

    @Test
    fun `the host seat's log lists the join commands right after the session id`() {
        val server = GameServer.host(ContentCatalog.load().resolveGame(), sessionId = "TESTID")
        server.use {
            server.connectLocal().use { local ->
                val endpoint = HostEndpoint(listOf("10.0.0.2", "192.168.1.5"), port = 4040, sessionId = "TESTID")

                val events = EndpointAnnotatedSession(local, endpoint).logFor(local.playerId).map { it.event }

                val opened = events.indexOf(SessionOpened("TESTID"))
                assertThat(opened).isNotNegative()
                assertThat(events.subList(opened + 1, opened + 3)).containsExactly(
                    SessionNotice("join 10.0.0.2:4040 --session TESTID"),
                    SessionNotice("join 192.168.1.5:4040 --session TESTID"),
                )
                assertThat(local.logFor(local.playerId).map { it.event }).noneMatch { it is SessionNotice && it.text.startsWith("join ") }
            }
        }
    }
}
