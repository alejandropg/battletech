package battletech.tactical.action

public interface ActionRule {
    public fun evaluate(context: ActionContext): RuleResult
}
