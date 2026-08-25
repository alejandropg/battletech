package battletech.tactical.attack.physical

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What kind of physical attack is being made, and with which limb. */
@Serializable
public sealed interface PhysicalAttackKind {
    @Serializable
    @SerialName("punch")
    public data class Punch(public val arm: Side) : PhysicalAttackKind
    @Serializable
    @SerialName("kick")
    public data class Kick(public val leg: Side) : PhysicalAttackKind
}
