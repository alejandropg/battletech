package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.FallResult
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.MechLocation
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.DestructionReason
import battletech.tactical.unit.PilotingSkillRoll
import battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

/**
 * Passive narration of something that happened in the session. Events are
 * the canonical channel by which clients learn about state changes;
 * see [CommandResult.Accepted].
 *
 * Events describe past facts only — they are never commands in disguise.
 */
@Serializable
public sealed interface GameEvent

@Serializable
public data class UnitMoved(
    public val unitId: UnitId,
    public val from: HexCoordinates,
    public val to: HexCoordinates,
    public val finalFacing: HexDirection,
    public val mode: MovementMode,
    public val mpSpent: Int,
) : GameEvent

@Serializable
public data class AttacksResolved(
    public val results: List<AttackResult>
) : GameEvent

@Serializable
public data class PhysicalAttacksResolved(
    public val results: List<PhysicalAttackResult>
) : GameEvent

@Serializable
public data class UnitFell(
    public val unitId: UnitId,
    public val fall: FallResult,
) : GameEvent

@Serializable
public data class UnitStoodUp(
    public val unitId: UnitId,
    public val psr: PilotingSkillRoll,
    public val stoodUp: Boolean,
) : GameEvent

@Serializable
public data class AttackDeclarationsRecorded(
    public val player: PlayerId,
    public val declarations: List<AttackDeclaration>,
) : GameEvent

@Serializable
public data class TorsoFacingsApplied(
    public val facings: Map<UnitId, HexDirection>
) : GameEvent

@Serializable
public data class PhaseChanged(
    public val from: TurnPhase,
    public val to: TurnPhase
) : GameEvent

@Serializable
public data class InitiativeRolled(
    public val initiative: Initiative
) : GameEvent

@Serializable
public data class HeatDissipated(
    public val heatBefore: Map<UnitId, Int>,
    public val heatAfter: Map<UnitId, Int>
) : GameEvent

/** A unit powered down in the Heat Phase. */
@Serializable
public sealed interface UnitShutdown : GameEvent {
    public val unitId: UnitId

    /** Automatic shutdown — heat reached the auto-shutdown threshold (≥ 30); no avoidance roll. */
    @Serializable
    public data class Automatic(override val unitId: UnitId) : UnitShutdown

    /** The unit failed its 2d6 shutdown-avoidance [roll]. */
    @Serializable
    public data class AvoidFailed(override val unitId: UnitId, public val roll: DiceRoll) : UnitShutdown
}

/** A shut-down unit came back online in the Heat Phase. */
@Serializable
public sealed interface UnitRestarted : GameEvent {
    public val unitId: UnitId

    /** Automatic restart — heat fell below the lowest shutdown threshold; no roll needed. */
    @Serializable
    public data class Automatic(override val unitId: UnitId) : UnitRestarted

    /** The unit passed its 2d6 restart [roll]. */
    @Serializable
    public data class RollPassed(override val unitId: UnitId, public val roll: DiceRoll) : UnitRestarted
}

/** A unit's ammunition cooked off from heat, taking internal damage. */
@Serializable
public data class AmmoExploded(
    public val unitId: UnitId,
    public val ammoType: AmmoType,
    public val damage: Int,
) : GameEvent

@Serializable
public data class TurnEnded(
    public val turnNumber: Int
) : GameEvent

/** A unit was eliminated from play. [reason] is the structural (or later, pilot/crit) cause. */
@Serializable
public data class UnitDestroyed(
    public val unitId: UnitId,
    public val reason: DestructionReason,
) : GameEvent

/** The match has concluded. */
@Serializable
public data class MatchEnded(
    public val outcome: MatchOutcome,
) : GameEvent

/**
 * A critical hit destroyed [content] at [location]/[slotIndex] on [unitId]
 * (`docs/rules/armor-damage.md` §3). This stage only records the destroyed slot;
 * per-component consequences (engine heat, gyro PSR, weapon disable, ammo
 * detonation, …) are wired in later stages.
 */
@Serializable
public data class CriticalHit(
    public val unitId: UnitId,
    public val location: MechLocation,
    public val slotIndex: Int,
    public val content: CriticalSlotContent,
) : GameEvent

/**
 * [unitId]'s pilot took a hit (life support, ASSUMPTION: head/fall/ammo sources in
 * future stages), bringing the running total to [pilotHits].
 */
@Serializable
public sealed interface PilotHit : GameEvent {
    public val unitId: UnitId
    public val pilotHits: Int

    /**
     * The hit reached [battletech.tactical.unit.PILOT_DEATH_THRESHOLD] — the pilot is dead, so
     * no consciousness roll is made (see [DestructionReason.PILOT_DEAD]).
     */
    @Serializable
    public data class Fatal(override val unitId: UnitId, override val pilotHits: Int) : PilotHit

    /** The pilot survived; [consciousnessRoll] is the scripted 2d6 roll and [conscious] its outcome. */
    @Serializable
    public data class Checked(
        override val unitId: UnitId,
        override val pilotHits: Int,
        public val consciousnessRoll: DiceRoll,
        public val conscious: Boolean,
    ) : PilotHit
}

/** [unitId]'s pilot failed a consciousness check (following a [PilotHit]) and blacked out. */
@Serializable
public data class PilotKnockedUnconscious(
    public val unitId: UnitId,
) : GameEvent

/**
 * [unitId]'s pilot regained consciousness (Stage 7 Heat Phase recovery roll —
 * ASSUMPTION/standard; `docs/rules/armor-damage.md` only pins life-support damage
 * sources and death, not recovery mechanics).
 */
@Serializable
public data class PilotRecoveredConsciousness(
    public val unitId: UnitId,
    public val roll: DiceRoll,
) : GameEvent

/**
 * A free-text annotation recorded in the game log for an out-of-band
 * happening (e.g. a network connect/disconnect) — used by deliveries/servers
 * that wrap a session; tactical itself stays network-agnostic and never
 * emits this on its own.
 */
@Serializable
public data class SessionNotice(
    public val text: String,
) : GameEvent
