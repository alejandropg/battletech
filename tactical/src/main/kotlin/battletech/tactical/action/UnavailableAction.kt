package battletech.tactical.action

import battletech.tactical.session.RuleRejection

public data class UnavailableAction(
    override val id: ActionId,
    override val name: String,
    public val reasons: List<RuleRejection>,
) : ActionOption
