package battletech.tactical.session

import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.PlayerGameState
import battletech.tactical.query.PlayerView

/**
 * The delivery-facing surface of a game session: everything a delivery (TUI,
 * a future remote client) needs to render state, query per-player views, and
 * submit commands, without depending on which implementation is holding
 * authority.
 *
 * [BattleSession] is the authoritative implementation, running command
 * processing locally. Other implementations — e.g. a remote proxy that
 * forwards [submitCommand] over the network and mirrors state pushed back by
 * the authoritative session — present the same surface so deliveries can be
 * written once against [GameSession] and swapped between local and remote
 * play without change.
 *
 * Deliberately absent: raw [battletech.tactical.model.GameState]. A remote
 * client only ever holds [stateFor]'s projection (what the wire actually
 * carried), so this interface cannot expose a field a remote implementation
 * couldn't honestly serve — see [battletech.network.client.RemoteGameSession].
 * [BattleSession], the authoritative in-process implementation, keeps a
 * concrete `gameState` of its own (not part of this interface) for the
 * server-side and headless-printer call sites that legitimately need it.
 *
 * Threading: not internally synchronised. Callers must serialise commands.
 */
public interface GameSession {
    public val turnState: TurnState
    public val currentPhase: TurnPhase
    public val activePlayer: PlayerId?
    public val isMatchOver: Boolean
    public val gameLog: GameLog

    public fun viewFor(playerId: PlayerId): PlayerView

    /** What [viewer] may see. null viewer => everything foreign (fails closed). */
    public fun stateFor(viewer: PlayerId?): PlayerGameState

    /**
     * The log counterpart of [stateFor]: every [gameLog] entry redacted for [viewer] via
     * [GameEvent.redactFor], with suppressed entries (redaction returning `null`) dropped.
     * Order is preserved. Null viewer => everything foreign (fails closed), same as [stateFor].
     */
    public fun logFor(viewer: PlayerId?): List<LogEntry>

    /**
     * Register [listener] to receive every raw event emitted by this session — session-wide
     * and unfiltered, every subscriber sees everything. This is not because the game is
     * open-information (it has real hidden information); it's because this is not the
     * redaction seam. Per-player enforcement happens once, at [stateFor]/[logFor], and a
     * listener rendering directly from an event delivered here bypasses that seam the same
     * way a raw [battletech.tactical.model.GameState] read would. Returns a [Subscription]
     * whose [Subscription.unsubscribe] detaches the listener.
     */
    public fun subscribe(listener: (GameEvent) -> Unit): Subscription

    public fun submitCommand(command: GameCommand): CommandResult
}
