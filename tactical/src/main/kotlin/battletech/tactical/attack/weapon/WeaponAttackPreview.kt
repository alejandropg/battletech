package battletech.tactical.attack.weapon

import battletech.tactical.query.ActionPreview

public data class WeaponAttackPreview(
    public val expectedDamage: IntRange,
    public val heatGenerated: Int,
    public val ammoConsumed: Int?,
) : ActionPreview
