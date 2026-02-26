package battletech.tactical.action

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class InitiativeTest {

    @Test
    fun `lower roll is loser, higher roll is winner`() {
        // Seed produces: P1 rolls 1+1=2, P2 rolls 6+6=12
        val random = Random(42)
        val p1Roll = random.nextInt(1, 7) + random.nextInt(1, 7)
        val p2Roll = random.nextInt(1, 7) + random.nextInt(1, 7)

        // Reset with same seed for actual call
        val result = rollInitiative(Random(42))

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
        val result = rollInitiative(Random(123))

        assertEquals(2, result.rolls.size)
        assert(result.rolls[PlayerId.PLAYER_1]!! in 2..12)
        assert(result.rolls[PlayerId.PLAYER_2]!! in 2..12)
    }

    @Test
    fun `loser and winner are different players`() {
        val result = rollInitiative(Random(99))

        assertNotEquals(result.loser, result.winner)
    }

    @Test
    fun `re-rolls on tie`() {
        // Find a seed that produces a tie on first attempt
        var seed = 0L
        while (true) {
            val r = Random(seed)
            val roll1 = r.nextInt(1, 7) + r.nextInt(1, 7)
            val roll2 = r.nextInt(1, 7) + r.nextInt(1, 7)
            if (roll1 == roll2) break
            seed++
        }

        // Should still produce a valid result (not hang)
        val result = rollInitiative(Random(seed))
        assertNotEquals(result.loser, result.winner)
        assertNotEquals(result.rolls[PlayerId.PLAYER_1], result.rolls[PlayerId.PLAYER_2])
    }
}
