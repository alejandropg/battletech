package battletech.tactical.action

public sealed interface RuleResult {
    public data object Satisfied : RuleResult
    public data class Unsatisfied(public val reason: UnavailabilityReason) : RuleResult
    public data class Penalized(public val warning: Warning) : RuleResult
}
