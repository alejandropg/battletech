package battletech.tactical.action

public sealed interface ActionOption {
    val id: ActionId
    val name: String
}

public data class AvailableAction(
    override val id: ActionId,
    override val name: String,
    public val successChance: Int,
    public val warnings: List<Warning>,
    public val preview: ActionPreview,
) : ActionOption

public data class UnavailableAction(
    override val id: ActionId,
    override val name: String,
    public val reasons: List<UnavailabilityReason>,
) : ActionOption
