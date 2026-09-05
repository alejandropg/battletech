package battletech.tui.animation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class AnimationGridTest {

    @Test
    fun `pyRound is half-to-even at exact half boundaries, unlike Kotlin's half-up roundToInt`() {
        assertEquals(2, pyRound(2.5))
        assertEquals(4, pyRound(3.5))
        assertEquals(0, pyRound(0.5))
        assertEquals(-2, pyRound(-2.5))
    }

    @Test
    fun `pointBetween clamps progress to 0 point 0 to 1 point 0`() {
        val origin = point(0, 0)
        val target = point(10, 0)
        assertEquals(0.0, pointBetween(origin, target, -5.0).first)
        assertEquals(10.0, pointBetween(origin, target, 5.0).first)
        assertEquals(5.0, pointBetween(origin, target, 0.5).first)
    }
}
