package battletech.tactical.action.attack

import battletech.tactical.action.ActionPreview

public data class PhysicalAttackPreview(
    public val expectedDamage: IntRange,
) : ActionPreview
