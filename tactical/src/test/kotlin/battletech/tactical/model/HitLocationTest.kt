package battletech.tactical.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class HitLocationTest {

    @Test
    fun `roll 2 maps to CENTER_TORSO`() {
        assertEquals(HitLocation.CENTER_TORSO, HitLocationTable.roll(2))
    }

    @Test
    fun `roll 3 maps to RIGHT_ARM`() {
        assertEquals(HitLocation.RIGHT_ARM, HitLocationTable.roll(3))
    }

    @Test
    fun `roll 4 maps to RIGHT_ARM`() {
        assertEquals(HitLocation.RIGHT_ARM, HitLocationTable.roll(4))
    }

    @Test
    fun `roll 5 maps to RIGHT_LEG`() {
        assertEquals(HitLocation.RIGHT_LEG, HitLocationTable.roll(5))
    }

    @Test
    fun `roll 6 maps to RIGHT_TORSO`() {
        assertEquals(HitLocation.RIGHT_TORSO, HitLocationTable.roll(6))
    }

    @Test
    fun `roll 7 maps to CENTER_TORSO`() {
        assertEquals(HitLocation.CENTER_TORSO, HitLocationTable.roll(7))
    }

    @Test
    fun `roll 8 maps to LEFT_TORSO`() {
        assertEquals(HitLocation.LEFT_TORSO, HitLocationTable.roll(8))
    }

    @Test
    fun `roll 9 maps to LEFT_LEG`() {
        assertEquals(HitLocation.LEFT_LEG, HitLocationTable.roll(9))
    }

    @Test
    fun `roll 10 maps to LEFT_ARM`() {
        assertEquals(HitLocation.LEFT_ARM, HitLocationTable.roll(10))
    }

    @Test
    fun `roll 11 maps to LEFT_ARM`() {
        assertEquals(HitLocation.LEFT_ARM, HitLocationTable.roll(11))
    }

    @Test
    fun `roll 12 maps to HEAD`() {
        assertEquals(HitLocation.HEAD, HitLocationTable.roll(12))
    }

    @Test
    fun `roll below 2 throws`() {
        assertThrows<IllegalStateException> { HitLocationTable.roll(1) }
    }

    @Test
    fun `roll above 12 throws`() {
        assertThrows<IllegalStateException> { HitLocationTable.roll(13) }
    }
}
