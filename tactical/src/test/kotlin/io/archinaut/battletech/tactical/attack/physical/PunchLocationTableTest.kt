package io.archinaut.battletech.tactical.attack.physical

import io.archinaut.battletech.tactical.attack.HitLocation
import io.archinaut.battletech.tactical.model.MechLocation.CENTER_TORSO
import io.archinaut.battletech.tactical.model.MechLocation.HEAD
import io.archinaut.battletech.tactical.model.MechLocation.LEFT_ARM
import io.archinaut.battletech.tactical.model.MechLocation.LEFT_TORSO
import io.archinaut.battletech.tactical.model.MechLocation.RIGHT_ARM
import io.archinaut.battletech.tactical.model.MechLocation.RIGHT_TORSO
import io.archinaut.battletech.tactical.attack.AttackDirection
import io.archinaut.battletech.tactical.attack.AttackDirection.FRONT
import io.archinaut.battletech.tactical.attack.AttackDirection.LEFT
import io.archinaut.battletech.tactical.attack.AttackDirection.REAR
import io.archinaut.battletech.tactical.attack.AttackDirection.RIGHT
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
