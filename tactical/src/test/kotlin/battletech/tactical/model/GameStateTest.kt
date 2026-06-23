package battletech.tactical.model

import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.unit.CombatUnit
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

        assertEquals(emptyList<CombatUnit>(), state.unitsOf(PlayerId.PLAYER_2))
    }

    @Test
    fun `unitsOf with mixed ownership returns correct subset`() {
        val u1 = aUnit(id = "u1", owner = PlayerId.PLAYER_1)
        val u2 = aUnit(id = "u2", owner = PlayerId.PLAYER_2)
        val u3 = aUnit(id = "u3", owner = PlayerId.PLAYER_1)
        val state = aGameState(units = listOf(u1, u2, u3))

        assertEquals(listOf(u1, u3), state.unitsOf(PlayerId.PLAYER_1))
    }

    @Test
    fun `activeUnitsOf excludes destroyed units`() {
        val active = aUnit(id = "active", owner = PlayerId.PLAYER_1)
        val destroyed = aUnit(id = "destroyed", owner = PlayerId.PLAYER_1, isDestroyed = true)
        val state = aGameState(units = listOf(active, destroyed))

        assertEquals(listOf(active), state.activeUnitsOf(PlayerId.PLAYER_1))
    }

    @Test
    fun `activeUnitsOf excludes shutdown units`() {
        val active = aUnit(id = "active", owner = PlayerId.PLAYER_1)
        val shutdown = aUnit(id = "shutdown", owner = PlayerId.PLAYER_1).copy(isShutdown = true)
        val state = aGameState(units = listOf(active, shutdown))

        assertEquals(listOf(active), state.activeUnitsOf(PlayerId.PLAYER_1))
    }

    @Test
    fun `activeUnitsOf excludes units with an unconscious pilot`() {
        val active = aUnit(id = "active", owner = PlayerId.PLAYER_1)
        val unconscious = aUnit(id = "unconscious", owner = PlayerId.PLAYER_1, isPilotConscious = false)
        val state = aGameState(units = listOf(active, unconscious))

        assertEquals(listOf(active), state.activeUnitsOf(PlayerId.PLAYER_1))
    }
}
