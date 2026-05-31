package battletech.tactical.attack.physical

import battletech.tactical.unit.UnitId

/** Left or right limb used for a physical attack. */
public enum class Side { LEFT, RIGHT }

/** What kind of physical attack is being made, and with which limb. */
public sealed interface PhysicalAttackKind {
    public data class Punch(public val arm: Side) : PhysicalAttackKind
    public data class Kick(public val leg: Side) : PhysicalAttackKind
}

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
