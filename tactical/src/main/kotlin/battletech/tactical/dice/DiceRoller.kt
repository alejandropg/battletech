package battletech.tactical.dice

import kotlin.random.Random

public interface DiceRoller {
    public fun d6(): Int

    public fun d6(count: Int): List<Int> = List(count) { d6() }

    public fun roll2d6(): Int = d6() + d6()

    public companion object {
        public fun seeded(seed: Long): DiceRoller = RandomDiceRoller(Random(seed))

        public fun deterministic(rolls: List<Int>): DiceRoller = ScriptedDiceRoller(rolls)

        public fun deterministic(vararg rolls: Int): DiceRoller = ScriptedDiceRoller(rolls.toList())
    }
}
