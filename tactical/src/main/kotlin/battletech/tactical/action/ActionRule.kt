package battletech.tactical.action

public interface ActionRule {
    fun evaluate(context: ActionContext): RuleResult
}
