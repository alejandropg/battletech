package battletech.tactical.action

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AttackOrderTest {

    @Test
    fun `produces loser block then winner block`() {
        val order = calculateAttackOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 3,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 2,
        )
        assertEquals(
            listOf(
                Impulse(PlayerId.PLAYER_1, 3),
                Impulse(PlayerId.PLAYER_2, 2),
            ),
            order,
        )
    }

    @Test
    fun `skips a side with zero units`() {
        val noWinnerUnits = calculateAttackOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 2,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 0,
        )
        assertEquals(listOf(Impulse(PlayerId.PLAYER_1, 2)), noWinnerUnits)

        val noLoserUnits = calculateAttackOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 0,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 3,
        )
        assertEquals(listOf(Impulse(PlayerId.PLAYER_2, 3)), noLoserUnits)
    }

    @Test
    fun `returns empty when both sides have zero units`() {
        val order = calculateAttackOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 0,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 0,
        )
        assertTrue(order.isEmpty())
    }
}
