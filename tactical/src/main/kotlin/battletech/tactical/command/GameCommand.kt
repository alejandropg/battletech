package battletech.tactical.command

import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackDeclaration
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.movement.ReachableHex

/**
 * A player intent submitted to a [battletech.tactical.session.BattleSession].
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
 * to attacker units before resolution. When [isWeaponPhase] is true the
 * declarations are resolved immediately (dice rolled, damage applied);
 * when false (physical phase) the declarations are recorded for combined
 * resolution at the end of the phase — current behaviour matches the TUI.
 */
public data class CommitAttackImpulse(
    public val playerId: PlayerId,
    public val isWeaponPhase: Boolean,
    public val declarations: List<AttackDeclaration>,
    public val torsoFacings: Map<UnitId, HexDirection>,
) : GameCommand
