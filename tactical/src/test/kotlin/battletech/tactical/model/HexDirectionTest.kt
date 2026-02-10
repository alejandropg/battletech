package battletech.tactical.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HexDirectionTest {

    @Test
    fun `rotate clockwise from N goes to NE`() {
        assertThat(HexDirection.N.rotateClockwise()).isEqualTo(HexDirection.NE)
    }

    @Test
    fun `rotate clockwise from NW wraps to N`() {
        assertThat(HexDirection.NW.rotateClockwise()).isEqualTo(HexDirection.N)
    }

    @Test
    fun `rotate counter-clockwise from N goes to NW`() {
        assertThat(HexDirection.N.rotateCounterClockwise()).isEqualTo(HexDirection.NW)
    }

    @Test
    fun `rotate counter-clockwise from NE goes to N`() {
        assertThat(HexDirection.NE.rotateCounterClockwise()).isEqualTo(HexDirection.N)
    }

    @Test
    fun `turn cost to same direction is zero`() {
        assertThat(HexDirection.N.turnCostTo(HexDirection.N)).isEqualTo(0)
    }

    @Test
    fun `turn cost to adjacent direction is one`() {
        assertThat(HexDirection.N.turnCostTo(HexDirection.NE)).isEqualTo(1)
        assertThat(HexDirection.N.turnCostTo(HexDirection.NW)).isEqualTo(1)
    }

    @Test
    fun `turn cost to opposite direction is three`() {
        assertThat(HexDirection.N.turnCostTo(HexDirection.S)).isEqualTo(3)
    }

    @Test
    fun `turn cost is two for directions two steps apart`() {
        assertThat(HexDirection.N.turnCostTo(HexDirection.SE)).isEqualTo(2)
        assertThat(HexDirection.N.turnCostTo(HexDirection.SW)).isEqualTo(2)
    }

    @Test
    fun `turn cost is symmetric`() {
        assertThat(HexDirection.NE.turnCostTo(HexDirection.SW))
            .isEqualTo(HexDirection.SW.turnCostTo(HexDirection.NE))
    }
}
