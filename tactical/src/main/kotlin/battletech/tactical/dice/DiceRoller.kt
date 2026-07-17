package battletech.tactical.dice

import kotlin.random.Random

public interface DiceRoller {
    public fun d6(): Int

    public fun d6(count: Int): List<Int> = List(count) { d6() }

    public fun roll2d6(): DiceRoll = DiceRoll(d6(), d6())

    /**
     * Reproducible rollers. Only tests call these today — they are NOT dead code awaiting a
     * cleanup, and a sweep for "no production caller" will keep finding them: replaying an exact
     * game means feeding back the exact rolls in the exact order they were consumed, which is
     * precisely what [deterministic] does and what a future replay/save feature will need.
     * (Both are `internal` to `tactical`; a replay driven from `tui`/`network` would have to
     * widen them — a problem for the commit that builds replay, not a reason to delete them now.)
     */
    public companion object {
        internal fun seeded(seed: Long): DiceRoller = RandomDiceRoller(Random(seed))

        internal fun deterministic(rolls: List<Int>): DiceRoller = ScriptedDiceRoller(rolls)

        internal fun deterministic(vararg rolls: Int): DiceRoller = ScriptedDiceRoller(rolls.toList())
    }
}

/**
 * Probability (percent, 0..100) of rolling at least [targetNumber] on 2d6.
 * Shared by physical attack definitions for their success-chance previews.
 */
public fun twoD6AtLeastProbability(targetNumber: Int): Int = when {
    targetNumber <= 2 -> 100
    targetNumber >= 13 -> 0
    else -> TWO_D6_AT_LEAST[targetNumber] ?: 0
}

private val TWO_D6_AT_LEAST: Map<Int, Int> = mapOf(
    2 to 100,
    3 to 97,
    4 to 92,
    5 to 83,
    6 to 72,
    7 to 58,
    8 to 42,
    9 to 28,
    10 to 17,
    11 to 8,
    12 to 3,
)
