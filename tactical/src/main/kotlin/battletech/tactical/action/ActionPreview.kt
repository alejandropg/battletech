package battletech.tactical.action

public data class ActionPreview(
    val expectedDamage: IntRange? = null,
    val heatGenerated: Int? = null,
    val ammoConsumed: Int? = null,
    val targetHitLocation: String? = null,
)
