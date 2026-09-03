package battletech.tui.setup

import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.AssetRegistry
import battletech.tactical.model.content.ContentSummary
import battletech.tactical.model.content.MatchPlan
import battletech.tactical.unit.AutoDeploy

internal enum class SetupMode { HOT_SEAT, HOST }

internal data class SetupState(
    /** What the panels list. For the mirror this is all there is. */
    val catalog: ContentSummary,
    /** The content itself, for capacity checks and the final deploy — also populated in the mirror, so its panels can render full map previews and mech record sheets, not just names. */
    val registry: AssetRegistry,
    val mode: SetupMode = SetupMode.HOT_SEAT,
    val modeLocked: Boolean = false,
    val plan: MatchPlan = MatchPlan(),
    /** Per-panel list cursor; absent means 0. */
    val cursors: Map<SetupPanelId, Int> = emptyMap(),
    val endpoint: HostEndpoint? = null,
    val opponentConnected: Boolean = false,
    /**
     * Latched on the first [LobbyEvent.OpponentJoined] and never cleared. [rostersVisible] keys
     * off this rather than off [opponentConnected] so that an opponent who drops mid-setup takes
     * the commit gate with them ([commitBlocker]) without taking panels 2-4 — and the selections
     * already made — off the screen.
     */
    val opponentEverConnected: Boolean = false,
    /** True for the joiner's mirror: editing keys are inert. */
    val readOnly: Boolean = false,
    val helpOpen: Boolean = false,
) {
    /** Stage 2 has begun: panels 2-4 exist. */
    val rostersVisible: Boolean
        get() = modeLocked && (mode == SetupMode.HOT_SEAT || opponentEverConnected)

    /**
     * Null when committing is legal right now; otherwise the reason to flash. Ordered so the
     * message always names the FIRST thing standing in the way: a host still waiting for its
     * opponent is told exactly that, rather than being asked to select a map it cannot yet see.
     */
    fun commitBlocker(): String? {
        if (!modeLocked) return "lock a mode first"
        if (mode == SetupMode.HOST && !opponentConnected) return "waiting for player 2"
        val mapName = plan.mapName ?: return "select a map"
        if (plan.totalUnits(PlayerId.PLAYER_1) == 0) return "player 1 has no units"
        if (plan.totalUnits(PlayerId.PLAYER_2) == 0) return "player 2 has no units"
        registry.map(mapName)?.let { map ->
            val overCapacity = PlayerId.entries.any { plan.totalUnits(it) > AutoDeploy.capacity(map, it) }
            if (overCapacity) return "too many units for this map"
        }
        return null
    }
}

/**
 * Which SIDE panels EXIST this frame — mirrors `battletech.tui.game.PanelVisibility`. MODE is
 * always present; MAP/PLAYER_1/PLAYER_2 only once [SetupState.rostersVisible]; HELP only while
 * [SetupState.helpOpen].
 */
internal object SetupPanelVisibility {
    fun visiblePanels(state: SetupState): Set<SetupPanelId> = buildSet {
        add(SetupPanelId.MODE)
        if (state.rostersVisible) {
            add(SetupPanelId.MAP)
            add(SetupPanelId.PLAYER_1)
            add(SetupPanelId.PLAYER_2)
        }
        if (state.helpOpen) add(SetupPanelId.HELP)
    }
}
