package battletech.tactical.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class WeaponsTest {

    @Test
    fun `mediumLaser returns correct stats`() {
        val weapon = Weapons.mediumLaser()

        assertEquals("Medium Laser", weapon.name)
        assertEquals(5, weapon.damage)
        assertEquals(3, weapon.heat)
        assertEquals(0, weapon.minimumRange)
        assertEquals(3, weapon.shortRange)
        assertEquals(6, weapon.mediumRange)
        assertEquals(9, weapon.longRange)
        assertNull(weapon.ammo)
    }

    @Test
    fun `ac20 returns correct stats`() {
        val weapon = Weapons.ac20()

        assertEquals("AC/20", weapon.name)
        assertEquals(20, weapon.damage)
        assertEquals(7, weapon.heat)
        assertEquals(3, weapon.minimumRange)
        assertEquals(3, weapon.shortRange)
        assertEquals(6, weapon.mediumRange)
        assertEquals(9, weapon.longRange)
        assertEquals(5, weapon.ammo)
    }

    @Test
    fun `srm6 returns correct stats`() {
        val weapon = Weapons.srm6()

        assertEquals("SRM 6", weapon.name)
        assertEquals(12, weapon.damage)
        assertEquals(4, weapon.heat)
        assertEquals(15, weapon.ammo)
    }

    @Test
    fun `each call returns independent instance`() {
        val w1 = Weapons.mediumLaser()
        val w2 = Weapons.mediumLaser()

        assertNotSame(w1, w2)
    }
}
