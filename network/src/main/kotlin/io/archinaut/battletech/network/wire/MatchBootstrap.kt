package io.archinaut.battletech.network.wire

import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.model.content.AssetRegistry
import io.archinaut.battletech.tactical.session.LogEntry
import kotlinx.serialization.Serializable

/**
 * The complete host-authoritative state required to start one client's match replica.
 *
 * [registry] and [map] are immutable match content sent only at join. [snapshot] and [log]
 * are the joining player's initial projected state; later changes arrive as
 * [ServerMessage.StatePush] messages. Unit positions are part of [GameSnapshot.units], so the
 * source game file never crosses the wire.
 */
@Serializable
public data class MatchBootstrap(
    public val playerId: PlayerId,
    public val registry: AssetRegistry,
    public val map: GameMap,
    public val snapshot: GameSnapshot,
    public val log: List<LogEntry>,
)
