package battletech.tactical.attack.physical

import battletech.tactical.unit.UnitId

/**
 * A single declared physical attack: [attackerId] strikes [targetId] using
 * [kind] (punch with an arm, or kick with a leg). Unlike weapon
 * [battletech.tactical.attack.AttackDeclaration]s, physical declarations carry
 * no weapon index — the limb and attack kind fully describe the strike.
 */
public data class PhysicalAttackDeclaration(
    public val attackerId: UnitId,
    public val targetId: UnitId,
    public val kind: PhysicalAttackKind,
)
