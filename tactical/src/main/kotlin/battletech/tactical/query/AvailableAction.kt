package battletech.tactical.query

public data class AvailableAction(
    override val id: ActionId,
    override val name: String,
    public val successChance: Int,
    public val warnings: List<Warning>,
    public val preview: ActionPreview,
) : ActionOption
