package battletech.tactical.action

import battletech.tactical.command.RuleRejection

public sealed interface RuleResult {
    public data object Satisfied : RuleResult
    public data class Unsatisfied(public val reason: RuleRejection) : RuleResult
    public data class Penalized(public val warning: Warning) : RuleResult
}
