package battletech.tactical.unit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CriticalEffectsTest {

    @Test
    fun `sensor 1 crit yields to-hit penalty`() {
        assertThat(criticalEffects(CriticalComponent.SENSOR, 1))
            .containsExactly(CritEffect.ToHitPenalty(SENSOR_TO_HIT_PENALTY))
    }

    @Test
    fun `sensor 2 crits yields cannot fire`() {
        assertThat(criticalEffects(CriticalComponent.SENSOR, 2))
            .containsExactly(CritEffect.CannotFire)
    }

    @Test
    fun `gyro 1 crit yields psr penalty`() {
        assertThat(criticalEffects(CriticalComponent.GYRO, 1))
            .containsExactly(CritEffect.PsrPenalty(GYRO_PSR_PENALTY))
    }

    @Test
    fun `gyro 2 crits yields cannot stand`() {
        assertThat(criticalEffects(CriticalComponent.GYRO, 2))
            .containsExactly(CritEffect.CannotStand)
    }

    @Test
    fun `engine 1 crit yields heat per turn`() {
        assertThat(criticalEffects(CriticalComponent.ENGINE, 1))
            .containsExactly(CritEffect.HeatPerTurn(ENGINE_CRIT_HEAT_PER_HIT))
    }

    @Test
    fun `engine 2 crits yields doubled heat per turn`() {
        assertThat(criticalEffects(CriticalComponent.ENGINE, 2))
            .containsExactly(CritEffect.HeatPerTurn(2 * ENGINE_CRIT_HEAT_PER_HIT))
    }

    @Test
    fun `engine 3 crits yields no penalty - destruction handled separately`() {
        assertThat(criticalEffects(CriticalComponent.ENGINE, 3)).isEmpty()
    }

    @Test
    fun `life support 1 crit yields pilot damage when heat at least threshold`() {
        assertThat(criticalEffects(CriticalComponent.LIFE_SUPPORT, 1))
            .containsExactly(CritEffect.PilotDamageWhenHeatAtLeast(LIFE_SUPPORT_HEAT_THRESHOLD))
    }

    @Test
    fun `life support 2 crits yields pilot damage every turn`() {
        assertThat(criticalEffects(CriticalComponent.LIFE_SUPPORT, 2))
            .containsExactly(CritEffect.PilotDamageEachTurn)
    }

    @Test
    fun `all components at 0 hits yield no effects`() {
        for (component in CriticalComponent.entries) {
            assertThat(criticalEffects(component, 0)).isEmpty()
        }
    }
}
