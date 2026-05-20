package battletech.tactical.attack.physical

import battletech.tactical.action.ActionPreview

public data class PhysicalAttackPreview(
    public val expectedDamage: IntRange,
) : ActionPreview
