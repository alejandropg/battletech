package battletech.tactical.query

import battletech.tactical.model.PlayerId
import battletech.tactical.model.UnitId
import battletech.tactical.attack.TargetInfo
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.movement.ReachabilityMap

/**
 * Read-side surface scoped to one [PlayerId]. Deliveries (TUI, web, remote
 * client) consume this to answer "what is legal right now?" without
 * reaching into raw [battletech.tactical.model.GameState].
 *
 * Today every implementation returns the same data regardless of [playerId];
 * the hidden-info redaction kicks in with PR8 once subscribers exist.
 */
public interface PlayerView {
    public val playerId: PlayerId
    public val state: PublicGameState

    /** Reachability map for each available movement mode, in WALK→RUN→JUMP
     *  order, skipping modes the unit cannot perform. */
    public fun legalMovementsFor(unitId: UnitId): List<ReachabilityMap>

    /** Hexes in the attacker's forward firing arc given a torso facing. */
    public fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates>

    /** Enemy unit IDs in arc AND having at least one weapon that can engage. */
    public fun validTargets(attackerId: UnitId, torsoFacing: HexDirection): Set<UnitId>

    /** Full target/weapon legality and success-chance data for the cursor UI. */
    public fun targetInfos(attackerId: UnitId, torsoFacing: HexDirection): List<TargetInfo>

    /** Positions of the given unit IDs on the board. */
    public fun resolveTargetPositions(targetIds: Set<UnitId>): Set<HexCoordinates>
}
