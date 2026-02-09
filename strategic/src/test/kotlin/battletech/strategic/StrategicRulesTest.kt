package battletech.strategic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

internal class StrategicRulesTest {
    @Test
    fun `should calculate campaign movement`() {
        val rules = StrategicRules()
        assertEquals(10, rules.calculateCampaignMovement(5))
    }
}
