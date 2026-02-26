package battletech.tactical.action

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MovementOrderTest {

    @Test
    fun `equal counts produce straight alternation`() {
        val order = calculateMovementOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 4,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 4,
        )

        assertEquals(
            listOf(
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 1),
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 1),
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 1),
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 1),
            ),
            order,
        )
    }

    @Test
    fun `unequal 3 vs 5 distributes correctly`() {
        val order = calculateMovementOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 3,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 5,
        )

        assertEquals(
            listOf(
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 2),
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 2),
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 1),
            ),
            order,
        )
    }

    @Test
    fun `unequal 2 vs 5 distributes correctly`() {
        val order = calculateMovementOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 2,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 5,
        )

        // 5 / 2 = 2 base, 5 % 2 = 1 extra in first round
        assertEquals(
            listOf(
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 3),
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 2),
            ),
            order,
        )
    }

    @Test
    fun `unequal 1 vs 4`() {
        val order = calculateMovementOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 1,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 4,
        )

        assertEquals(
            listOf(
                MovementImpulse(PlayerId.PLAYER_1, 1),
                MovementImpulse(PlayerId.PLAYER_2, 4),
            ),
            order,
        )
    }

    @Test
    fun `both zero units returns empty list`() {
        val order = calculateMovementOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 0,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 0,
        )

        assertEquals(emptyList<MovementImpulse>(), order)
    }

    @Test
    fun `one side has zero units`() {
        val order = calculateMovementOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 0,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 3,
        )

        assertEquals(
            listOf(MovementImpulse(PlayerId.PLAYER_2, 3)),
            order,
        )
    }

    @Test
    fun `loser always moves first`() {
        val order = calculateMovementOrder(
            loser = PlayerId.PLAYER_2, loserUnitCount = 2,
            winner = PlayerId.PLAYER_1, winnerUnitCount = 3,
        )

        assertEquals(PlayerId.PLAYER_2, order.first().player)
    }

    @Test
    fun `total unit counts match inputs`() {
        val order = calculateMovementOrder(
            loser = PlayerId.PLAYER_1, loserUnitCount = 3,
            winner = PlayerId.PLAYER_2, winnerUnitCount = 5,
        )

        val p1Total = order.filter { it.player == PlayerId.PLAYER_1 }.sumOf { it.unitCount }
        val p2Total = order.filter { it.player == PlayerId.PLAYER_2 }.sumOf { it.unitCount }
        assertEquals(3, p1Total)
        assertEquals(5, p2Total)
    }
}
