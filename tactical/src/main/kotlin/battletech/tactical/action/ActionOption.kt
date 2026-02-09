package battletech.tactical.action

public sealed interface ActionOption {
    val id: ActionId
    val name: String
}

public data class AvailableAction(
    override val id: ActionId,
    override val name: String,
    val successChance: Int,
    val warnings: List<Warning>,
    val preview: ActionPreview,
) : ActionOption

public data class UnavailableAction(
    override val id: ActionId,
    override val name: String,
    val reasons: List<UnavailabilityReason>,
) : ActionOption
