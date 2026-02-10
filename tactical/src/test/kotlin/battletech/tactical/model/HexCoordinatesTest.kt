package battletech.tactical.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class HexCoordinatesTest {

    @Test
    fun `distance to same hex is zero`() {
        val hex = HexCoordinates(3, 4)

        assertThat(hex.distanceTo(hex)).isEqualTo(0)
    }

    @Test
    fun `distance to adjacent hex is one`() {
        val origin = HexCoordinates(3, 3)

        assertThat(origin.distanceTo(HexCoordinates(4, 3))).isEqualTo(1)
        assertThat(origin.distanceTo(HexCoordinates(2, 3))).isEqualTo(1)
        assertThat(origin.distanceTo(HexCoordinates(3, 2))).isEqualTo(1)
        assertThat(origin.distanceTo(HexCoordinates(3, 4))).isEqualTo(1)
    }

    @Test
    fun `distance is symmetric`() {
        val a = HexCoordinates(2, 1)
        val b = HexCoordinates(5, 4)

        assertThat(a.distanceTo(b)).isEqualTo(b.distanceTo(a))
    }

    @Test
    fun `distance to far hex`() {
        val origin = HexCoordinates(0, 0)
        val far = HexCoordinates(3, 3)

        assertThat(origin.distanceTo(far)).isEqualTo(5)
    }

    @Test
    fun `distance across columns on even row`() {
        val a = HexCoordinates(0, 0)
        val b = HexCoordinates(2, 0)

        assertThat(a.distanceTo(b)).isEqualTo(2)
    }

    @Test
    fun `distance with diagonal movement`() {
        val a = HexCoordinates(1, 1)
        val b = HexCoordinates(4, 2)

        assertThat(a.distanceTo(b)).isEqualTo(3)
    }

    @Test
    fun `neighbors from even column`() {
        val hex = HexCoordinates(2, 2)

        assertThat(hex.neighbor(HexDirection.N)).isEqualTo(HexCoordinates(2, 1))
        assertThat(hex.neighbor(HexDirection.NE)).isEqualTo(HexCoordinates(3, 1))
        assertThat(hex.neighbor(HexDirection.SE)).isEqualTo(HexCoordinates(3, 2))
        assertThat(hex.neighbor(HexDirection.S)).isEqualTo(HexCoordinates(2, 3))
        assertThat(hex.neighbor(HexDirection.SW)).isEqualTo(HexCoordinates(1, 2))
        assertThat(hex.neighbor(HexDirection.NW)).isEqualTo(HexCoordinates(1, 1))
    }

    @Test
    fun `neighbors from odd column`() {
        val hex = HexCoordinates(3, 2)

        assertThat(hex.neighbor(HexDirection.N)).isEqualTo(HexCoordinates(3, 1))
        assertThat(hex.neighbor(HexDirection.NE)).isEqualTo(HexCoordinates(4, 2))
        assertThat(hex.neighbor(HexDirection.SE)).isEqualTo(HexCoordinates(4, 3))
        assertThat(hex.neighbor(HexDirection.S)).isEqualTo(HexCoordinates(3, 3))
        assertThat(hex.neighbor(HexDirection.SW)).isEqualTo(HexCoordinates(2, 3))
        assertThat(hex.neighbor(HexDirection.NW)).isEqualTo(HexCoordinates(2, 2))
    }

    @Test
    fun `neighbors returns all six adjacent hexes`() {
        val hex = HexCoordinates(2, 2)

        val neighbors = hex.neighbors()

        assertThat(neighbors).hasSize(6)
        assertThat(neighbors).containsExactlyInAnyOrder(
            HexCoordinates(2, 1),
            HexCoordinates(3, 1),
            HexCoordinates(3, 2),
            HexCoordinates(2, 3),
            HexCoordinates(1, 2),
            HexCoordinates(1, 1),
        )
    }

    @Test
    fun `all neighbors are at distance one`() {
        val hex = HexCoordinates(3, 3)

        hex.neighbors().forEach { neighbor ->
            assertThat(hex.distanceTo(neighbor)).isEqualTo(1)
        }
    }
}
