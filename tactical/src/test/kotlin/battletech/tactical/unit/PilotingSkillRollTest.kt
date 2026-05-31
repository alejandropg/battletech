package battletech.tactical.unit

import battletech.tactical.dice.DiceRoller
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PilotingSkillRollTest {

    @Test
    fun `passes when the roll meets the piloting skill`() {
        val pilot = aUnit(pilotingSkill = 4)
        val result = pilotingSkillRoll(pilot, DiceRoller.deterministic(3, 3)) // 6 >= 4

        assertThat(result.targetNumber).isEqualTo(4)
        assertThat(result.roll.total).isEqualTo(6)
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `fails when the roll is below the piloting skill`() {
        val pilot = aUnit(pilotingSkill = 5)
        val result = pilotingSkillRoll(pilot, DiceRoller.deterministic(1, 1)) // 2 < 5

        assertThat(result.passed).isFalse()
    }

    @Test
    fun `modifiers raise the target number`() {
        val pilot = aUnit(pilotingSkill = 4)
        val result = pilotingSkillRoll(pilot, DiceRoller.deterministic(2, 3), modifier = 2) // 5 < 6

        assertThat(result.targetNumber).isEqualTo(6)
        assertThat(result.passed).isFalse()
    }
}
