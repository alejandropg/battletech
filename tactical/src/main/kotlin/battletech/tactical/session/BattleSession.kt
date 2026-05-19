package battletech.tactical.session

import battletech.tactical.action.PlayerId
import battletech.tactical.command.CommandRejection
import battletech.tactical.command.CommandResult
import battletech.tactical.command.GameCommand
import battletech.tactical.dice.DiceRoller
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.event.GameEvent
import battletech.tactical.event.PhaseChanged
import battletech.tactical.model.GameState
import battletech.tactical.view.DefaultPlayerView
import battletech.tactical.view.PlayerView

/**
 * The authoritative aggregate for a single match. Holds [GameState] and
 * [TurnState] privately and delegates command processing to a list of
 * [PhaseHandler] strategies (one per [battletech.tactical.action.TurnPhase])
 * in canonical order.
 *
 * Phase progression model:
 *
 *  - Commands flow through [submitCommand]. The current handler decides
 *    whether to [PhaseHandler.accepts] (else [CommandRejection.WrongPhase]),
 *    then [PhaseHandler.validate]s (else specific rejection), then
 *    [PhaseHandler.apply]s. If the handler reports [PhaseHandler.isComplete]
 *    after apply, the phase index advances (a [PhaseChanged] event is
 *    appended) and the new handler's on-entry work is deferred to the next
 *    [advance] call.
 *
 *  - System phases (Initiative/Heat/End) do their work in
 *    [PhaseHandler.onEntry], are immediately complete, and need [advance]
 *    to be called by the caller (the TUI tick loop) to fire. Each
 *    [advance] runs at most one on-entry + one phase-index increment, so
 *    each tick produces a discrete flash for the user.
 *
 * Threading: not internally synchronised. Callers must serialise commands.
 */
public class BattleSession(
    initialGameState: GameState,
    initialTurnState: TurnState = TurnState.NULL,
    private val roller: DiceRoller = RandomDiceRoller(),
    private val handlers: List<PhaseHandler> = standardHandlers(),
    initialPhase: battletech.tactical.action.TurnPhase = handlers.first().phase,
    initialNeedsOnEntry: Boolean = true,
) {

    private var _gameState: GameState = initialGameState
    private var _turnState: TurnState = initialTurnState
    private var _currentPhaseIndex: Int = handlers.indexOfFirst { it.phase == initialPhase }.also {
        require(it >= 0) { "initialPhase $initialPhase not present in handlers" }
    }
    private var _needsOnEntry: Boolean = initialNeedsOnEntry
    private var _matchOver: Boolean = false

    public val gameState: GameState get() = _gameState
    public val turnState: TurnState get() = _turnState
    public val currentPhase: battletech.tactical.action.TurnPhase
        get() = handlers[_currentPhaseIndex].phase
    public val activePlayer: PlayerId?
        get() = handlers[_currentPhaseIndex].activePlayer(_turnState)
    public val isMatchOver: Boolean get() = _matchOver

    public fun viewFor(playerId: PlayerId): PlayerView = DefaultPlayerView(playerId, _gameState)

    public fun submitCommand(command: GameCommand): CommandResult {
        if (_matchOver) return CommandResult.Rejected(CommandRejection.MatchOver)
        val handler = handlers[_currentPhaseIndex]
        if (!handler.accepts(command, _turnState)) {
            return CommandResult.Rejected(CommandRejection.WrongPhase(handler.phase))
        }
        handler.validate(command, _gameState, _turnState)?.let { reason ->
            return CommandResult.Rejected(reason)
        }
        val outcome = handler.apply(command, _gameState, _turnState, roller)
        _gameState = outcome.state
        _turnState = outcome.turn
        val events = outcome.events.toMutableList()
        if (handler.isComplete(_turnState)) {
            events += advanceIndex()
            // Fire on-entry for the new phase so its state is settled for
            // the next read. We deliberately do NOT cascade further here —
            // discrete TUI ticks drive system-phase cascades, one per tick.
            val newHandler = handlers[_currentPhaseIndex]
            if (_needsOnEntry) {
                val entry = newHandler.onEntry(_gameState, _turnState, roller)
                _gameState = entry.state
                _turnState = entry.turn
                events.addAll(entry.events)
                _needsOnEntry = false
            }
        }
        return CommandResult.Accepted(events)
    }

    /**
     * Drive one step of phase progression. Used by the TUI tick loop to
     * run system-phase on-entry work (init roll, heat dissipation,
     * end-of-turn reset) and cascade into the next phase.
     *
     * One call does at most one on-entry + one phase-index increment, so
     * each tick produces a discrete event stream.
     */
    public fun advance(): List<GameEvent> {
        if (_matchOver) return emptyList()
        val handler = handlers[_currentPhaseIndex]
        val events = mutableListOf<GameEvent>()
        if (_needsOnEntry) {
            val outcome = handler.onEntry(_gameState, _turnState, roller)
            _gameState = outcome.state
            _turnState = outcome.turn
            events.addAll(outcome.events)
            _needsOnEntry = false
        }
        if (handler.isComplete(_turnState)) {
            events += advanceIndex()
        }
        return events
    }

    private fun advanceIndex(): GameEvent {
        val from = handlers[_currentPhaseIndex].phase
        _currentPhaseIndex = (_currentPhaseIndex + 1) % handlers.size
        _needsOnEntry = true
        return PhaseChanged(from, handlers[_currentPhaseIndex].phase)
    }

    /**
     * Transitional escape hatch used by the TUI for the attack-impulse
     * *draft* state — the in-progress declarations a player is building
     * before they press Commit. PR7 moves the draft out of [TurnState]
     * into the TUI's own state and removes this method.
     */
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Transitional escape hatch for the TUI's attack-draft updates. " +
            "PR7 removes it once the draft lives in the TUI layer.",
    )
    public fun applyMutation(transform: (GameState, TurnState) -> Pair<GameState, TurnState>) {
        val (g, t) = transform(_gameState, _turnState)
        _gameState = g
        _turnState = t
    }

    public companion object {
        public fun standardHandlers(): List<PhaseHandler> = listOf(
            InitiativePhaseHandler(),
            MovementPhaseHandler(),
            WeaponAttackPhaseHandler(),
            PhysicalAttackPhaseHandler(),
            HeatPhaseHandler(),
            EndPhaseHandler(),
        )
    }
}
