package battletech.tactical.session

import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId
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
        initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        ),
        movement = MovementProgress(
            sequence = ImpulseSequence(movementOrder, currentImpulseIndex),
            movedUnitIds = movedUnitIds,
            movedInCurrentImpulse = unitsMovedInCurrentImpulse,
        ),
    )

    @Test
    fun `NULL TurnState has turnNumber 1`() {
        assertEquals(1, TurnState.NULL.turnNumber)
    }

    @Test
    fun `activePlayer returns current impulse player`() {
        val state = aTurnState()
        assertEquals(PlayerId.PLAYER_1, state.movement.activePlayer)
    }

    @Test
    fun `remainingInImpulse returns units left to move`() {
        val state = aTurnState(
            movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 3)),
            unitsMovedInCurrentImpulse = 1,
        )
        assertEquals(2, state.movement.remainingInImpulse)
    }

    @Test
    fun `allImpulsesComplete when index past end`() {
        val state = aTurnState(
            movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 1)),
            currentImpulseIndex = 1,
        )
        assertTrue(state.movement.isComplete)
    }

    @Test
    fun `allImpulsesComplete is false when impulses remain`() {
        val state = aTurnState(currentImpulseIndex = 0)
        assertFalse(state.movement.isComplete)
    }

    @Nested
    inner class AdvanceAfterUnitMovedTest {

        @Test
        fun `adds unit to movedUnitIds`() {
            val state = aTurnState(
                movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 2)),
            )

            val result = state.copy(movement = state.movement.afterUnitMoved(UnitId("u1")))

            assertTrue(UnitId("u1") in result.movement.movedUnitIds)
        }

        @Test
        fun `advances impulse when current impulse is full`() {
            val state = aTurnState(
                movementOrder = listOf(
                    Impulse(PlayerId.PLAYER_1, 1),
                    Impulse(PlayerId.PLAYER_2, 1),
                ),
            )

            val result = state.copy(movement = state.movement.afterUnitMoved(UnitId("u1")))

            assertEquals(1, result.movement.sequence.currentIndex)
            assertEquals(0, result.movement.movedInCurrentImpulse)
        }

        @Test
        fun `stays in current impulse when more units remain`() {
            val state = aTurnState(
                movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 3)),
            )

            val result = state.copy(movement = state.movement.afterUnitMoved(UnitId("u1")))

            assertEquals(0, result.movement.sequence.currentIndex)
            assertEquals(1, result.movement.movedInCurrentImpulse)
        }

        @Test
        fun `all impulses complete after last unit moves`() {
            val state = aTurnState(
                movementOrder = listOf(Impulse(PlayerId.PLAYER_1, 1)),
            )

            val result = state.copy(movement = state.movement.afterUnitMoved(UnitId("u1")))

            assertTrue(result.movement.isComplete)
        }

        @Test
        fun `multi-impulse progression`() {
            val state = aTurnState(
                movementOrder = listOf(
                    Impulse(PlayerId.PLAYER_1, 1),
                    Impulse(PlayerId.PLAYER_2, 2),
                ),
            )

            val after1 = state.copy(movement = state.movement.afterUnitMoved(UnitId("p1-u1")))
            assertEquals(1, after1.movement.sequence.currentIndex)
            assertEquals(PlayerId.PLAYER_2, after1.movement.activePlayer)

            val after2 = after1.copy(movement = after1.movement.afterUnitMoved(UnitId("p2-u1")))
            assertEquals(1, after2.movement.sequence.currentIndex)
            assertEquals(1, after2.movement.movedInCurrentImpulse)

            val after3 = after2.copy(movement = after2.movement.afterUnitMoved(UnitId("p2-u2")))
            assertTrue(after3.movement.isComplete)
        }
    }
}
