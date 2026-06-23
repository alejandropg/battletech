package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.FallResult
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.DestructionReason
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
    public val ammoType: AmmoType,
    public val damage: Int,
) : GameEvent

public data class TurnEnded(
    public val turnNumber: Int
) : GameEvent

/** A unit was eliminated from play. [reason] is the structural (or later, pilot/crit) cause. */
public data class UnitDestroyed(
    public val unitId: UnitId,
    public val reason: DestructionReason,
) : GameEvent

/** The match has concluded. [winner] is null when neither side has survivors (draw). */
public data class MatchEnded(
    public val winner: PlayerId?,
) : GameEvent

/**
 * A critical hit destroyed [content] at [location]/[slotIndex] on [unitId]
 * (`docs/rules/armor-damage.md` §3). This stage only records the destroyed slot;
 * per-component consequences (engine heat, gyro PSR, weapon disable, ammo
 * detonation, …) are wired in later stages.
 */
public data class CriticalHit(
    public val unitId: UnitId,
    public val location: MechLocation,
    public val slotIndex: Int,
    public val content: CriticalSlotContent,
) : GameEvent

/**
 * [unitId]'s pilot took a hit (life support, ASSUMPTION: head/fall/ammo sources in
 * future stages), bringing the running total to [pilotHits]. [consciousnessRoll] is
 * null when no consciousness check was needed (death threshold reached — see
 * [battletech.tactical.unit.PILOT_DEATH_THRESHOLD] / [DestructionReason.PILOT_DEAD]);
 * otherwise it is the scripted 2d6 roll, and [conscious] reports the outcome.
 */
public data class PilotHit(
    public val unitId: UnitId,
    public val pilotHits: Int,
    public val consciousnessRoll: DiceRoll?,
    public val conscious: Boolean,
) : GameEvent

/** [unitId]'s pilot failed a consciousness check (following a [PilotHit]) and blacked out. */
public data class PilotKnockedUnconscious(
    public val unitId: UnitId,
) : GameEvent

/**
 * [unitId]'s pilot regained consciousness (Stage 7 Heat Phase recovery roll —
 * ASSUMPTION/standard; `docs/rules/armor-damage.md` only pins life-support damage
 * sources and death, not recovery mechanics).
 */
public data class PilotRecoveredConsciousness(
    public val unitId: UnitId,
    public val roll: DiceRoll,
) : GameEvent
