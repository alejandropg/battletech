package battletech.tactical.action.attack

import battletech.tactical.action.ActionId
import battletech.tactical.action.ActionOption
import battletech.tactical.action.ActionPreview
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.CombatUnit
import battletech.tactical.action.RuleResult
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnavailableAction
import battletech.tactical.model.GameState

public interface AttackDefinition<C : AttackContext> {
    public val phase: TurnPhase
    public val name: String
    public val rules: List<AttackRule<C>>

    public fun expand(actor: CombatUnit, gameState: GameState): List<C>
    public fun preview(context: C): ActionPreview
    public fun successChance(context: C): Int
    public fun actionName(context: C): String

    public fun evaluateAll(actor: CombatUnit, gameState: GameState): List<ActionOption> {
        return expand(actor, gameState).map { context ->
            val results = rules.map { rule -> rule.evaluate(context) }
            val reasons = results.filterIsInstance<RuleResult.Unsatisfied>().map { it.reason }
            val warnings = results.filterIsInstance<RuleResult.Penalized>().map { it.warning }
            val name = actionName(context)
            val id = ActionId(name)

            if (reasons.isNotEmpty()) {
                UnavailableAction(id = id, name = name, reasons = reasons)
            } else {
                AvailableAction(
                    id = id,
                    name = name,
                    successChance = successChance(context),
                    warnings = warnings,
                    preview = preview(context),
                )
            }
        }
    }
}
