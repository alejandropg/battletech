package battletech.tactical.attack

import battletech.tactical.rules.RuleResult

public interface AttackRule<in C : AttackContext> {
    public fun evaluate(context: C): RuleResult
}
