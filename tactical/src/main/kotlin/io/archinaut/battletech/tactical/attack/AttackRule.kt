package io.archinaut.battletech.tactical.attack

import io.archinaut.battletech.tactical.rules.RuleResult

public interface AttackRule<in C : AttackContext> {
    public fun evaluate(context: C): RuleResult
}
