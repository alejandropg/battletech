package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.HitLocation.CENTER_TORSO
import battletech.tactical.attack.HitLocation.HEAD
import battletech.tactical.attack.HitLocation.LEFT_ARM
import battletech.tactical.attack.HitLocation.LEFT_TORSO
import battletech.tactical.attack.HitLocation.RIGHT_ARM
import battletech.tactical.attack.HitLocation.RIGHT_TORSO
import battletech.tactical.attack.physical.AttackDirection.FRONT
import battletech.tactical.attack.physical.AttackDirection.LEFT
import battletech.tactical.attack.physical.AttackDirection.REAR
import battletech.tactical.attack.physical.AttackDirection.RIGHT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PunchLocationTableTest {

    private fun assertColumn(direction: AttackDirection, expected: List<HitLocation>) {
        val actual = (1..6).map { PunchLocationTable.roll(it, direction) }
        assertEquals(expected, actual, "punch column for $direction")
    }

    @Test
    fun `front column`() {
        assertColumn(FRONT, listOf(LEFT_ARM, LEFT_TORSO, CENTER_TORSO, RIGHT_TORSO, RIGHT_ARM, HEAD))
    }

    @Test
    fun `rear column matches front`() {
        assertColumn(REAR, listOf(LEFT_ARM, LEFT_TORSO, CENTER_TORSO, RIGHT_TORSO, RIGHT_ARM, HEAD))
    }

    @Test
    fun `left column`() {
        assertColumn(LEFT, listOf(LEFT_TORSO, LEFT_ARM, LEFT_TORSO, CENTER_TORSO, LEFT_ARM, HEAD))
    }

    @Test
    fun `right column`() {
        assertColumn(RIGHT, listOf(RIGHT_TORSO, RIGHT_ARM, RIGHT_TORSO, CENTER_TORSO, RIGHT_ARM, HEAD))
    }
}
