package battletech.tactical.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Recorded locally when a host-supplied [variant] exists in this client's catalog with different
 * content. The host definition remains authoritative; this event is informational only.
 */
@Serializable
@SerialName("mechModelMismatch")
public data class MechModelMismatch(
    public val variant: String,
) : GameEvent
