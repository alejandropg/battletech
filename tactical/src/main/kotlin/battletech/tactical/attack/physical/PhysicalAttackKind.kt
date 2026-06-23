package battletech.tactical.attack.physical

/** What kind of physical attack is being made, and with which limb. */
public sealed interface PhysicalAttackKind {
    public data class Punch(public val arm: Side) : PhysicalAttackKind
    public data class Kick(public val leg: Side) : PhysicalAttackKind
}
