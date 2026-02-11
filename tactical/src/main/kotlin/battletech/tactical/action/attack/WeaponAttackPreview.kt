package battletech.tactical.action.attack

import battletech.tactical.action.ActionPreview

public data class WeaponAttackPreview(
    public val expectedDamage: IntRange,
    public val heatGenerated: Int,
    public val ammoConsumed: Int?,
) : ActionPreview
