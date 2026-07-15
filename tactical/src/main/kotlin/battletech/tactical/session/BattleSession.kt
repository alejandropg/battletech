package battletech.tactical.session

import battletech.tactical.attack.physical.PhysicalAttackPhaseHandler
import battletech.tactical.attack.weapon.WeaponAttackPhaseHandler
import battletech.tactical.dice.DiceRoller
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.MatchStatus
import battletech.tactical.model.PlayerId
import battletech.tactical.model.victoryStatus
import battletech.tactical.movement.MovementPhaseHandler
import battletech.tactical.query.DefaultPlayerView
import battletech.tactical.query.PlayerGameState
import battletech.tactical.query.PlayerView
import battletech.tactical.query.projectFor
import battletech.tactical.unit.destructionReason

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
) : GameSession {

    private var _gameState: GameState = initialGameState
    private var _turnState: TurnState = initialTurnState
    private var _currentPhaseIndex: Int = handlers.indexOfFirst { it.phase == initialPhase }.also {
        require(it >= 0) { "initialPhase $initialPhase not present in handlers" }
    }
    private var _needsOnEntry: Boolean = initialNeedsOnEntry
    private var _matchOver: Boolean = false

    private val listeners: MutableList<(GameEvent) -> Unit> = mutableListOf()

    private val _gameLog: GameLog = GameLog()

    public override val gameLog: GameLog get() = _gameLog

    /**
     * The authoritative, unredacted state. NOT part of [GameSession] — this is a concrete
     * member only [BattleSession] exposes, for the server-side call sites that legitimately
     * need raw state in-process ([battletech.network.server.GameServer] building outbound
     * [battletech.network.wire.GameSnapshot]s, the headless printer in `Main.kt`). Every
     * delivery-facing read goes through [stateFor] instead.
     */
    public val gameState: GameState get() = _gameState
    public override val turnState: TurnState get() = _turnState
    public override val currentPhase: battletech.tactical.model.TurnPhase
        get() = handlers[_currentPhaseIndex].phase
    public override val activePlayer: PlayerId?
        get() = handlers[_currentPhaseIndex].activePlayer(_turnState)
    public override val isMatchOver: Boolean get() = _matchOver

    /**
     * Built from [stateFor] — the projection — NOT raw [_gameState], so the authoritative
     * host and a remote client
     * ([battletech.network.client.RemoteGameSession.viewFor], over its projected snapshot)
     * run the identical [DefaultPlayerView] code against the identical shape of input. One
     * implementation, so a client's "what is legal right now?" can never drift from the
     * server's answer, and the host cannot accidentally rely on data a client wouldn't have.
     */
    public override fun viewFor(playerId: PlayerId): PlayerView =
        DefaultPlayerView(playerId, stateFor(playerId), _turnState)

    /**
     * The deliberate match-over reveal lives here, inside the projection:
     * once [_matchOver] is set, every unit becomes visible to every viewer
     * (including `null`), regardless of ownership.
     */
    public override fun stateFor(viewer: PlayerId?): PlayerGameState =
        _gameState.projectFor(viewer, revealAll = _matchOver)

    /**
     * The log counterpart of [stateFor]: same viewer, same [_matchOver] reveal, applied
     * per-entry via [GameEvent.redactFor] instead of per-unit via [battletech.tactical.query.projectFor].
     */
    public override fun logFor(viewer: PlayerId?): List<LogEntry> =
        _gameLog.snapshot().mapNotNull { entry ->
            entry.event.redactFor(viewer, _gameState, revealAll = _matchOver)?.let { entry.copy(event = it) }
        }

    /**
     * Register [listener] to receive every event emitted by this session:
     * a session-wide feed, not scoped to any player (open-information).
     *
     * Returns a [Subscription] whose [Subscription.unsubscribe] detaches
     * the listener. Listeners are invoked synchronously on the thread
     * that called [submitCommand] / [advance]; long-running work should
     * be deferred to another thread by the listener.
     */
    public override fun subscribe(listener: (GameEvent) -> Unit): Subscription {
        listeners += listener
        return object : Subscription {
            override fun unsubscribe() {
                listeners.remove(listener)
            }
        }
    }

    public override fun submitCommand(command: GameCommand): CommandResult {
        if (_matchOver) return CommandResult.Rejected(CommandRejection.MatchOver)
        val handler = handlers[_currentPhaseIndex]
        if (!handler.accepts(command, _turnState)) {
            return CommandResult.Rejected(CommandRejection.WrongPhase(handler.phase))
        }
        val active = handler.activePlayer(_turnState)
        if (active != null && active != command.playerId) {
            return CommandResult.Rejected(CommandRejection.NotYourTurn(activePlayer = active, attemptedBy = command.playerId))
        }
        handler.validate(command, _gameState, _turnState)?.let { reason ->
            return CommandResult.Rejected(reason)
        }
        val outcome = handler.apply(command, _gameState, _turnState, roller)
        logEvents(outcome.events)
        _gameState = outcome.state
        _turnState = outcome.turn
        val events = outcome.events.toMutableList()
        events.addAll(runDestructionSweep())
        events.addAll(cascade())
        dispatch(events)
        return CommandResult.Accepted(events)
    }

    /**
     * Records [event] in the log at the current turn number and dispatches
     * it to subscribers, without touching game/turn state or the phase
     * cascade. For out-of-band happenings (e.g. a network server annotating
     * a connect/disconnect) that deliveries want to land in the same
     * chronological log subscribers already see everything else through.
     */
    public fun annotate(event: GameEvent) {
        _gameLog.append(LogEntry(_turnState.turnNumber, event))
        dispatch(listOf(event))
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
            events.addAll(runDestructionSweep())
        }
        events.addAll(cascade())
        dispatch(events)
        return events
    }

    private fun logEvents(events: List<GameEvent>) {
        for (event in events) {
            _gameLog.append(LogEntry(_turnState.turnNumber, event))
        }
    }

    private fun dispatch(events: List<GameEvent>) {
        if (events.isEmpty() || listeners.isEmpty()) return
        // Iterate over a snapshot so listeners that unsubscribe themselves
        // mid-dispatch don't trip a ConcurrentModificationException.
        val snapshot = listeners.toList()
        for (event in events) for (listener in snapshot) listener(event)
    }

    private fun cascade(): List<GameEvent> {
        val events = mutableListOf<GameEvent>()
        while (!_matchOver && handlers[_currentPhaseIndex].isComplete(_turnState)) {
            events += advanceIndex()
            events.addAll(fireOnEntry())
            events.addAll(runDestructionSweep())
        }
        return events
    }

    private fun fireOnEntry(): List<GameEvent> {
        val outcome = handlers[_currentPhaseIndex].onEntry(_gameState, _turnState, roller)
        logEvents(outcome.events)
        _gameState = outcome.state
        _turnState = outcome.turn
        _needsOnEntry = false
        return outcome.events
    }

    /**
     * Centralized destruction check, run after every state-mutating step
     * (command application, phase `onEntry`) so kills are caught regardless
     * of source (weapon/physical damage, ammo cook-off, future crit/pilot
     * conditions) without duplicating detect+flag+event+match-over logic in
     * every handler. Flips newly-destroyed units' [battletech.tactical.unit.CombatUnit.isDestroyed],
     * emits one [UnitDestroyed] per newly destroyed unit, then asks the pure
     * [battletech.tactical.model.victoryStatus] rule whether the match just ended; if so, sets
     * [_matchOver] and emits [MatchEnded] exactly once. Idempotent — a sweep with nothing newly
     * destroyed and the match already decided returns an empty list.
     */
    private fun runDestructionSweep(): List<GameEvent> {
        if (_matchOver) return emptyList()

        val events = mutableListOf<GameEvent>()
        val newlyDestroyed = _gameState.units.filter { !it.isDestroyed && destructionReason(it) != null }

        if (newlyDestroyed.isNotEmpty()) {
            val destroyedIds = newlyDestroyed.map { it.id }.toSet()
            _gameState = _gameState.copy(
                units = _gameState.units.map { unit ->
                    if (unit.id in destroyedIds) unit.copy(isDestroyed = true) else unit
                },
            )
            for (unit in newlyDestroyed) {
                events += UnitDestroyed(unit.id, destructionReason(unit)!!)
            }
        }

        val status = victoryStatus(_gameState)
        if (status is MatchStatus.Ended) {
            _matchOver = true
            events += MatchEnded(status.outcome)
        }

        logEvents(events)
        return events
    }

    private fun advanceIndex(): GameEvent {
        val from = handlers[_currentPhaseIndex].phase
        _currentPhaseIndex = (_currentPhaseIndex + 1) % handlers.size
        _needsOnEntry = true
        val event = PhaseChanged(from, handlers[_currentPhaseIndex].phase)
        logEvents(listOf(event))
        return event
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
