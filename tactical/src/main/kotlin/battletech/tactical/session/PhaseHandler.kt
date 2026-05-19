package battletech.tactical.session

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.command.CommandRejection
import battletech.tactical.command.GameCommand
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState

/**
 * One handler per [TurnPhase] of a turn. The [BattleSession] holds a list of
 * handlers in canonical phase order and delegates commands to the current
 * one. Adding a new phase = adding a handler and registering it; no edits
 * to existing handlers or the session (OCP).
 *
 * Handlers are stateless — all data flows through their arguments and
 * the [PhaseOutcome] they return.
 */
public interface PhaseHandler {

    public val phase: TurnPhase

    /** The player whose intent the session is waiting for, if any. System
     *  phases (Initiative/Heat/End) return null. */
    public fun activePlayer(turn: TurnState): PlayerId?

    /** True if [command] is one this handler is willing to process now. The
     *  session uses this to decide whether to dispatch or reject with
     *  [battletech.tactical.command.CommandRejection.WrongPhase]. */
    public fun accepts(command: GameCommand, turn: TurnState): Boolean

    /** Command-specific validation run after [accepts] passes but before
     *  [apply]. Return null to accept; return a [CommandRejection] to
     *  reject with that reason (e.g., UnknownUnit, NotYourTurn,
     *  UnitAlreadyActed). Default: no validation. */
    public fun validate(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
    ): CommandRejection? = null

    /** Apply a command this handler [accepts] and [validate]s. Caller has
     *  already checked both; implementations may assume the command is
     *  legal at this point. */
    public fun apply(
        command: GameCommand,
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome

    /** True when the phase has nothing more to do. The session uses this to
     *  drive the auto-advance cascade. */
    public fun isComplete(turn: TurnState): Boolean

    /** Called once when the session enters this phase, before any commands.
     *  System phases use this to do their on-entry work (roll initiative,
     *  dissipate heat, reset for next turn, seed attack sequence). Default
     *  is no-op for player phases. */
    public fun onEntry(
        state: GameState,
        turn: TurnState,
        roller: DiceRoller,
    ): PhaseOutcome = PhaseOutcome(state, turn, emptyList())
}
