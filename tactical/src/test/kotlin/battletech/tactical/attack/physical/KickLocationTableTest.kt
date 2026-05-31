package battletech.tactical.attack.physical

import battletech.tactical.attack.HitLocation.LEFT_LEG
import battletech.tactical.attack.HitLocation.RIGHT_LEG
import battletech.tactical.attack.physical.AttackDirection.FRONT
import battletech.tactical.attack.physical.AttackDirection.LEFT
import battletech.tactical.attack.physical.AttackDirection.REAR
import battletech.tactical.attack.physical.AttackDirection.RIGHT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class KickLocationTableTest {

    @Test
    fun `front hits right leg on 1-3 and left leg on 4-6`() {
        assertEquals(listOf(RIGHT_LEG, RIGHT_LEG, RIGHT_LEG, LEFT_LEG, LEFT_LEG, LEFT_LEG),
            (1..6).map { KickLocationTable.roll(it, FRONT) })
    }

    @Test
    fun `rear hits right leg on 1-3 and left leg on 4-6`() {
        assertEquals(listOf(RIGHT_LEG, RIGHT_LEG, RIGHT_LEG, LEFT_LEG, LEFT_LEG, LEFT_LEG),
            (1..6).map { KickLocationTable.roll(it, REAR) })
    }

    @Test
    fun `left side always hits the left leg`() {
        assertEquals(List(6) { LEFT_LEG }, (1..6).map { KickLocationTable.roll(it, LEFT) })
    }

    @Test
    fun `right side always hits the right leg`() {
        assertEquals(List(6) { RIGHT_LEG }, (1..6).map { KickLocationTable.roll(it, RIGHT) })
    }
}
