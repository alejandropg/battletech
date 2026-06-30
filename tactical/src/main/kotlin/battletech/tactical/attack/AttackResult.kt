package battletech.tactical.attack

import battletech.tactical.dice.DiceRoll
import battletech.tactical.unit.UnitId

public data class AttackResult(
    val attackerId: UnitId,
    val targetId: UnitId,
    val weaponName: String,
    val hit: Boolean,
    val hitLocation: HitLocation?,
    val damageApplied: Int,
    val targetNumber: Int,
    val roll: Int,
    val toHitRoll: DiceRoll,
    val locationRoll: DiceRoll?,
    val gunnery: Int,
    val rangeModifier: Int,
    val rangeBand: RangeBand,
    val heatPenalty: Int,
    val secondaryPenalty: Int,
    val sensorPenalty: Int = 0,
    val attackerMoveModifier: Int = 0,
    val targetMoveModifier: Int = 0,
    val minRangeModifier: Int = 0,
    val damage: List<LocationDamage> = emptyList(),
    val modifiers: List<ToHitModifier> = emptyList(),
    /** True when the target has partial cover (lower body masked by an obstacle).
     *  Leg-location hits under partial cover deal no damage and roll no crit. */
    val partialCover: Boolean = false,
    /** True when the attack struck the target's rear arc; rear torso hits use the rear armor track. */
    val useRearArmor: Boolean = false,
    /**
     * Per-group location hits, populated after a successful to-hit roll.
     *
     * For single-location (non-cluster) weapons this is a one-element list mirroring
     * [hitLocation]/[locationRoll]. For cluster weapons (SRM/LRM) this contains one entry per
     * missile group, each with its own location roll and group damage.  Empty on a miss.
     *
     * Pass-2 damage resolution iterates this list so downstream code has a uniform shape;
     * the legacy scalar fields ([hitLocation], [locationRoll], [damageApplied]) are kept for
     * backward-compatible consumers and are populated from the first element on a hit.
     */
    val locationHits: List<LocationHit> = emptyList(),
    /**
     * Number of missiles that connected for cluster weapons; null for non-cluster weapons.
     * Used by the TUI to render "LRM-20: 12 missiles → 5 CT, 5 RT, 2 LA".
     */
    val missilesHit: Int? = null,
)
