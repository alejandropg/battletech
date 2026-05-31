package battletech.tactical.attack.physical

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
