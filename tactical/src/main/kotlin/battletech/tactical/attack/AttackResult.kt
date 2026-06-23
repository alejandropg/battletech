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
    val damage: List<LocationDamage> = emptyList(),
)
