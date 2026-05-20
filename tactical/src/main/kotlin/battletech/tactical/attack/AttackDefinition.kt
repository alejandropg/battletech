package battletech.tactical.attack

import battletech.tactical.query.ActionId
import battletech.tactical.query.ActionOption
import battletech.tactical.query.ActionPreview
import battletech.tactical.query.AvailableAction
import battletech.tactical.model.CombatUnit
import battletech.tactical.query.RuleResult
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.UnavailableAction
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
