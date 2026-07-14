package battletech.tactical.movement

import battletech.tactical.heat.movementHeatSources
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.model.mapUnits
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.UnitId

public fun GameState.moveUnit(unitId: UnitId, destination: ReachableHex): GameState =
    mapUnits { unit ->
        if (unit.id == unitId) {
            unit.copy(position = destination.position, facing = destination.facing, torsoFacing = destination.facing)
        } else {
            unit
        }
    }

/** Hexes actually entered along [destination]'s path (turn-in-place steps excluded). */
public fun hexesMoved(from: HexCoordinates, destination: ReachableHex): Int {
    val positions = listOf(from) + destination.path.map { it.position }
    return positions.zipWithNext().count { (previous, next) -> previous != next }
}

/**
 * Full move-effects transform for a unit's [destination] in [mode]: repositions
 * the unit ([moveUnit]), records this turn's [MovementThisTurn.Moved] (mode +
 * hexes actually entered), and accrues the movement's heat sources. [destination]
 * must already be server-authoritative (see [MovementRules.authoritativeDestination]) —
 * this transform trusts it without re-deriving reachability.
 */
public fun GameState.applyMove(unitId: UnitId, mode: MovementMode, destination: ReachableHex): GameState {
    val unit = unitById(unitId)
    val hexes = hexesMoved(unit.position, destination)
    val heatSources = movementHeatSources(mode, hexes)
    return moveUnit(unitId, destination).mapUnits { u ->
        if (u.id == unitId) {
            u.copy(
                movementThisTurn = MovementThisTurn.Moved(mode, hexes),
                heatGeneratedThisTurn = u.heatGeneratedThisTurn + heatSources,
            )
        } else {
            u
        }
    }
}
