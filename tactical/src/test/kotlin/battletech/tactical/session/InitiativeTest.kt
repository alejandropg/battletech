package battletech.tactical.session

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.PlayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class InitiativeTest {

    @Test
    fun `lower roll is loser, higher roll is winner`() {
        // Predict the rolls by replaying a freshly-seeded roller
        val predict = DiceRoller.seeded(42)
        val p1Roll = predict.roll2d6()
        val p2Roll = predict.roll2d6()

        // Reset with same seed for actual call
        val result = rollInitiative(DiceRoller.seeded(42))

        assertEquals(p1Roll, result.rolls[PlayerId.PLAYER_1])
        assertEquals(p2Roll, result.rolls[PlayerId.PLAYER_2])
        if (p1Roll < p2Roll) {
            assertEquals(PlayerId.PLAYER_1, result.loser)
            assertEquals(PlayerId.PLAYER_2, result.winner)
        } else {
            assertEquals(PlayerId.PLAYER_2, result.loser)
            assertEquals(PlayerId.PLAYER_1, result.winner)
        }
    }

    @Test
    fun `both rolls are present in result`() {
        val result = rollInitiative(DiceRoller.seeded(123))

        assertEquals(2, result.rolls.size)
        assert(result.rolls[PlayerId.PLAYER_1]!! in 2..12)
        assert(result.rolls[PlayerId.PLAYER_2]!! in 2..12)
    }

    @Test
    fun `loser and winner are different players`() {
        val result = rollInitiative(DiceRoller.seeded(99))

        assertNotEquals(result.loser, result.winner)
    }

    @Test
    fun `re-rolls on tie`() {
        // Find a seed that produces a tie on first attempt
        var seed = 0L
        while (true) {
            val r = DiceRoller.seeded(seed)
            val roll1 = r.roll2d6()
            val roll2 = r.roll2d6()
            if (roll1 == roll2) break
            seed++
        }

        // Should still produce a valid result (not hang)
        val result = rollInitiative(DiceRoller.seeded(seed))
        assertNotEquals(result.loser, result.winner)
        assertNotEquals(result.rolls[PlayerId.PLAYER_1], result.rolls[PlayerId.PLAYER_2])
    }
}
