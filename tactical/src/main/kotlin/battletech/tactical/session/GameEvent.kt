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

/**
 * Whether [unitId] stood up (or failed to) is observable — you watch the miniature rise
 * or stay down. The [PilotingSkillRoll] behind it reveals `pilotingSkill` (its
 * `targetNumber` is `pilotingSkill + modifier`), so it is withheld from foreign viewers
 * via [Undisclosed] — see [battletech.tactical.session.redactFor].
 */
@Serializable
public sealed interface UnitStoodUp : GameEvent {
    public val unitId: UnitId
    public val stoodUp: Boolean

    @Serializable
    public data class Detailed(
        override val unitId: UnitId,
        public val psr: PilotingSkillRoll,
        override val stoodUp: Boolean,
    ) : UnitStoodUp

    /** Foreign-viewer redaction of [Detailed]: [stoodUp] stays, the PSR does not. */
    @Serializable
    public data class Undisclosed(
        override val unitId: UnitId,
        override val stoodUp: Boolean,
    ) : UnitStoodUp
}

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

/**
 * A unit powered down in the Heat Phase. The shutdown itself is observable (the 'Mech goes
 * visibly inert); `currentHeat` behind it is record-sheet data, and this event leaks it two
 * ways, so foreign viewers get [Undisclosed] — see [battletech.tactical.session.redactFor].
 *
 *  1. WHICH sub-case fired is itself a heat band: [Automatic] means heat ≥ 30, [AvoidFailed]
 *     a lower, roll-dependent band.
 *  2. [AvoidFailed.roll] leaks **via its emission precondition**, not in isolation:
 *     `resolvePower` emits it ONLY on a failed avoidance, so the event's existence asserts
 *     `roll.total < HeatScale.shutdownAvoidTarget(currentHeat)` and the roll value bounds
 *     `currentHeat`. (This is the same shape as [UnitRestarted.RollPassed] and
 *     [PilotRecoveredConsciousness.Detailed.roll]; a roll is never "just a roll" when the
 *     event carrying it is conditional on the roll's outcome.)
 */
@Serializable
public sealed interface UnitShutdown : GameEvent {
    public val unitId: UnitId

    /** Automatic shutdown — heat reached the auto-shutdown threshold (≥ 30); no avoidance roll. */
    @Serializable
    public data class Automatic(override val unitId: UnitId) : UnitShutdown

    /** The unit failed its 2d6 shutdown-avoidance [roll]. */
    @Serializable
    public data class AvoidFailed(override val unitId: UnitId, public val roll: DiceRoll) : UnitShutdown

    /** Foreign-viewer redaction of [Automatic] or [AvoidFailed]: the mechanism is withheld. */
    @Serializable
    public data class Undisclosed(override val unitId: UnitId) : UnitShutdown
}

/**
 * A shut-down unit came back online in the Heat Phase. Same two leaks as [UnitShutdown], with
 * the roll's precondition inverted: [RollPassed] is emitted ONLY on a *passed* restart roll, so
 * its existence asserts `roll.total >= HeatScale.shutdownAvoidTarget(currentHeat)` and the roll
 * value bounds `currentHeat` from the other side. Foreign viewers get [Undisclosed].
 */
@Serializable
public sealed interface UnitRestarted : GameEvent {
    public val unitId: UnitId

    /** Automatic restart — heat fell below the lowest shutdown threshold; no roll needed. */
    @Serializable
    public data class Automatic(override val unitId: UnitId) : UnitRestarted

    /** The unit passed its 2d6 restart [roll]. */
    @Serializable
    public data class RollPassed(override val unitId: UnitId, public val roll: DiceRoll) : UnitRestarted

    /** Foreign-viewer redaction of [Automatic] or [RollPassed]: the mechanism is withheld. */
    @Serializable
    public data class Undisclosed(override val unitId: UnitId) : UnitRestarted
}

/**
 * A unit's ammunition cooked off from heat, taking internal damage. The explosion itself
 * and its damage are observable (armor/structure damage is applied openly); which [ammoType]
 * cooked off is record-sheet loadout detail, withheld from foreign viewers via [Undisclosed]
 * — see [battletech.tactical.session.redactFor].
 */
@Serializable
public sealed interface AmmoExploded : GameEvent {
    public val unitId: UnitId
    public val damage: Int

    @Serializable
    public data class Detailed(
        override val unitId: UnitId,
        public val ammoType: AmmoType,
        override val damage: Int,
    ) : AmmoExploded

