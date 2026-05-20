package battletech.tactical.session

import battletech.tactical.model.PlayerId
import battletech.tactical.dice.DiceRoller
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.attack.PhysicalAttackPhaseHandler
import battletech.tactical.attack.WeaponAttackPhaseHandler
import battletech.tactical.model.GameState
import battletech.tactical.movement.MovementPhaseHandler
import battletech.tactical.query.DefaultPlayerView
import battletech.tactical.query.PlayerView

/**
 * The authoritative aggregate for a single match. Holds [GameState] and
 * [TurnState] privately and delegates command processing to a list of
 * [PhaseHandler] strategies (one per [battletech.tactical.model.TurnPhase])
 * in canonical order.
 *
 * Phase progression model:
 *
 *  - Commands flow through [submitCommand]. The current handler decides
 *    whether to [PhaseHandler.accepts] (else [CommandRejection.WrongPhase]),
 *    then [PhaseHandler.validate]s (else specific rejection), then
 *    [PhaseHandler.apply]s. After applying, the session cascades through any
 *    handlers that report [PhaseHandler.isComplete] — each advance fires the
 *    new handler's [PhaseHandler.onEntry] and emits a [PhaseChanged] event
 *    — until it lands on a handler that needs more input.
 *
 *  - [advance] is the kickstart: it fires the current handler's on-entry if
 *    pending and then runs the same cascade. Used at game start so the TUI
 *    doesn't have to know about system phases (Initiative/Heat/End) at all.
 *
 * Threading: not internally synchronised. Callers must serialise commands.
 */
public class BattleSession(
    initialGameState: GameState,
    initialTurnState: TurnState = TurnState.NULL,
    private val roller: DiceRoller = RandomDiceRoller(),
    private val handlers: List<PhaseHandler> = standardHandlers(),
    initialPhase: battletech.tactical.model.TurnPhase = handlers.first().phase,
    initialNeedsOnEntry: Boolean = true,
) {

    private var _gameState: GameState = initialGameState
    private var _turnState: TurnState = initialTurnState
    private var _currentPhaseIndex: Int = handlers.indexOfFirst { it.phase == initialPhase }.also {
        require(it >= 0) { "initialPhase $initialPhase not present in handlers" }
    }
    private var _needsOnEntry: Boolean = initialNeedsOnEntry
    private var _matchOver: Boolean = false

    private val listeners: MutableMap<PlayerId, MutableList<(GameEvent) -> Unit>> = mutableMapOf()

    public val gameState: GameState get() = _gameState
    public val turnState: TurnState get() = _turnState
    public val currentPhase: battletech.tactical.model.TurnPhase
        get() = handlers[_currentPhaseIndex].phase
    public val activePlayer: PlayerId?
        get() = handlers[_currentPhaseIndex].activePlayer(_turnState)
    public val isMatchOver: Boolean get() = _matchOver

    public fun viewFor(playerId: PlayerId): PlayerView = DefaultPlayerView(playerId, _gameState)

    /**
     * Register [listener] to receive events emitted by this session,
     * scoped to [playerId]'s view. Each event is run through
     * [EventVisibility.filterFor] before delivery; the listener never
     * sees a raw event the player isn't entitled to.
     *
     * Returns a [Subscription] whose [Subscription.unsubscribe] detaches
     * the listener. Listeners are invoked synchronously on the thread
     * that called [submitCommand] / [advance]; long-running work should
     * be deferred to another thread by the listener.
     */
    public fun subscribe(playerId: PlayerId, listener: (GameEvent) -> Unit): Subscription {
        val perPlayer = listeners.getOrPut(playerId) { mutableListOf() }
        perPlayer += listener
        return object : Subscription {
            override fun unsubscribe() {
                perPlayer.remove(listener)
            }
        }
    }

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
        events.addAll(cascade())
        dispatch(events)
        return CommandResult.Accepted(events)
    }

    /**
     * Kickstart the session at game start: fires the initial phase's
     * on-entry if pending, then cascades through any auto-completing system
     * phases. After construction with a fresh [TurnState.NULL], one call
     * lands the session at [battletech.tactical.model.TurnPhase.MOVEMENT]
     * with initiative rolled and the movement sequence seeded.
     */
    public fun advance(): List<GameEvent> {
        if (_matchOver) return emptyList()
        val events = mutableListOf<GameEvent>()
        if (_needsOnEntry) {
            events.addAll(fireOnEntry())
        }
        events.addAll(cascade())
        dispatch(events)
        return events
    }

    private fun dispatch(events: List<GameEvent>) {
        if (listeners.isEmpty() || events.isEmpty()) return
        // Iterate over a snapshot so listeners that unsubscribe themselves
        // mid-dispatch don't trip a ConcurrentModificationException.
        val snapshot = listeners.mapValues { (_, list) -> list.toList() }
        for ((playerId, perPlayer) in snapshot) {
            for (event in events) {
                val visible = EventVisibility.filterFor(playerId, event) ?: continue
                for (listener in perPlayer) listener(visible)
            }
        }
    }

    private fun cascade(): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        while (handlers[_currentPhaseIndex].isComplete(_turnState)) {
            events += advanceIndex()
            events.addAll(fireOnEntry())
        }
        return events
    }

    private fun fireOnEntry(): List<GameEvent> {
        val outcome = handlers[_currentPhaseIndex].onEntry(_gameState, _turnState, roller)
        _gameState = outcome.state
        _turnState = outcome.turn
        _needsOnEntry = false
        return outcome.events
    }

    private fun advanceIndex(): GameEvent {
        val from = handlers[_currentPhaseIndex].phase
        _currentPhaseIndex = (_currentPhaseIndex + 1) % handlers.size
        _needsOnEntry = true
        return PhaseChanged(from, handlers[_currentPhaseIndex].phase)
    }

    private companion object {
        private fun standardHandlers(): List<PhaseHandler> = listOf(
            InitiativePhaseHandler(),
            MovementPhaseHandler(),
            WeaponAttackPhaseHandler(),
            PhysicalAttackPhaseHandler(),
            HeatPhaseHandler(),
            EndPhaseHandler(),
        )
    }
}
