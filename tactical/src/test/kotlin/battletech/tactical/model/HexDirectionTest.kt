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
}
