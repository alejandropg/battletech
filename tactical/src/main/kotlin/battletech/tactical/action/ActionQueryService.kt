package battletech.tactical.action

import battletech.tactical.attack.AttackDefinition
import battletech.tactical.movement.MoveActionDefinition
import battletech.tactical.model.GameState

public class ActionQueryService(
    private val movementDefinition: MoveActionDefinition,
    private val attackDefinitions: List<AttackDefinition<*>>,
) {

    public fun getMovementActions(unit: CombatUnit, gameState: GameState): PhaseActionReport {
        val actions = movementDefinition.expand(unit, gameState).map { context ->
            AvailableAction(
                id = ActionId(movementDefinition.actionName(context)),
                name = movementDefinition.actionName(context),
                successChance = 100,
                warnings = emptyList(),
                preview = movementDefinition.preview(context),
            )
        }

        return PhaseActionReport(
            phase = TurnPhase.MOVEMENT,
            unitId = unit.id,
            actions = actions,
        )
    }

    public fun getAttackActions(unit: CombatUnit, phase: TurnPhase, gameState: GameState): PhaseActionReport {
        val actions = attackDefinitions
            .filter { it.phase == phase }
            .flatMap { definition -> definition.evaluateAll(unit, gameState) }

        return PhaseActionReport(
            phase = phase,
            unitId = unit.id,
            actions = actions,
        )
    }
}
