package battletech.tactical.session

import battletech.tactical.model.GameState
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
 * Threading: not internally synchronised. Callers must serialise commands.
 */
public interface GameSession {
    public val gameState: GameState
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
     * Register [listener] to receive every event emitted by this session.
     * Open-information: there is no per-player filtering — every subscriber
     * sees everything. Returns a [Subscription] whose
     * [Subscription.unsubscribe] detaches the listener.
     */
    public fun subscribe(listener: (GameEvent) -> Unit): Subscription

    public fun submitCommand(command: GameCommand): CommandResult
}
