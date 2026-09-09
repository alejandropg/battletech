package tenterexample

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ExampleSmokeTest {

    @Test
    fun `headless consumer renders list help and animation through public tenter seams`() {
        val result = runHeadlessSmoke()

        assertEquals(40, result.renderedRows)
        assertTrue(result.helpContainsMovement)
        assertTrue(result.animationCompleted)
    }
}
