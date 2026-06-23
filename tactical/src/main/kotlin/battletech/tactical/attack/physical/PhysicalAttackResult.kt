package battletech.tactical.attack.physical

import battletech.tactical.attack.FallResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.LocationDamage
import battletech.tactical.dice.DiceRoll
import battletech.tactical.unit.PilotingSkillRoll
import battletech.tactical.unit.UnitId

/** Outcome of resolving one declared physical attack. */
public data class PhysicalAttackResult(
    public val attackerId: UnitId,
    public val targetId: UnitId,
    public val attackName: String,
    public val hit: Boolean,
    public val hitLocation: HitLocation?,
    public val damageApplied: Int,
    public val targetNumber: Int,
    public val roll: Int,
    public val toHitRoll: DiceRoll,
    public val locationRoll: Int?,
    public val attackDirection: AttackDirection,
    /** Knockdown PSR forced by a kick (target on a hit, attacker on a miss); null for punches. */
    public val psr: PilotingSkillRoll? = null,
    /** The fall that resulted from a failed [psr], if any. */
    public val fall: FallResult? = null,
    /** Which unit fell as a consequence of this attack, if any. */
    public val fallenUnitId: UnitId? = null,
    public val damage: List<LocationDamage> = emptyList(),
)
