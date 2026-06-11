package battletech.tactical.attack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

internal class HitLocationTest {

    @ParameterizedTest
    @CsvSource(
        "2, CENTER_TORSO",
        "3, RIGHT_ARM",
        "4, RIGHT_ARM",
        "5, RIGHT_LEG",
        "6, RIGHT_TORSO",
        "7, CENTER_TORSO",
        "8, LEFT_TORSO",
        "9, LEFT_LEG",
        "10, LEFT_ARM",
        "11, LEFT_ARM",
        "12, HEAD",
    )
    fun `roll maps to hit location`(roll: Int, expected: HitLocation) {
        assertEquals(expected, HitLocationTable.roll(roll))
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 13])
    fun `roll outside 2-12 throws`(roll: Int) {
        assertThrows<IllegalStateException> { HitLocationTable.roll(roll) }
    }
}
