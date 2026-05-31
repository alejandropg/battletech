package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.physical.PhysicalAttackDeclaration
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.movement.MovementMode
import battletech.tactical.movement.ReachableHex
import battletech.tactical.unit.UnitId

/**
 * A player intent submitted to a [BattleSession].
 * Coarse and commit-on-intent: each command represents a real game decision
 * the session validates and applies as one atomic step.
 *
 * The transient UI workflow (cursor, hover, in-progress weapon picks) lives
 * client-side and never crosses this boundary — only finalised intents do.
 */
public sealed interface GameCommand

/**
 * Move a unit along its chosen path to [destination], spending the MP encoded
 * by [destination] (a [ReachableHex] carries path + facing + MP cost).
 */
public data class MoveUnit(
    public val playerId: PlayerId,
    public val unitId: UnitId,
    public val destination: ReachableHex,
    public val mode: MovementMode,
) : GameCommand

/**
 * Commit a full attack impulse for [playerId]. [torsoFacings] is applied
 * to attacker units before resolution. The active phase handler decides
 * what "commit" means for its phase: WeaponAttackPhaseHandler resolves
 * accumulated declarations on the final impulse; PhysicalAttackPhaseHandler
 * just records them.
 */
public data class CommitAttackImpulse(
    public val playerId: PlayerId,
    public val declarations: List<AttackDeclaration>,
    public val torsoFacings: Map<UnitId, HexDirection>,
) : GameCommand

/**
 * Commit a full physical-attack impulse for [playerId]. Carries
 * [PhysicalAttackDeclaration]s (punches/kicks) rather than weapon declarations.
 * [torsoFacings] is applied to attacker units before resolution.
 * [PhysicalAttackPhaseHandler] resolves accumulated declarations on the final
 * impulse and applies damage.
 */
public data class CommitPhysicalAttackImpulse(
    public val playerId: PlayerId,
    public val declarations: List<PhysicalAttackDeclaration>,
    public val torsoFacings: Map<UnitId, HexDirection>,
) : GameCommand
