package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.ImpulseAttackPhaseHandler
import battletech.tactical.attack.physical.PhysicalAttackDeclaration
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.movement.ReachableHex
import battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

/**
 * A player intent submitted to a [BattleSession].
 * Coarse and commit-on-intent: each command represents a real game decision
 * the session validates and applies as one atomic step.
 *
 * The transient UI workflow (cursor, hover, in-progress weapon picks) lives
 * client-side and never crosses this boundary — only finalised intents do.
 *
 * Every concrete command carries [playerId] so the session can enforce
 * active-player authorization centrally without inspecting subtypes.
 */
@Serializable
public sealed interface GameCommand {
    public val playerId: PlayerId
}

/**
 * Move a unit along its chosen path to [destination], spending the MP encoded
 * by [destination] (a [ReachableHex] carries path + facing + MP cost).
 */
@Serializable
public data class MoveUnit(
    override val playerId: PlayerId,
    public val unitId: UnitId,
    public val destination: ReachableHex,
    public val mode: MovementMode,
) : GameCommand

/**
 * Attempt to stand a prone unit during the movement phase: rolls a Piloting
 * Skill Roll. On success the unit is no longer prone and may still move this
 * impulse; on failure it remains prone and its activation is spent.
 */
@Serializable
public data class StandUp(
    override val playerId: PlayerId,
    public val unitId: UnitId,
) : GameCommand

/**
 * Shared shape of the two impulse-commit commands (weapon and physical attack
 * phases): both carry [playerId] and a [torsoFacings] map applied to attacker
 * units before resolution. Declaration payloads genuinely differ between the
 * two phases (weapon vs. physical), so they stay separate concrete commands —
 * this interface exists so [ImpulseAttackPhaseHandler] can validate the
 * torso-facing portion once for both.
 */
@Serializable
public sealed interface AttackImpulseCommand : GameCommand {
    override val playerId: PlayerId
    public val torsoFacings: Map<UnitId, HexDirection>
}

/**
 * Commit a full attack impulse for [playerId]. [torsoFacings] is applied
 * to attacker units before resolution. The active phase handler decides
 * what "commit" means for its phase: WeaponAttackPhaseHandler resolves
 * accumulated declarations on the final impulse; PhysicalAttackPhaseHandler
 * just records them.
 */
@Serializable
public data class CommitAttackImpulse(
    override val playerId: PlayerId,
    public val declarations: List<AttackDeclaration>,
    override val torsoFacings: Map<UnitId, HexDirection>,
) : AttackImpulseCommand

/**
 * Commit a full physical-attack impulse for [playerId]. Carries
 * [PhysicalAttackDeclaration]s (punches/kicks) rather than weapon declarations.
 * [torsoFacings] is applied to attacker units before resolution.
 * [PhysicalAttackPhaseHandler] resolves accumulated declarations on the final
 * impulse and applies damage.
 */
@Serializable
public data class CommitPhysicalAttackImpulse(
    override val playerId: PlayerId,
    public val declarations: List<PhysicalAttackDeclaration>,
    override val torsoFacings: Map<UnitId, HexDirection>,
) : AttackImpulseCommand
