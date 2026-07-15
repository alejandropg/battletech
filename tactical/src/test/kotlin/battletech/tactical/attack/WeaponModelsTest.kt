package battletech.tactical.attack

import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.WeaponKind
import battletech.tactical.unit.WeaponModels
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class WeaponModelsTest {

    @Test
    fun `mediumLaser returns correct stats`() {
        val weapon = WeaponModels.mediumLaser

        assertEquals("Medium Laser", weapon.name)
        assertEquals(5, weapon.damage)
        assertEquals(3, weapon.heat)
        assertEquals(0, weapon.minimumRange)
        assertEquals(3, weapon.shortRange)
        assertEquals(6, weapon.mediumRange)
        assertEquals(9, weapon.longRange)
        assertEquals(WeaponKind.Energy, weapon.kind)
    }

    @Test
    fun `ac20 returns correct stats`() {
        val weapon = WeaponModels.ac20

        assertEquals("AC/20", weapon.name)
        assertEquals(20, weapon.damage)
        assertEquals(7, weapon.heat)
        assertEquals(3, weapon.minimumRange)
        assertEquals(3, weapon.shortRange)
        assertEquals(6, weapon.mediumRange)
        assertEquals(9, weapon.longRange)
        assertEquals(WeaponKind.Ballistic(AmmoType.AC20), weapon.kind)
    }

    @Test
    fun `srm6 returns correct stats`() {
        val weapon = WeaponModels.srm6

        assertEquals("SRM 6", weapon.name)
        assertEquals(12, weapon.damage)
        assertEquals(4, weapon.heat)
        assertEquals(WeaponKind.Missile(AmmoType.SRM6, clusterSize = 6, damagePerMissile = 2, missilesPerGroup = 1), weapon.kind)
    }

    @Test
    fun `each reference returns the same shared instance`() {
        val w1 = WeaponModels.mediumLaser
        val w2 = WeaponModels.mediumLaser

        assertEquals(w1, w2)
    }
}
