package battletech.network.wire

import battletech.tactical.model.GameMap
import battletech.tactical.model.PlayerId
import battletech.tactical.session.LogEntry
import battletech.tactical.unit.MechModel
import kotlinx.serialization.Serializable

/**
 * The complete host-authoritative state required to start one client's match replica.
 *
 * [mechModels] and [map] are immutable match content sent only at join. [snapshot] and [log]
 * are the joining player's initial projected state; later changes arrive as
 * [ServerMessage.StatePush] messages. Unit positions are part of [GameSnapshot.units], so the
 * source game file never crosses the wire.
 */
@Serializable
public data class MatchBootstrap(
    public val playerId: PlayerId,
    public val mechModels: List<MechModel>,
    public val map: GameMap,
    public val snapshot: GameSnapshot,
    public val log: List<LogEntry>,
) {
    init {
        val repeatedVariant = mechModels
            .groupingBy(MechModel::variant)
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        require(repeatedVariant == null) {
            "Mech variant is repeated in match bootstrap: $repeatedVariant"
        }
    }
}
