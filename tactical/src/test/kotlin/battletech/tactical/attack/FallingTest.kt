package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.HexDirection
import battletech.tactical.query.aUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FallingTest {

    @Test
    fun `a fall deals tonnage-based damage, changes facing, and leaves the unit prone`() {
        val unit = aUnit(tonnage = 50, facing = HexDirection.N)
        // location 2d6 = 3+4 = 7 -> Center Torso; facing d6 = 2 -> rotate 1 clockwise -> NE.
        val roller = DiceRoller.deterministic(3, 4, 2)

        val (fallen, result) = fall(unit, roller)

        assertThat(result.damage).isEqualTo(5) // ceil(50 / 10)
        assertThat(result.hitLocation).isEqualTo(HitLocation.CENTER_TORSO)
        assertThat(result.newFacing).isEqualTo(HexDirection.NE)

        assertThat(fallen.isProne).isTrue()
        assertThat(fallen.facing).isEqualTo(HexDirection.NE)
        assertThat(fallen.torsoFacing).isEqualTo(HexDirection.NE)
        assertThat(fallen.armor.centerTorso).isEqualTo(unit.armor.centerTorso - 5)
    }

    @Test
    fun `a facing roll of one keeps the original facing`() {
        val unit = aUnit(tonnage = 75, facing = HexDirection.S)
        // location 6+6 = 12 -> Head; facing d6 = 1 -> no change.
        val roller = DiceRoller.deterministic(6, 6, 1)

        val (fallen, result) = fall(unit, roller)

        assertThat(result.damage).isEqualTo(8) // ceil(75 / 10)
        assertThat(result.newFacing).isEqualTo(HexDirection.S)
        assertThat(fallen.facing).isEqualTo(HexDirection.S)
    }
}
