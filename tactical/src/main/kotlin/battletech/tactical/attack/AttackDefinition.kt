package battletech.tactical.attack

import battletech.tactical.query.RuleResult
import battletech.tactical.session.RuleRejection

public interface AttackDefinition<C : AttackContext> {
    public val name: String
    public val rules: List<AttackRule<C>>

    /**
     * Runs all rules against a single context and returns the first rejection,
     * or null if the action is legal.
     *
     * Used by attack handlers and the query layer to gate execution on a single
     * context without collecting the full set of reasons.
     */
    public fun firstRejection(context: C): RuleRejection? =
        rules.asSequence()
            .map { it.evaluate(context) }
            .filterIsInstance<RuleResult.Unsatisfied>()
            .map { it.reason }
            .firstOrNull()
}
