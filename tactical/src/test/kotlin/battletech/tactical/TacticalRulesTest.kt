package battletech.tactical

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

internal class TacticalRulesTest {
    @Test
    fun `should calculate to-hit number`() {
        val rules = TacticalRules()
        assertEquals(7, rules.calculateToHit(4, 3))
    }
}
