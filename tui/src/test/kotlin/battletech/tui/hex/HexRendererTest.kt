package battletech.tui.hex

import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HexRendererTest {

    @Test
    fun `renders clear hex border characters`() {
        val buffer = ScreenBuffer(10, 5)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        // Top row: " _____ "
        assertEquals('_', buffer.get(1, 0).char)
        assertEquals('_', buffer.get(5, 0).char)
        // Second row: "/     \"
        assertEquals('/', buffer.get(0, 1).char)
        assertEquals('\\', buffer.get(6, 1).char)
        // Third row: "\     /"
        assertEquals('\\', buffer.get(0, 2).char)
        assertEquals('/', buffer.get(6, 2).char)
        // Bottom row: " \_____/ "
        assertEquals('\\', buffer.get(1, 3).char)
        assertEquals('_', buffer.get(2, 3).char)
        assertEquals('/', buffer.get(5, 3).char)
    }

    @Test
    fun `water hex has blue background in content area`() {
        val buffer = ScreenBuffer(10, 5)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        // Content area cells should have blue background
        assertEquals(Color.BLUE, buffer.get(1, 1).bg)
        assertEquals(Color.BLUE, buffer.get(5, 1).bg)
        assertEquals(Color.BLUE, buffer.get(1, 2).bg)
    }

    @Test
    fun `light woods hex has green background`() {
        val buffer = ScreenBuffer(10, 5)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.GREEN, buffer.get(3, 1).bg)
    }

    @Test
    fun `heavy woods hex has dark green background`() {
        val buffer = ScreenBuffer(10, 5)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.HEAVY_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.DARK_GREEN, buffer.get(3, 1).bg)
    }

    @Test
    fun `elevation is rendered in content area`() {
        val buffer = ScreenBuffer(10, 5)
        val hex = Hex(HexCoordinates(0, 0), elevation = 2)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals('2', buffer.get(5, 1).char)
    }

    @Test
    fun `cursor highlight changes border color to yellow`() {
        val buffer = ScreenBuffer(10, 5)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.CURSOR)

        assertEquals(Color.BRIGHT_YELLOW, buffer.get(0, 1).fg) // '/' border
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(6, 1).fg) // '\' border
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(1, 0).fg) // '_' top
    }

    @Test
    fun `reachable highlight uses cyan background in content area`() {
        val buffer = ScreenBuffer(10, 5)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.REACHABLE)

        assertEquals(Color.CYAN, buffer.get(3, 1).bg)
    }

    @Test
    fun `path highlight uses yellow background in content area`() {
        val buffer = ScreenBuffer(10, 5)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.PATH)

        assertEquals(Color.YELLOW, buffer.get(3, 1).bg)
    }
}
