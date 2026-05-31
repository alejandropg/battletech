package battletech.tactical.attack

import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexDirection
import battletech.tactical.unit.CombatUnit
import kotlin.math.ceil

/** What happened when a unit fell: the damage it took and the facing it ended up in. */
public data class FallResult(
    public val damage: Int,
    public val hitLocation: HitLocation,
    public val locationRoll: DiceRoll,
    public val newFacing: HexDirection,
    public val facingRoll: Int,
)

/**
 * Makes [unit] fall: it takes ⌈tonnage/10⌉ damage to a location rolled on the
 * standard hit-location table, its facing is randomised by a 1d6 roll
 * (1 = no change, otherwise rotate clockwise that many hexsides minus one),
 * and it ends up prone. Reusable by any fall trigger (kick knockdown today;
 * weapon-damage and DFA later).
 */
public fun fall(unit: CombatUnit, roller: DiceRoller): Pair<CombatUnit, FallResult> {
    val damage = ceil(unit.tonnage / 10.0).toInt()

    val locationRoll = roller.roll2d6()
    val hitLocation = HitLocationTable.roll(locationRoll.total)
    val damaged = applyDamage(unit, hitLocation, damage)

    val facingRoll = roller.d6()
    var newFacing = unit.facing
    repeat(facingRoll - 1) { newFacing = newFacing.rotateClockwise() }

    val fallen = damaged.copy(facing = newFacing, torsoFacing = newFacing, isProne = true)
    return fallen to FallResult(
        damage = damage,
        hitLocation = hitLocation,
        locationRoll = locationRoll,
        newFacing = newFacing,
        facingRoll = facingRoll,
    )
}
