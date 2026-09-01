package battletech.tui

import battletech.network.client.LobbyClient
import battletech.network.server.LobbyHost
import battletech.network.server.SocketAcceptor
import battletech.tactical.model.content.AssetBundle
import battletech.tactical.model.content.MatchPlan
import battletech.tui.setup.HostEndpoint
import battletech.tui.setup.LobbyEvent
import battletech.tui.setup.SetupLobby
import java.net.BindException
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The `battletech.network` adapters `Main.kt`'s `Mode.Interactive`/`Mode.Join` branches compose —
 * split out of `Main.kt` to keep that file from growing past a single screenful of wiring; see
 * `ArchitectureTest`'s KDoc for why this file is the one other place allowed to import
 * `battletech.network`.
 */

/**
 * The setup screen's [SetupLobby] port over a real [LobbyHost]: [beginHosting] constructs the
 * lobby and starts listening, [publish] mirrors the host's plan to the parked peer, and
 * [subscribe]'s listener is wired onto [LobbyHost.onChange] as soon as the lobby exists — it may
 * be registered before [beginHosting] runs (the setup loop subscribes once at startup,
 * regardless of which mode the user ends up choosing), so it is buffered here until then.
 *
 * [lobbyHost] is exposed for `main()`'s own use at commit time: this adapter models only what the
 * SETUP SCREEN needs (the [SetupLobby] port), not the commit itself, which needs the full
 * [LobbyHost] (`commit`, `connectLocal` ordering) that [SetupLobby] deliberately doesn't expose.
 */
internal class LobbyHostAdapter(
    private val content: AssetBundle,
    private val port: Int = DEFAULT_PORT,
) : SetupLobby {

    private var host: LobbyHost? = null
    private val pendingListeners: MutableList<(LobbyEvent) -> Unit> = mutableListOf()

    val lobbyHost: LobbyHost? get() = host

    override fun beginHosting(): HostEndpoint {
        val lobby = LobbyHost(ownContent = content)
        host = lobby
        pendingListeners.forEach { listener -> lobby.onChange { registry -> listener(LobbyEvent.OpponentJoined(registry)) } }

        val acceptor = try {
            SocketAcceptor(lobby, port).also { it.start() }
        } catch (e: BindException) {
            SocketAcceptor(lobby, 0).also { it.start() }
        }

        return HostEndpoint(addresses = addresses(), port = acceptor.boundPort, sessionId = lobby.sessionId)
    }

    override fun publish(plan: MatchPlan) {
        host?.publish(plan)
    }

    override fun subscribe(listener: (LobbyEvent) -> Unit) {
        pendingListeners += listener
    }
}

/** The setup screen's [SetupLobby] port for the joiner's read-only mirror, over a [LobbyClient]. */
internal class LobbyMirrorAdapter(private val client: LobbyClient) : SetupLobby {
    override fun beginHosting(): HostEndpoint? = null

    override fun publish(plan: MatchPlan): Unit = Unit

    override fun subscribe(listener: (LobbyEvent) -> Unit) {
        client.onSelections { listener(LobbyEvent.SelectionsChanged(it)) }
        client.onCommitted { listener(LobbyEvent.MatchStarted) }
    }
}

/**
 * Every non-loopback IPv4 address of an up network interface, in interface order — the addresses
 * shown in panel 1 for a peer to `join`. Falls back to `127.0.0.1` when the list would otherwise
 * be empty, so the panel is never blank.
 */
internal fun addresses(): List<String> {
    val found = NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .filterNot { it.isLoopbackAddress }
        .map { it.hostAddress }
        .toList()
    return found.ifEmpty { listOf("127.0.0.1") }
}
