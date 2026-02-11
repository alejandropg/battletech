package battletech.tactical.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HexCoordinatesTest {

    @Test
    fun `distance to same hex is zero`() {
        val hex = HexCoordinates(3, 4)

        assertEquals(0, hex.distanceTo(hex))
    }

    @Test
    fun `distance to adjacent hex is one`() {
        val origin = HexCoordinates(3, 3)

        assertEquals(1, origin.distanceTo(HexCoordinates(4, 3)))
        assertEquals(1, origin.distanceTo(HexCoordinates(2, 3)))
        assertEquals(1, origin.distanceTo(HexCoordinates(3, 2)))
        assertEquals(1, origin.distanceTo(HexCoordinates(3, 4)))
    }

    @Test
    fun `distance is symmetric`() {
        val a = HexCoordinates(2, 1)
        val b = HexCoordinates(5, 4)

        assertEquals(b.distanceTo(a), a.distanceTo(b))
    }

    @Test
    fun `distance to far hex`() {
        val origin = HexCoordinates(0, 0)
        val far = HexCoordinates(3, 3)

        assertEquals(5, origin.distanceTo(far))
    }

    @Test
    fun `distance across columns on even row`() {
        val a = HexCoordinates(0, 0)
        val b = HexCoordinates(2, 0)

        assertEquals(2, a.distanceTo(b))
    }

    @Test
    fun `distance with diagonal movement`() {
        val a = HexCoordinates(1, 1)
        val b = HexCoordinates(4, 2)

        assertEquals(3, a.distanceTo(b))
    }

    @Test
    fun `neighbors from even column`() {
        val hex = HexCoordinates(2, 2)

        assertEquals(HexCoordinates(2, 1), hex.neighbor(HexDirection.N))
        assertEquals(HexCoordinates(3, 1), hex.neighbor(HexDirection.NE))
        assertEquals(HexCoordinates(3, 2), hex.neighbor(HexDirection.SE))
        assertEquals(HexCoordinates(2, 3), hex.neighbor(HexDirection.S))
        assertEquals(HexCoordinates(1, 2), hex.neighbor(HexDirection.SW))
        assertEquals(HexCoordinates(1, 1), hex.neighbor(HexDirection.NW))
    }

    @Test
    fun `neighbors from odd column`() {
        val hex = HexCoordinates(3, 2)

        assertEquals(HexCoordinates(3, 1), hex.neighbor(HexDirection.N))
        assertEquals(HexCoordinates(4, 2), hex.neighbor(HexDirection.NE))
        assertEquals(HexCoordinates(4, 3), hex.neighbor(HexDirection.SE))
        assertEquals(HexCoordinates(3, 3), hex.neighbor(HexDirection.S))
        assertEquals(HexCoordinates(2, 3), hex.neighbor(HexDirection.SW))
        assertEquals(HexCoordinates(2, 2), hex.neighbor(HexDirection.NW))
    }

    @Test
    fun `neighbors returns all six adjacent hexes`() {
        val hex = HexCoordinates(2, 2)

        val neighbors = hex.neighbors()

        assertEquals(
            listOf(
                HexCoordinates(2, 1),
                HexCoordinates(3, 1),
                HexCoordinates(3, 2),
                HexCoordinates(2, 3),
                HexCoordinates(1, 2),
                HexCoordinates(1, 1)
            ),
            neighbors
        )
    }

    @Test
    fun `all neighbors are at distance one`() {
        val hex = HexCoordinates(3, 3)

        hex.neighbors().forEach { neighbor ->
            assertEquals(1, hex.distanceTo(neighbor))
        }
    }
}