    /** Foreign-viewer redaction of [Detailed]: the damage is observable, the ammo type is not. */
    @Serializable
    public data class Undisclosed(
        override val unitId: UnitId,
        override val damage: Int,
    ) : AmmoExploded
}

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
 * A critical hit destroyed a component at [Detailed.location]/[Detailed.slotIndex] on
 * [unitId] (`docs/rules/armor-damage.md` §3). *That* a crit landed is observable at the
 * table; WHICH component ([Detailed.content]) is record-sheet detail, withheld from
 * foreign viewers via [Undisclosed] — see [battletech.tactical.session.redactFor].
 */
@Serializable
public sealed interface CriticalHit : GameEvent {
    public val unitId: UnitId

    /** This stage only records the destroyed slot; per-component consequences (engine
     * heat, gyro PSR, weapon disable, ammo detonation, …) are wired in later stages. */
    @Serializable
    public data class Detailed(
        override val unitId: UnitId,
        public val location: MechLocation,
        public val slotIndex: Int,
        public val content: CriticalSlotContent,
    ) : CriticalHit

    /** Foreign-viewer redaction of [Detailed]: only that a crit landed on [unitId]. */
    @Serializable
    public data class Undisclosed(override val unitId: UnitId) : CriticalHit
}

/**
 * [unitId]'s pilot took a hit (life support, ASSUMPTION: head/fall/ammo sources in
 * future stages). The running hit total ([Fatal.pilotHits]/[Checked.pilotHits]) is
 * record-sheet data, withheld from foreign viewers via [Undisclosed] — see
 * [battletech.tactical.session.redactFor].
 */
@Serializable
public sealed interface PilotHit : GameEvent {
    public val unitId: UnitId

    /**
     * The hit reached [battletech.tactical.unit.PILOT_DEATH_THRESHOLD] — the pilot is dead, so
     * no consciousness roll is made (see [DestructionReason.PILOT_DEAD]).
     */
    @Serializable
    public data class Fatal(override val unitId: UnitId, public val pilotHits: Int) : PilotHit

    /** The pilot survived; [consciousnessRoll] is the scripted 2d6 roll and [conscious] its outcome. */
    @Serializable
    public data class Checked(
        override val unitId: UnitId,
        public val pilotHits: Int,
        public val consciousnessRoll: DiceRoll,
        public val conscious: Boolean,
    ) : PilotHit

    /**
     * Foreign-viewer redaction of [Fatal] or [Checked]: the pilot was wounded, but the
     * running hit count, the consciousness roll, and whether it passed are all
     * record-sheet data. Whether the pilot is subsequently conscious is public through
     * a separate, unredacted [PilotKnockedUnconscious]/[PilotRecoveredConsciousness] —
     * this event only needs to say a hit landed.
     */
    @Serializable
    public data class Undisclosed(override val unitId: UnitId) : PilotHit
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
 *
 * *That* the pilot woke up is observable (the 'Mech visibly comes back under control),
 * but the recovery [Detailed.roll] leaks **via this event's success precondition**, not
 * in isolation: [battletech.tactical.attack.attemptConsciousnessRecovery] emits it ONLY
 * when `roll.total >= CONSCIOUSNESS_TARGET[pilotHits]`, so the event's mere existence
 * asserts that inequality and the roll value bounds `pilotHits` — recovering on a 3
 * implies a very different hit count than recovering on a 10. `pilotHits` is the same
 * record-sheet number [PilotHit] withholds, so foreign viewers get [Undisclosed].
 *
 * The roll is never rendered (the log prints only "pilot regained consciousness"), so
 * unlike [battletech.tactical.attack.AttackResult.gunnery] — whose target number the UI
 * is obliged to show — withholding it costs no rendering fidelity at all. See
 * [battletech.tactical.session.redactFor].
 */
@Serializable
public sealed interface PilotRecoveredConsciousness : GameEvent {
    public val unitId: UnitId

    @Serializable
    public data class Detailed(
        override val unitId: UnitId,
        public val roll: DiceRoll,
    ) : PilotRecoveredConsciousness

    /** Foreign-viewer redaction of [Detailed]: the pilot woke up; the roll that bounds `pilotHits` is withheld. */
    @Serializable
    public data class Undisclosed(override val unitId: UnitId) : PilotRecoveredConsciousness
}

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
