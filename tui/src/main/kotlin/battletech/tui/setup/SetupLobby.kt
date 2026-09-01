package battletech.tui.setup

import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.MatchPlan

internal data class HostEndpoint(
    val addresses: List<String>,
    val port: Int,
    val sessionId: String,
)

internal sealed interface LobbyEvent {
    /** The opponent parked; [registry] is the merged registry the panels must list from. */
    data class OpponentJoined(val registry: AssetRegistry) : LobbyEvent

    /**
     * The parked opponent dropped. The panels stay (the selections made so far are still valid,
     * and the endpoint is unchanged, so the same player can reconnect) — only the commit gate
     * closes again. See [SetupState.opponentEverConnected].
     */
    data object OpponentLeft : LobbyEvent

    /** Mirror only: the host changed its selections. */
    data class SelectionsChanged(val plan: MatchPlan) : LobbyEvent

    /** Mirror only: the host committed; the setup screen is done. */
    data object MatchStarted : LobbyEvent
}

/**
 * What the setup screen needs from whatever is (or is not) on the other end of a wire. Three
 * adapters satisfy it: [NoLobby] for hot-seat, and — built in Main.kt over battletech.network —
 * one over the host's lobby and one over the joiner's mirror.
 */
internal interface SetupLobby {
    /** Binds the listener and returns where to connect, or null when this adapter never hosts. */
    fun beginHosting(): HostEndpoint?

    /** Host only: mirror the current plan to a parked opponent. A no-op elsewhere. */
    fun publish(plan: MatchPlan)

    fun subscribe(listener: (LobbyEvent) -> Unit)
}

internal object NoLobby : SetupLobby {
    override fun beginHosting(): HostEndpoint? = null
    override fun publish(plan: MatchPlan): Unit = Unit
    override fun subscribe(listener: (LobbyEvent) -> Unit): Unit = Unit
}
