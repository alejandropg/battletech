package battletech.tactical.attack.physical

import battletech.tactical.attack.FallResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.LocationDamage
import battletech.tactical.attack.ResolvedAttack
import battletech.tactical.dice.DiceRoll
import battletech.tactical.session.GameEvent
import battletech.tactical.unit.PilotingSkillRoll
import battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

/** Outcome of resolving one declared physical attack. */
@Serializable
public sealed interface PhysicalAttackResult {
    public val attackerId: UnitId
    public val targetId: UnitId
    public val attackName: String
    public val targetNumber: Int
    public val toHitRoll: DiceRoll
    public val attackDirection: AttackDirection

    /**
     * Outcome of the knockdown piloting-skill roll a kick forces — on the target when the kick
     * hit, on the attacker when it missed (a whiffed kick can topple the kicker). [Knockdown.None]
     * for punches, which never force a PSR.
     */
    public val knockdown: Knockdown

    @Serializable
    public data class Miss(
        override val attackerId: UnitId,
        override val targetId: UnitId,
        override val attackName: String,
        override val targetNumber: Int,
        override val toHitRoll: DiceRoll,
        override val attackDirection: AttackDirection,
        override val knockdown: Knockdown = Knockdown.None,
    ) : PhysicalAttackResult

    @Serializable
    public data class Hit(
        override val attackerId: UnitId,
        override val targetId: UnitId,
        override val attackName: String,
        override val targetNumber: Int,
        override val toHitRoll: DiceRoll,
        override val attackDirection: AttackDirection,
        public val hitLocation: HitLocation,
        /** Physical attacks roll a single 1d6 for hit location — deliberately not [DiceRoll] (2d6), unlike weapon fire. */
        public val locationRoll: Int,
        public val damageApplied: Int,
        override val damage: List<LocationDamage> = emptyList(),
        override val knockdown: Knockdown = Knockdown.None,
    ) : PhysicalAttackResult, ResolvedAttack {
        public fun withDamage(damage: List<LocationDamage>): Hit = copy(damage = damage)
    }
}

/**
 * Outcome of a kick's knockdown piloting-skill roll. Whether the faller (target on a hit,
 * attacker on a miss — see [battletech.tactical.attack.physical.resolvePhysicalAttacks])
 * fell or resisted is observable; the [PilotingSkillRoll] behind either outcome reveals
 * `pilotingSkill`, so it is withheld from foreign viewers via each case's `Undisclosed`
 * leaf — see [battletech.tactical.session.redactFor].
 */
@Serializable
public sealed interface Knockdown {
    /** No PSR was forced — always the case for punches. */
    @Serializable
    public data object None : Knockdown

    /** The PSR was made; no fall resulted. */
    @Serializable
    public sealed interface Resisted : Knockdown {
        @Serializable
        public data class Detailed(public val psr: PilotingSkillRoll) : Resisted

        /** Foreign-viewer redaction of [Detailed]: only that the faller resisted. */
        @Serializable
        public data object Undisclosed : Resisted
    }

    /** The PSR failed; [unitId] fell, taking [fall] damage and (per standard rules) a pilot hit. */
    @Serializable
    public sealed interface Fell : Knockdown {
        public val unitId: UnitId
        public val fall: FallResult

        /**
         * Pilot-hit events ([battletech.tactical.session.PilotHit] /
         * [battletech.tactical.session.PilotKnockedUnconscious]) resulting from this fall.
         * Emitted by [battletech.tactical.attack.physical.PhysicalAttackPhaseHandler] alongside
         * [battletech.tactical.session.UnitFell].
         */
        public val pilotEvents: List<GameEvent>

        @Serializable
        public data class Detailed(
            override val unitId: UnitId,
            public val psr: PilotingSkillRoll,
            override val fall: FallResult,
            override val pilotEvents: List<GameEvent> = emptyList(),
        ) : Fell

        /** Foreign-viewer redaction of [Detailed]: the fall (and its damage) stays observable, the PSR does not. */
        @Serializable
        public data class Undisclosed(
            override val unitId: UnitId,
            override val fall: FallResult,
            override val pilotEvents: List<GameEvent> = emptyList(),
        ) : Fell
    }
}
