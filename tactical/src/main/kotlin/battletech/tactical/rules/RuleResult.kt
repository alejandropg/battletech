package battletech.tactical.rules

public sealed interface RuleResult {
    public data object Satisfied : RuleResult
    public data class Unsatisfied(public val reason: RuleRejection) : RuleResult
    public data class Penalized(public val warning: Warning) : RuleResult
}
