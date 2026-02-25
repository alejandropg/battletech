package battletech.tui.hex

import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HexRendererTest {

    @Test
    fun `renders clear hex border characters`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        // Row 0: "  _____  "
        assertEquals("_", buffer.get(2, 0).char)
        assertEquals("_", buffer.get(6, 0).char)
        // Row 1: " /     \ "
        assertEquals("/", buffer.get(1, 1).char)
        assertEquals("\\", buffer.get(7, 1).char)
        // Row 2: "/       \"
        assertEquals("/", buffer.get(0, 2).char)
        assertEquals("\\", buffer.get(8, 2).char)
        // Row 3: "\       /"
        assertEquals("\\", buffer.get(0, 3).char)
        assertEquals("/", buffer.get(8, 3).char)
        // Row 4: " \_____/ "
        assertEquals("\\", buffer.get(1, 4).char)
        assertEquals("_", buffer.get(2, 4).char)
        assertEquals("/", buffer.get(7, 4).char)
    }

    @Test
    fun `water hex renders terrain icon with blue foreground`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.BLUE, buffer.get(2, 1).fg)
    }

    @Test
    fun `light woods hex renders terrain icon with green foreground`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.GREEN, buffer.get(2, 1).fg)
    }

    @Test
    fun `heavy woods hex renders terrain icon with dark green foreground`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.HEAVY_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.DARK_GREEN, buffer.get(2, 1).fg)
    }

    @Test
    fun `elevation is rendered in content area`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), elevation = 2)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(String(Character.toChars(0xF03A8)), buffer.get(6, 1).char)
    }

    @Test
    fun `cursor highlight changes border color to yellow`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.CURSOR)

        assertEquals(Color.BRIGHT_YELLOW, buffer.get(1, 1).fg) // '/' border
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(7, 1).fg) // '\' border
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(2, 0).fg) // '_' top
    }

    @Test
    fun `reachable highlight shows dot marker at center`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.REACHABLE_WALK)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(Color.DEFAULT, buffer.get(4, 2).bg)
    }

    @Test
    fun `path highlight without mode shows star`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.PATH)

        assertEquals("*", buffer.get(4, 2).char)
    }

    @Test
    fun `path highlight with WALK mode shows walk icon`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.PATH, MovementMode.WALK)

        assertEquals(String(Character.toChars(0xF0583)), buffer.get(4, 2).char)
    }

    @Test
    fun `path highlight with RUN mode shows run icon`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.PATH, MovementMode.RUN)

        assertEquals(String(Character.toChars(0xF046E)), buffer.get(4, 2).char)
    }

    @Test
    fun `path highlight with JUMP mode shows jump icon`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.PATH, MovementMode.JUMP)

        assertEquals(String(Character.toChars(0xF14DE)), buffer.get(4, 2).char)
    }
}
