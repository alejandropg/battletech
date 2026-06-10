package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.FallResult
import battletech.tactical.dice.DiceRoll
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.model.MovementMode
import battletech.tactical.unit.PilotingSkillRoll
import battletech.tactical.unit.UnitId

/**
 * Passive narration of something that happened in the session. Events are
 * the canonical channel by which clients learn about state changes;
 * see [CommandResult.Accepted].
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

public data class AttacksResolved(
    public val results: List<AttackResult>
) : GameEvent

public data class PhysicalAttacksResolved(
    public val results: List<PhysicalAttackResult>
) : GameEvent

public data class UnitFell(
    public val unitId: UnitId,
    public val fall: FallResult,
) : GameEvent

public data class UnitStoodUp(
    public val unitId: UnitId,
    public val psr: PilotingSkillRoll,
    public val stoodUp: Boolean,
) : GameEvent

public data class AttackDeclarationsRecorded(
    public val player: PlayerId,
    public val declarations: List<AttackDeclaration>,
) : GameEvent

public data class TorsoFacingsApplied(
    public val facings: Map<UnitId, HexDirection>
) : GameEvent

public data class PhaseChanged(
    public val from: TurnPhase,
    public val to: TurnPhase
) : GameEvent

public data class InitiativeRolled(
    public val initiative: Initiative
) : GameEvent

public data class HeatDissipated(
    public val heatBefore: Map<UnitId, Int>,
    public val heatAfter: Map<UnitId, Int>
) : GameEvent

/**
 * A unit powered down in the Heat Phase. [roll] is null for an automatic
 * shutdown (heat ≥ 30); otherwise it is the failed 2D6 avoidance roll.
 */
public data class UnitShutdown(
    public val unitId: UnitId,
    public val roll: DiceRoll?,
    public val auto: Boolean,
) : GameEvent

/**
 * A shut-down unit came back online in the Heat Phase. [roll] is null for an
 * automatic restart (heat below the lowest shutdown threshold).
 */
public data class UnitRestarted(
    public val unitId: UnitId,
    public val roll: DiceRoll?,
) : GameEvent

/** A unit's ammunition cooked off from heat, taking internal damage. */
public data class AmmoExploded(
    public val unitId: UnitId,
    public val weaponName: String,
    public val damage: Int,
) : GameEvent

public data class TurnEnded(
    public val turnNumber: Int
) : GameEvent
