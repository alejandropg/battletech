package battletech.tactical.movement

import battletech.tactical.heat.movementHeatSources
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.UnitId

public fun GameState.moveUnit(unitId: UnitId, destination: ReachableHex): GameState =
    copy(
        units = units.mapUnits { unit ->
            if (unit.id == unitId) {
                unit.copy(position = destination.position, facing = destination.facing, torsoFacing = destination.facing)
            } else {
                unit
            }
        },
    )

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
    val unit = units.byId(unitId)
    val hexes = hexesMoved(unit.position, destination)
    val heatSources = movementHeatSources(mode, hexes)
    val moved = moveUnit(unitId, destination)
    // mpSpent == 0 is the unique discriminator for a genuine stay-put: every turn-in-place,
    // walk, or jump step costs >=1 MP, so only a true "declare stationary" hits 0. Per
    // docs/rules/to-hit-modifiers.md ("Stationary attacker -> +0"), that must be recorded as
    // Stationary, not Moved(mode, 0) (which would wrongly apply the +1/+2/+3 movement modifier).
    val movementThisTurn = if (destination.mpSpent == 0) {
        MovementThisTurn.Stationary
    } else {
        MovementThisTurn.Moved(mode, hexes)
    }
    return moved.copy(
        units = moved.units.mapUnits { u ->
            if (u.id == unitId) {
                u.copy(
                    movementThisTurn = movementThisTurn,
                    heatGeneratedThisTurn = u.heatGeneratedThisTurn + heatSources,
                )
            } else {
                u
            }
        },
    )
}
