package battletech.tui.game

import battletech.tactical.action.Impulse
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class TurnStateTest {

    private fun aTurnState(
        movementOrder: List<Impulse> = listOf(
            Impulse(PlayerId.PLAYER_1, 1),
            Impulse(PlayerId.PLAYER_2, 1),
        ),
        currentImpulseIndex: Int = 0,
        movedUnitIds: Set<UnitId> = emptySet(),
        unitsMovedInCurrentImpulse: Int = 0,
    ) = TurnState(
        initiativeResult = InitiativeResult(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        ),
        movementSequence = ImpulseSequence(movementOrder, currentImpulseIndex),
        movedUnitIds = movedUnitIds,
        unitsMovedInCurrentImpulse = unitsMovedInCurrentImpulse,
    )

    @Test
    fun `activePlayer returns current impulse player`() {
        val state = aTurnState()
        assertEquals(PlayerId.PLAYER_1, state.activePlayer)
    }

    @Test
    fun `remainingInImpulse returns units left to move`() {
        val state = aTurnState(
            movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 3)),
            unitsMovedInCurrentImpulse = 1,
        )
        assertEquals(2, state.remainingInImpulse)
    }

    @Test
    fun `allImpulsesComplete when index past end`() {
        val state = aTurnState(
            movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 1)),
            currentImpulseIndex = 1,
        )
        assertTrue(state.allImpulsesComplete)
    }

    @Test
    fun `allImpulsesComplete is false when impulses remain`() {
        val state = aTurnState(currentImpulseIndex = 0)
        assertFalse(state.allImpulsesComplete)
    }

    @Nested
    inner class AdvanceAfterUnitMovedTest {

        @Test
        fun `adds unit to movedUnitIds`() {
            val state = aTurnState(
                movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 2)),
            )

            val result = advanceAfterUnitMoved(state, UnitId("u1"))

            assertTrue(UnitId("u1") in result.movedUnitIds)
        }

        @Test
        fun `advances impulse when current impulse is full`() {
            val state = aTurnState(
                movementOrder = listOf(
                    Impulse(PlayerId.PLAYER_1, 1),
                    Impulse(PlayerId.PLAYER_2, 1),
                ),
            )

            val result = advanceAfterUnitMoved(state, UnitId("u1"))

            assertEquals(1, result.movementSequence.currentIndex)
            assertEquals(0, result.unitsMovedInCurrentImpulse)
        }

        @Test
        fun `stays in current impulse when more units remain`() {
            val state = aTurnState(
                movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 3)),
            )

            val result = advanceAfterUnitMoved(state, UnitId("u1"))

            assertEquals(0, result.movementSequence.currentIndex)
            assertEquals(1, result.unitsMovedInCurrentImpulse)
        }

        @Test
        fun `all impulses complete after last unit moves`() {
            val state = aTurnState(
                movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 1)),
            )

            val result = advanceAfterUnitMoved(state, UnitId("u1"))

            assertTrue(result.allImpulsesComplete)
        }

        @Test
        fun `multi-impulse progression`() {
            val state = aTurnState(
                movementOrder = listOf(
                    Impulse(PlayerId.PLAYER_1, 1),
                    Impulse(PlayerId.PLAYER_2, 2),
                ),
            )

            val after1 = advanceAfterUnitMoved(state, UnitId("p1-u1"))
            assertEquals(1, after1.movementSequence.currentIndex)
            assertEquals(PlayerId.PLAYER_2, after1.activePlayer)

            val after2 = advanceAfterUnitMoved(after1, UnitId("p2-u1"))
            assertEquals(1, after2.movementSequence.currentIndex)
            assertEquals(1, after2.unitsMovedInCurrentImpulse)

            val after3 = advanceAfterUnitMoved(after2, UnitId("p2-u2"))
            assertTrue(after3.allImpulsesComplete)
        }
    }

    @Nested
    inner class CalculateAttackOrderTest {
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
}
