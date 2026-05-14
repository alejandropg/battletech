package battletech.tui.game.phase

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.calculateMovementOrder
import battletech.tactical.action.rollInitiative
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.ImpulseSequence
import battletech.tui.game.TurnState

public data object InitiativePhase : Phase {
    override val turnPhase: TurnPhase = TurnPhase.INITIATIVE

    override fun tick(app: AppState, svc: PhaseServices): Transition {
        val initiative = rollInitiative(svc.random)

        val loserCount = app.gameState.unitsOf(initiative.loser).size
        val winnerCount = app.gameState.unitsOf(initiative.winner).size
        val movementOrder = calculateMovementOrder(
            loser = initiative.loser, loserUnitCount = loserCount,
            winner = initiative.winner, winnerUnitCount = winnerCount,
        )

        val turnState = TurnState(
            initiativeResult = initiative,
            movementSequence = ImpulseSequence(movementOrder),
        )

        val p1Roll = initiative.rolls[PlayerId.PLAYER_1]!!
        val p2Roll = initiative.rolls[PlayerId.PLAYER_2]!!
        val loserName = if (initiative.loser == PlayerId.PLAYER_1) "P1" else "P2"
        val flash = FlashMessage("Initiative: P1 rolled $p1Roll, P2 rolled $p2Roll — $loserName moves first")

        return Transition(
            app = app.copy(turnState = turnState, phase = MovementPhase.SelectingUnit),
            flash = flash,
        )
    }

    override fun prompt(app: AppState): String = "Rolling initiative..."
}
