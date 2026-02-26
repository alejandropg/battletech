package battletech.tactical.model

import battletech.tactical.action.PlayerId
import battletech.tactical.action.aGameState
import battletech.tactical.action.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class GameStateTest {

    @Test
    fun `unitsOf returns units belonging to given player`() {
        val p1Unit = aUnit(id = "p1", owner = PlayerId.PLAYER_1)
        val p2Unit = aUnit(id = "p2", owner = PlayerId.PLAYER_2)
        val state = aGameState(units = listOf(p1Unit, p2Unit))

        assertEquals(listOf(p1Unit), state.unitsOf(PlayerId.PLAYER_1))
        assertEquals(listOf(p2Unit), state.unitsOf(PlayerId.PLAYER_2))
    }

    @Test
    fun `unitsOf returns empty list for player with no units`() {
        val p1Unit = aUnit(id = "p1", owner = PlayerId.PLAYER_1)
        val state = aGameState(units = listOf(p1Unit))

        assertEquals(emptyList<battletech.tactical.action.Unit>(), state.unitsOf(PlayerId.PLAYER_2))
    }

    @Test
    fun `unitsOf with mixed ownership returns correct subset`() {
        val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1)
        val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_2)
        val u3 = aUnit(id = "u3", owner = PlayerId.PLAYER_1)
        val state = aGameState(units = listOf(u1, u2, u3))

        assertEquals(listOf(u1, u3), state.unitsOf(PlayerId.PLAYER_1))
    }
}
