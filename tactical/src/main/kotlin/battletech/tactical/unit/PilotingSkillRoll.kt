package battletech.tactical.unit

import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import kotlinx.serialization.Serializable

/** Outcome of a Piloting Skill Roll: a 2d6 [roll] compared to a [targetNumber]. */
@Serializable
public data class PilotingSkillRoll(
    public val targetNumber: Int,
    public val roll: DiceRoll,
    public val passed: Boolean,
)

/**
 * Rolls 2d6 against [unit]'s piloting skill plus [modifier]. Reusable by any
 * caller that needs a PSR (kick knockdowns, stand-up attempts, ...).
 */
public fun pilotingSkillRoll(unit: CombatUnit, roller: DiceRoller, modifier: Int = 0): PilotingSkillRoll {
    val targetNumber = unit.pilotingSkill + modifier
    val roll = roller.roll2d6()
    return PilotingSkillRoll(targetNumber = targetNumber, roll = roll, passed = roll.total >= targetNumber)
}

/**
 * PSR modifier from [unit]'s gyro critical hits (`docs/rules/critical-hits.md` §5).
 * Derives from the single tier -> effect source, [critEffects], so it computes harmlessly
 * even for a caller running ahead of the destruction sweep.
 */
public fun gyroPsrModifier(unit: CombatUnit): Int =
    unit.critEffects(CriticalComponent.GYRO).filterIsInstance<CritEffect.PsrPenalty>().sumOf { it.amount }

/**
 * The two PSR penalties that apply to almost every piloting-skill roll a unit makes:
 * [gyroPsrModifier] (gyro crits) plus [legPsrModifier] (destroyed legs). Callers that need
 * additional situational modifiers (20-damage PSR's `totalDamage / 20`, etc.) add them on top.
 */
public fun CombatUnit.basePsrModifier(): Int = gyroPsrModifier(this) + legPsrModifier(this)
