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
 * Two implementations: [BattleSession] runs command processing locally,
 * [battletech.network.client.ClientGameSession] forwards it to a host and mirrors what
 * comes back.
 *
 * Raw [battletech.tactical.model.GameState] is deliberately absent, so this interface
 * cannot expose a field a client implementation could not honestly serve.
 * [BattleSession] keeps a concrete `gameState` of its own, outside this interface.
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
     * and unfiltered, every subscriber sees everything. Returns a [Subscription] whose
     * [Subscription.unsubscribe] detaches the listener.
     */
    public fun subscribe(listener: (GameEvent) -> Unit): Subscription

    public fun submitCommand(command: GameCommand): CommandResult
}
