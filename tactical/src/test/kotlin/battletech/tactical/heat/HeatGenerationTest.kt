package battletech.tactical.heat

import battletech.tactical.model.MovementMode
import battletech.tactical.unit.HeatSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class HeatGenerationTest {

    @Test
    fun `stationary or turn-in-place generates no heat`() {
        assertNull(movementHeatSource(MovementMode.WALK, 0))
        assertNull(movementHeatSource(MovementMode.RUN, 0))
        assertNull(movementHeatSource(MovementMode.JUMP, 0))
    }

    @Test
    fun `walking is a flat one heat`() {
        assertEquals(HeatSource("Walking", 1), movementHeatSource(MovementMode.WALK, 5))
    }

    @Test
    fun `running is a flat two heat`() {
        assertEquals(HeatSource("Running", 2), movementHeatSource(MovementMode.RUN, 7))
    }

    @Test
    fun `jumping is one heat per hex with a floor of three`() {
        assertEquals(HeatSource("Jumping", 3), movementHeatSource(MovementMode.JUMP, 1))
        assertEquals(HeatSource("Jumping", 3), movementHeatSource(MovementMode.JUMP, 2))
        assertEquals(HeatSource("Jumping", 3), movementHeatSource(MovementMode.JUMP, 3))
        assertEquals(HeatSource("Jumping", 5), movementHeatSource(MovementMode.JUMP, 5))
    }
}
