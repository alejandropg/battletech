package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.Impulse
import battletech.tactical.session.Initiative
import battletech.tactical.session.PhaseHandler
import battletech.tactical.session.PhaseOutcome
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnState
import battletech.tactical.session.calculateAttackOrder
import battletech.tactical.unit.UnitId

/**
 * Base for attack phases driven by an alternating impulse sequence
 * (weapon fire, physical attacks). On entry (re-)seeds [TurnState.attackSequence]
 * from initiative; the re-seed-when-complete guard lets the physical phase
 * reuse the sequence the weapon phase finished. Stateless.
 */
public abstract class ImpulseAttackPhaseHandler : PhaseHandler {

    /** Return true if [command] is the concrete command type this handler processes. */
    protected abstract fun acceptsCommand(command: GameCommand): Boolean

    final override fun activePlayer(turn: TurnState): PlayerId? =
        if (!turn.attack.inProgress) null
        else turn.attack.activePlayer

    final override fun accepts(command: GameCommand, turn: TurnState): Boolean =
        acceptsCommand(command) && turn.attack.inProgress

    final override fun isComplete(turn: TurnState): Boolean =
        turn.attack.sequence.order.isNotEmpty() && turn.attack.isComplete

    final override fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome {
        if (turn.attack.inProgress) {
            return PhaseOutcome(state, turn, emptyList())
        }
        val newTurn = turn.copy(attack = turn.attack.seed(attackOrderFor(turn.initiative, state)))
        return PhaseOutcome(state, newTurn, emptyList())
    }

    /**
     * Applies torso facings to [state] when [facings] is non-empty, appending
     * a [TorsoFacingsApplied] event to [events]. Returns the (possibly updated) state.
     */
    protected fun applyTorsoFacingsStep(
        state: GameState,
        facings: Map<UnitId, HexDirection>,
        events: MutableList<GameEvent>,
    ): GameState {
        if (facings.isEmpty()) return state
        events += TorsoFacingsApplied(facings)
        return state.applyTorsoFacings(facings)
    }
}

internal fun attackOrderFor(initiative: Initiative, state: GameState): List<Impulse> {
    val loser = initiative.loser
    val winner = initiative.winner
    // Shutdown 'Mechs can't fire, so they don't take an impulse slot.
    return calculateAttackOrder(
        loser = loser,
        loserUnitCount = state.activeUnitsOf(loser).size,
        winner = winner,
        winnerUnitCount = state.activeUnitsOf(winner).size,
    )
}
