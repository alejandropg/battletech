package battletech.tactical.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HexDirectionTest {

    @Test
    fun `rotate clockwise from N goes to NE`() {
        assertEquals(HexDirection.NE, HexDirection.N.rotateClockwise())
    }

    @Test
    fun `rotate clockwise from NW wraps to N`() {
        assertEquals(HexDirection.N, HexDirection.NW.rotateClockwise())
    }

    @Test
    fun `rotate counter-clockwise from N goes to NW`() {
        assertEquals(HexDirection.NW, HexDirection.N.rotateCounterClockwise())
    }

    @Test
    fun `rotate counter-clockwise from NE goes to N`() {
        assertEquals(HexDirection.N, HexDirection.NE.rotateCounterClockwise())
    }

    @Test
    fun `turn cost to same direction is zero`() {
        assertEquals(0, HexDirection.N.turnCostTo(HexDirection.N))
    }

    @Test
    fun `turn cost to adjacent direction is one`() {
        assertEquals(1, HexDirection.N.turnCostTo(HexDirection.NE))
        assertEquals(1, HexDirection.N.turnCostTo(HexDirection.NW))
    }

    @Test
    fun `turn cost to opposite direction is three`() {
        assertEquals(3, HexDirection.N.turnCostTo(HexDirection.S))
    }

    @Test
    fun `turn cost is two for directions two steps apart`() {
        assertEquals(2, HexDirection.N.turnCostTo(HexDirection.SE))
        assertEquals(2, HexDirection.N.turnCostTo(HexDirection.SW))
    }

    @Test
    fun `turn cost is symmetric`() {
        assertEquals(HexDirection.SW.turnCostTo(HexDirection.NE), HexDirection.NE.turnCostTo(HexDirection.SW))
    }
}
