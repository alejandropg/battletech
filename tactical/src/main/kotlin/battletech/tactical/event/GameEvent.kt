package battletech.tactical.event

import battletech.tactical.action.Initiative
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackResult
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode

/**
 * Passive narration of something that happened in the session. Events are
 * the canonical channel by which clients learn about state changes;
 * see [battletech.tactical.command.CommandResult.Accepted].
 *
 * Events describe past facts only — they are never commands in disguise.
 */
public sealed interface GameEvent

public data class UnitMoved(
    public val unitId: UnitId,
    public val from: HexCoordinates,
    public val to: HexCoordinates,
    public val finalFacing: HexDirection,
    public val mode: MovementMode,
    public val mpSpent: Int,
) : GameEvent

public data class AttacksResolved(public val results: List<AttackResult>) : GameEvent

public data class AttackDeclarationsRecorded(public val player: PlayerId, public val count: Int) : GameEvent

public data class TorsoFacingsApplied(public val facings: Map<UnitId, HexDirection>) : GameEvent
