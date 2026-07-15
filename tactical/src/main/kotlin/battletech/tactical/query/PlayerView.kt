package battletech.tactical.query

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.movement.ReachabilityMap
import battletech.tactical.unit.UnitId

/**
 * Read-side surface scoped to one [PlayerId]. Deliveries (TUI, web, remote
 * client) consume this to answer "what is legal right now?" without
 * reaching into raw [GameState].
 *
 * The game is open-information: every implementation returns the same data
 * for any [playerId]. `playerId` is the view's identity for convenience
 * (whose turn is this, whose units are "mine" for display purposes), not an
 * access boundary — there is no hidden information being withheld.
 */
public interface PlayerView {
    public val playerId: PlayerId
    public val state: GameState

    /** Reachability map for each available movement mode, in WALK→RUN→JUMP
     *  order, skipping modes the unit cannot perform. */
    public fun legalMovementsFor(unitId: UnitId): List<ReachabilityMap>

    /** Hexes in the attacker's forward firing arc given a torso facing. */
    public fun fireArc(attackerId: UnitId, torsoFacing: HexDirection): Set<HexCoordinates>

    /** Enemy unit IDs in arc AND having at least one weapon that can engage. */
    public fun validTargets(attackerId: UnitId, torsoFacing: HexDirection): Set<UnitId>

    /** Full target/weapon legality and success-chance data for the cursor UI. */
    public fun targetInfos(attackerId: UnitId, torsoFacing: HexDirection): List<TargetInfo>

    /** Physical-attack options (punch per arm, kick) against each adjacent enemy. */
    public fun physicalAttackOptions(attackerId: UnitId): List<PhysicalAttackOption>

    /**
     * Legal torso facings for [unitId]: its leg facing (no twist) or ±1
     * hexside either way. Empty if the unit doesn't exist. Single source
     * shared with the impulse-commit validation
     * ([battletech.tactical.attack.ImpulseAttackPhaseHandler.validateTorsoFacings])
     * via [battletech.tactical.attack.torsoTwistOptions] so the TUI's twist
     * input handling can never drift from what the server will accept.
     */
    public fun legalTorsoFacings(unitId: UnitId): Set<HexDirection>

    /**
     * Committed weapon-attack declarations for the current attack impulse sequence, across both
     * players, grouped one entry per (attacker, target) pair and ordered by impulse-commit
     * player order then attacker id — the projection the DECLARED TARGETS panel renders
     * alongside the viewing player's own in-progress (uncommitted) drafts, which stay
     * client-side (see [battletech.tactical.session.GameCommand]'s transient-UI-workflow carve-out).
     */
    public fun declaredWeaponAttacks(): List<DeclaredWeaponAttack>

    /** Positions of the given unit IDs on the board. */
    public fun resolveTargetPositions(targetIds: Set<UnitId>): Set<HexCoordinates>

    /** Public projection of a unit — fields visible to all players. Returns null if not found. */
    public fun publicUnit(unitId: UnitId): PublicUnit?
}
