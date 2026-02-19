package battletech.tui.hex

import battletech.tactical.model.HexCoordinates
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class HexLayoutTest {

    @Test
    fun `hexToScreen for origin hex`() {
        val (charX, charY) = HexLayout.hexToScreen(0, 0)

        assertEquals(0, charX)
        assertEquals(0, charY)
    }

    @Test
    fun `hexToScreen for even column`() {
        val (charX, charY) = HexLayout.hexToScreen(2, 1)

        assertEquals(14, charX) // 2 * 7
        assertEquals(4, charY)  // 1 * 4, no odd offset
    }

    @Test
    fun `hexToScreen for odd column applies row offset`() {
        val (charX, charY) = HexLayout.hexToScreen(1, 0)

        assertEquals(7, charX)  // 1 * 7
        assertEquals(2, charY)  // 0 * 4 + 2 (odd column offset)
    }

    @Test
    fun `hexToScreen for odd column with nonzero row`() {
        val (charX, charY) = HexLayout.hexToScreen(3, 2)

        assertEquals(21, charX) // 3 * 7
        assertEquals(10, charY) // 2 * 4 + 2
    }

    @Test
    fun `screenToHex recovers even column hex`() {
        val coords = HexLayout.screenToHex(14, 4, 0, 0)

        assertEquals(HexCoordinates(2, 1), coords)
    }

    @Test
    fun `screenToHex recovers odd column hex`() {
        val coords = HexLayout.screenToHex(7, 2, 0, 0)

        assertEquals(HexCoordinates(1, 0), coords)
    }

    @Test
    fun `screenToHex recovers origin hex`() {
        val coords = HexLayout.screenToHex(0, 0, 0, 0)

        assertEquals(HexCoordinates(0, 0), coords)
    }

    @Test
    fun `screenToHex applies scroll offset`() {
        val coords = HexLayout.screenToHex(0, 0, 14, 4)

        assertEquals(HexCoordinates(2, 1), coords)
    }

    @Test
    fun `round trip hexToScreen then screenToHex recovers coordinates`() {
        for (col in 0..5) {
            for (row in 0..5) {
                val (charX, charY) = HexLayout.hexToScreen(col, row)
                val recovered = HexLayout.screenToHex(charX, charY, 0, 0)
                assertEquals(
                    HexCoordinates(col, row), recovered,
                    "Round trip failed for ($col, $row)"
                )
            }
        }
    }

    @Test
    fun `screenToHex with negative result returns null`() {
        val coords = HexLayout.screenToHex(0, 0, -14, 0)

        assertNull(coords)
    }
}
