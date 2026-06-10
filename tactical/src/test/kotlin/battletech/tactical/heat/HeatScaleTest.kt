package battletech.tactical.heat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HeatScaleTest {

    @Test
    fun `movement penalty steps every five heat`() {
        assertEquals(0, HeatScale.movementPenalty(4))
        assertEquals(1, HeatScale.movementPenalty(5))
        assertEquals(1, HeatScale.movementPenalty(9))
        assertEquals(2, HeatScale.movementPenalty(10))
        assertEquals(3, HeatScale.movementPenalty(15))
        assertEquals(4, HeatScale.movementPenalty(20))
        assertEquals(5, HeatScale.movementPenalty(25))
    }

    @Test
    fun `movement penalty caps at five`() {
        assertEquals(5, HeatScale.movementPenalty(30))
        assertEquals(5, HeatScale.movementPenalty(45))
    }

    @Test
    fun `to-hit penalty follows the table`() {
        assertEquals(0, HeatScale.toHitPenalty(7))
        assertEquals(1, HeatScale.toHitPenalty(8))
        assertEquals(1, HeatScale.toHitPenalty(12))
        assertEquals(2, HeatScale.toHitPenalty(13))
        assertEquals(3, HeatScale.toHitPenalty(18))
        assertEquals(4, HeatScale.toHitPenalty(24))
        assertEquals(4, HeatScale.toHitPenalty(30))
    }

    @Test
    fun `shutdown avoid target follows the table`() {
        assertNull(HeatScale.shutdownAvoidTarget(13))
        assertEquals(4, HeatScale.shutdownAvoidTarget(14))
        assertEquals(6, HeatScale.shutdownAvoidTarget(17))
        assertEquals(8, HeatScale.shutdownAvoidTarget(22))
        assertEquals(10, HeatScale.shutdownAvoidTarget(26))
    }

    @Test
    fun `auto shutdown at thirty`() {
        assertFalse(HeatScale.isAutoShutdown(29))
        assertTrue(HeatScale.isAutoShutdown(30))
    }

    @Test
    fun `ammo explosion avoid target follows the table`() {
        assertNull(HeatScale.ammoExplosionAvoidTarget(14))
        assertEquals(4, HeatScale.ammoExplosionAvoidTarget(15))
        assertEquals(6, HeatScale.ammoExplosionAvoidTarget(19))
        assertEquals(8, HeatScale.ammoExplosionAvoidTarget(23))
        assertEquals(10, HeatScale.ammoExplosionAvoidTarget(28))
    }
}
