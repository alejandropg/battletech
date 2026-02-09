package battletech.tactical.action

public data class ActionPreview(
    public val expectedDamage: IntRange? = null,
    public val heatGenerated: Int? = null,
    public val ammoConsumed: Int? = null,
    public val targetHitLocation: String? = null,
)
