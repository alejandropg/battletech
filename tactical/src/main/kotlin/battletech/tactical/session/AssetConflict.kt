package battletech.tactical.session

import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.AssetKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A contributed asset collided with one already in the match's registry: same id, different
 * content. The registered definition stays authoritative; this is informational only.
 */
@Serializable
@SerialName("assetConflict")
public data class AssetConflict(
    public val kind: AssetKind,
    public val id: String,
    public val player: PlayerId,
) : GameEvent
