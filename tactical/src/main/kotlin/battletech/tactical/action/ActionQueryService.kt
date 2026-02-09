package battletech.tactical.action

import battletech.tactical.model.GameState

public class ActionQueryService(
    private val definitions: List<ActionDefinition>
) {

    public fun getActions(unit: Unit, phase: TurnPhase, gameState: GameState): PhaseActionReport {
        val actions = definitions
            .filter { it.phase == phase }
            .flatMap { definition -> expandAndEvaluate(definition, unit, gameState) }

        return PhaseActionReport(
            phase = phase,
            unitId = unit.id,
            actions = actions,
        )
    }

    private fun expandAndEvaluate(
        definition: ActionDefinition,
        unit: Unit,
        gameState: GameState,
    ): List<ActionOption> {
        return definition.expand(unit, gameState).map { context ->
            val results = definition.rules.map { rule -> rule.evaluate(context) }

            val reasons = results.filterIsInstance<RuleResult.Unsatisfied>().map { it.reason }
            val warnings = results.filterIsInstance<RuleResult.Penalized>().map { it.warning }

            val actionName = definition.actionName(context)
            val actionId = ActionId(actionName)

            if (reasons.isNotEmpty()) {
                UnavailableAction(
                    id = actionId,
                    name = actionName,
                    reasons = reasons,
                )
            } else {
                AvailableAction(
                    id = actionId,
                    name = actionName,
                    successChance = definition.successChance(context),
                    warnings = warnings,
                    preview = definition.preview(context),
                )
            }
        }
    }
}
