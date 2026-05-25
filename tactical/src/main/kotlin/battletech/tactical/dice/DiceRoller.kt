package battletech.tactical.dice

import kotlin.random.Random

public interface DiceRoller {
    public fun d6(): Int

    public fun d6(count: Int): List<Int> = List(count) { d6() }

    public fun roll2d6(): DiceRoll = DiceRoll(d6(), d6())

    public companion object {
        internal fun seeded(seed: Long): DiceRoller = RandomDiceRoller(Random(seed))

        internal fun deterministic(rolls: List<Int>): DiceRoller = ScriptedDiceRoller(rolls)

        internal fun deterministic(vararg rolls: Int): DiceRoller = ScriptedDiceRoller(rolls.toList())
    }
}
