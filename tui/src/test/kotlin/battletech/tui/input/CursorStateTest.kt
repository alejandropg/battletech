package battletech.tui.input

import battletech.tui.aGameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class CursorStateTest {

    @Test
    fun `move north from center`() {
        val map = aGameMap(cols = 5, rows = 5)
        val cursor = CursorState(HexCoordinates(2, 2))

        val moved = cursor.moveCursor(HexDirection.N, map)

        assertEquals(HexCoordinates(2, 1), moved.position)
    }

    @Test
    fun `move south from center`() {
        val map = aGameMap(cols = 5, rows = 5)
        val cursor = CursorState(HexCoordinates(2, 2))

        val moved = cursor.moveCursor(HexDirection.S, map)

        assertEquals(HexCoordinates(2, 3), moved.position)
    }

    @Test
    fun `move off north edge stays in place`() {
        val map = aGameMap(cols = 3, rows = 3)
        val cursor = CursorState(HexCoordinates(0, 0))

        val moved = cursor.moveCursor(HexDirection.N, map)

        assertEquals(HexCoordinates(0, 0), moved.position)
    }

    @Test
    fun `move off west edge stays in place`() {
        val map = aGameMap(cols = 3, rows = 3)
        val cursor = CursorState(HexCoordinates(0, 0))

        val moved = cursor.moveCursor(HexDirection.NW, map)

        assertEquals(HexCoordinates(0, 0), moved.position)
    }

    @Test
    fun `move southeast from even column`() {
        val map = aGameMap(cols = 5, rows = 5)
        val cursor = CursorState(HexCoordinates(2, 2))

        val moved = cursor.moveCursor(HexDirection.SE, map)

        assertEquals(HexCoordinates(3, 2), moved.position)
    }

    @Test
    fun `selected unit id is preserved on move`() {
        val map = aGameMap(cols = 5, rows = 5)
        val cursor = CursorState(HexCoordinates(2, 2), selectedUnitId = battletech.tactical.action.UnitId("u1"))

        val moved = cursor.moveCursor(HexDirection.N, map)

        assertEquals(battletech.tactical.action.UnitId("u1"), moved.selectedUnitId)
    }
}
