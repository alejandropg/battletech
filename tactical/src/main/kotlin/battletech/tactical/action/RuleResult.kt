package battletech.tactical.action

public sealed interface RuleResult {
    public data object Satisfied : RuleResult
    public data class Unsatisfied(val reason: UnavailabilityReason) : RuleResult
    public data class Penalized(val warning: Warning) : RuleResult
}
