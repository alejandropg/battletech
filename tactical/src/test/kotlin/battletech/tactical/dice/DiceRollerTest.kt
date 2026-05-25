package battletech.tactical.dice

import battletech.tactical.dice.DiceRoll
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal class DiceRollerTest {

    @Test
    fun `RandomDiceRoller d6 produces 1 through 6 inclusive`() {
        val roller = RandomDiceRoller(Random(42))
        repeat(1000) {
            assertThat(roller.d6()).isBetween(1, 6)
        }
    }

    @Test
    fun `RandomDiceRoller produces the same sequence as raw Random nextInt(1,7) for same seed`() {
        val expected = Random(42).let { r -> List(20) { r.nextInt(1, 7) } }
        val actual = RandomDiceRoller(Random(42)).let { r -> List(20) { r.d6() } }
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `roll2d6 sums two d6 in order`() {
        val roller = ScriptedDiceRoller(listOf(3, 5))
        assertThat(roller.roll2d6()).isEqualTo(DiceRoll(3, 5))
    }

    @Test
    fun `d6(count) pops rolls in order`() {
        val roller = ScriptedDiceRoller(listOf(1, 2, 3, 4))
        assertThat(roller.d6(count = 4)).containsExactly(1, 2, 3, 4)
    }

    @Test
    fun `ScriptedDiceRoller throws when exhausted`() {
        val roller = ScriptedDiceRoller(listOf(6))
        roller.d6()
        assertThatThrownBy { roller.d6() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("exhausted")
    }

    @Test
    fun `ScriptedDiceRoller rejects values outside 1 to 6`() {
        val roller = ScriptedDiceRoller(listOf(7))
        assertThatThrownBy { roller.d6() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("out of range")
    }

    @Test
    fun `DiceRoller seeded factory matches RandomDiceRoller with same seed`() {
        val viaFactory = List(10) { DiceRoller.seeded(7).d6() }
        val viaCtor = List(10) { RandomDiceRoller(Random(7)).d6() }
        assertThat(viaFactory).isEqualTo(viaCtor)
    }

    @Test
    fun `DiceRoller deterministic factory pops in order`() {
        val roller = DiceRoller.deterministic(1, 2, 3)
        assertThat(roller.d6()).isEqualTo(1)
        assertThat(roller.d6()).isEqualTo(2)
        assertThat(roller.d6()).isEqualTo(3)
    }
}
