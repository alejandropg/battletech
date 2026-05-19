package battletech.tactical.session

import battletech.tactical.action.PlayerId
import battletech.tactical.action.attack.resolveAttacks
import battletech.tactical.command.CommandRejection
import battletech.tactical.command.CommandResult
import battletech.tactical.command.CommitAttackImpulse
import battletech.tactical.command.GameCommand
import battletech.tactical.command.MoveUnit
import battletech.tactical.dice.DiceRoller
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.event.AttackDeclarationsRecorded
import battletech.tactical.event.AttacksResolved
import battletech.tactical.event.GameEvent
import battletech.tactical.event.TorsoFacingsApplied
import battletech.tactical.event.UnitMoved
import battletech.tactical.model.GameState
import battletech.tactical.model.applyTorsoFacings
import battletech.tactical.view.DefaultPlayerView
import battletech.tactical.view.PlayerView

/**
 * The authoritative aggregate for a single match. Holds [GameState] and
 * [TurnState] privately; deliveries mutate state only by submitting a
 * [GameCommand]. Reads happen through [viewFor] (per-player) or the
 * convenience [gameState]/[turnState] accessors.
 *
 * Threading: not internally synchronised. Callers must serialise commands.
 * For hot-seat TUI this is trivially true (single-threaded loop); a future
 * web/server delivery should wrap one session per match in a single-threaded
 * actor/coroutine.
 *
 * This is the PR5 skeleton: a `when (cmd)` dispatch with phase-progression
 * logic still hand-rolled inside. PR6 extracts that into [PhaseHandler]
 * strategies and adds an auto-advance cascade.
 */
public class BattleSession(
    initialGameState: GameState,
    initialTurnState: TurnState = TurnState.NULL,
    private val roller: DiceRoller = RandomDiceRoller(),
) {

    private var _gameState: GameState = initialGameState
    private var _turnState: TurnState = initialTurnState
    private var _matchOver: Boolean = false

    public val gameState: GameState get() = _gameState
    public val turnState: TurnState get() = _turnState
    public val isMatchOver: Boolean get() = _matchOver

    public fun viewFor(playerId: PlayerId): PlayerView = DefaultPlayerView(playerId, _gameState)

    public fun submitCommand(command: GameCommand): CommandResult {
        if (_matchOver) return CommandResult.Rejected(CommandRejection.MatchOver)
        return when (command) {
            is MoveUnit -> applyMoveUnit(command)
            is CommitAttackImpulse -> applyCommitAttackImpulse(command)
        }
    }

    /**
     * Transitional escape hatch the TUI uses for state mutations that aren't
     * yet modelled as commands: rolling initiative, dissipating heat,
     * resetting at end of turn, building the in-progress attack draft.
     *
     * Removed in PR7 when the TUI loses its phase-progression logic and all
     * mutations flow through [submitCommand] (via PR6 PhaseHandlers).
     */
    @Suppress("DEPRECATION_ERROR")
    @Deprecated(
        "Transitional escape hatch; only the TUI's phase-progression code " +
            "should call this. PR7 removes it.",
    )
    public fun applyMutation(transform: (GameState, TurnState) -> Pair<GameState, TurnState>) {
        val (g, t) = transform(_gameState, _turnState)
        _gameState = g
        _turnState = t
    }

    private fun applyMoveUnit(cmd: MoveUnit): CommandResult {
        val unit = _gameState.unitById(cmd.unitId)
            ?: return CommandResult.Rejected(CommandRejection.UnknownUnit(cmd.unitId))
        if (unit.owner != cmd.playerId) {
            return CommandResult.Rejected(
                CommandRejection.NotYourTurn(activePlayer = unit.owner, attemptedBy = cmd.playerId),
            )
        }
        if (cmd.unitId in _turnState.movedUnitIds) {
            return CommandResult.Rejected(CommandRejection.UnitAlreadyActed(cmd.unitId))
        }

        val from = unit.position
        _gameState = _gameState.moveUnit(cmd.unitId, cmd.destination)
        _turnState = _turnState.advanceAfterUnitMoved(cmd.unitId)

        val event = UnitMoved(
            unitId = cmd.unitId,
            from = from,
            to = cmd.destination.position,
            finalFacing = cmd.destination.facing,
            mode = cmd.mode,
            mpSpent = cmd.destination.mpSpent,
        )
        return CommandResult.Accepted(listOf(event))
    }

    private fun applyCommitAttackImpulse(cmd: CommitAttackImpulse): CommandResult {
        val events = mutableListOf<GameEvent>()

        if (cmd.torsoFacings.isNotEmpty()) {
            _gameState = _gameState.applyTorsoFacings(cmd.torsoFacings)
            events += TorsoFacingsApplied(cmd.torsoFacings)
        }

        val accumulated = _turnState.attackDeclarations + cmd.declarations
        _turnState = _turnState.copy(
            attackDeclarations = accumulated,
            attackImpulse = null,
            attackSequence = _turnState.attackSequence.advance(),
        )

        events += AttackDeclarationsRecorded(player = cmd.playerId, count = cmd.declarations.size)

        // Match the TUI's current behaviour: weapon attacks resolve at the end
        // of the phase (when this is the final impulse). Physical attacks are
        // dropped (the existing engine doesn't apply physical damage yet).
        if (_turnState.attackSequence.isComplete && cmd.isWeaponPhase && accumulated.isNotEmpty()) {
            val (resolvedGameState, results) = resolveAttacks(accumulated, _gameState, roller)
            _gameState = resolvedGameState
            _turnState = _turnState.copy(attackDeclarations = emptyList())
            events += AttacksResolved(results)
        } else if (_turnState.attackSequence.isComplete && !cmd.isWeaponPhase) {
            _turnState = _turnState.copy(attackDeclarations = emptyList())
        }

        return CommandResult.Accepted(events)
    }
}
