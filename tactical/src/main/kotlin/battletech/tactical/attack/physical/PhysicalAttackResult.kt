package battletech.tactical.attack.physical

import battletech.tactical.attack.FallResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.LocationDamage
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
        public val damage: List<LocationDamage> = emptyList(),
        override val knockdown: Knockdown = Knockdown.None,
    ) : PhysicalAttackResult {
        public fun withDamage(damage: List<LocationDamage>): Hit = copy(damage = damage)
    }
}

/** Outcome of a kick's knockdown piloting-skill roll. */
@Serializable
public sealed interface Knockdown {
    /** No PSR was forced — always the case for punches. */
    @Serializable
    public data object None : Knockdown

    /** The PSR was made; no fall resulted. */
    @Serializable
    public data class Resisted(public val psr: PilotingSkillRoll) : Knockdown

    /** The PSR failed; [unitId] fell, taking [fall] damage and (per standard rules) a pilot hit. */
    @Serializable
    public data class Fell(
        public val unitId: UnitId,
        public val psr: PilotingSkillRoll,
        public val fall: FallResult,
        /**
         * Pilot-hit events ([battletech.tactical.session.PilotHit] /
         * [battletech.tactical.session.PilotKnockedUnconscious]) resulting from this fall.
         * Emitted by [battletech.tactical.attack.physical.PhysicalAttackPhaseHandler] alongside
         * [battletech.tactical.session.UnitFell].
         */
        public val pilotEvents: List<GameEvent> = emptyList(),
    ) : Knockdown
}
