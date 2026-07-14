package battletech.tactical.attack.weapon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FireWeaponActionDefinitionTest {

    private val definition = FireWeaponActionDefinition()

    @Test
    fun `rules include all weapon rules`() {
        assertThat(definition.rules).hasSize(6)
        assertThat(definition.rules.map { it::class }).containsExactlyInAnyOrder(
            WeaponNotDestroyedRule::class,
            HasAmmoRule::class,
            InRangeRule::class,
            HeatPenaltyRule::class,
            SubmergedWeaponRule::class,
            LineOfSightRule::class,
        )
    }
}
