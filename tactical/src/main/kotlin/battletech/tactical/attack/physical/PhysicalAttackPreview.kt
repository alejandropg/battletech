package battletech.tactical.attack.physical

import battletech.tactical.query.ActionPreview

public data class PhysicalAttackPreview(
    public val expectedDamage: IntRange,
) : ActionPreview
