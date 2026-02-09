package battletech.tactical.action

public data class UnavailableAction(
    override val id: ActionId,
    override val name: String,
    public val reasons: List<UnavailabilityReason>,
) : ActionOption
