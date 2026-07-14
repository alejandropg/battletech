package battletech.tactical.heat

import battletech.tactical.model.MovementMode
import battletech.tactical.unit.HeatSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HeatGenerationTest {

    @Test
    fun `stationary or turn-in-place generates no heat`() {
        assertTrue(movementHeatSources(MovementMode.WALK, 0).isEmpty())
        assertTrue(movementHeatSources(MovementMode.RUN, 0).isEmpty())
        assertTrue(movementHeatSources(MovementMode.JUMP, 0).isEmpty())
    }

    @Test
    fun `walking is a flat one heat`() {
        assertEquals(listOf(HeatSource("Walking", 1)), movementHeatSources(MovementMode.WALK, 5))
    }

    @Test
    fun `running is a flat two heat`() {
        assertEquals(listOf(HeatSource("Running", 2)), movementHeatSources(MovementMode.RUN, 7))
    }

    @Test
    fun `jumping is one heat per hex with a floor of three`() {
        assertEquals(listOf(HeatSource("Jumping", 3)), movementHeatSources(MovementMode.JUMP, 1))
        assertEquals(listOf(HeatSource("Jumping", 3)), movementHeatSources(MovementMode.JUMP, 2))
        assertEquals(listOf(HeatSource("Jumping", 3)), movementHeatSources(MovementMode.JUMP, 3))
        assertEquals(listOf(HeatSource("Jumping", 5)), movementHeatSources(MovementMode.JUMP, 5))
    }
}
