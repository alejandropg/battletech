package battletech.tactical.dice

import kotlin.random.Random

public class RandomDiceRoller(private val random: Random = Random.Default) : DiceRoller {
    override fun d6(): Int = random.nextInt(1, 7)
}
