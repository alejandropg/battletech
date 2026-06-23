package battletech.tactical.unit

import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller

/** Outcome of a Piloting Skill Roll: a 2d6 [roll] compared to a [targetNumber]. */
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
 * +3 PSR modifier applied to ALL piloting skill rolls once [unit] has taken at least
 * one gyro critical hit (`docs/rules/armor-damage.md` §3 Quick Reference table). Zero
 * once the gyro is destroyed too (2 crits) since the unit is eliminated by then — the
 * modifier still computes harmlessly for any caller that runs ahead of the destruction
 * sweep. Derives from the single tier -> effect source, [critEffects].
 */
public fun gyroPsrModifier(unit: CombatUnit): Int =
    unit.critEffects(CriticalComponent.GYRO).filterIsInstance<CritEffect.PsrPenalty>().sumOf { it.amount }
