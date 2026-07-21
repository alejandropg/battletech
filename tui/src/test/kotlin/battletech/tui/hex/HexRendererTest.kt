package battletech.tui.hex

import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Terrain
import battletech.tactical.model.MovementMode
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

        assertEquals(Color.BLUE, buffer.get(2, 1).style.fg)
    }

    @Test
    fun `light woods hex renders terrain icon with green foreground`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.GREEN, buffer.get(2, 1).style.fg)
    }

    @Test
    fun `heavy woods hex renders terrain icon with dark green foreground`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.HEAVY_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.DARK_GREEN, buffer.get(2, 1).style.fg)
    }

    @Test
    fun `elevation is rendered in content area`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), elevation = 2)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(String(Character.toChars(0xF03A8)), buffer.get(6, 1).char)
    }

    @Test
    fun `light woods hex tints background with soft light green`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.WOODS_LIGHT_BG, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `heavy woods hex tints background with darker green`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.HEAVY_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.WOODS_HEAVY_BG, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `shallow water hex tints background with lighter blue`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = 1)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.WATER_SHALLOW_BG, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `deep water hex tints background with darker blue`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = 3)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.WATER_DEEP_BG, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `elevation 1 hill tints background with darker brown`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), elevation = 1)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.ELEVATION_LOW_BG, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `higher elevation hill tints background with lighter brown`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), elevation = 3)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.ELEVATION_HIGH_BG, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `clear level-0 hex has no background tint`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.DEFAULT, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `terrain wins over elevation - elevated forest stays green`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS, elevation = 2)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals(Color.WOODS_LIGHT_BG, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `border glyphs carry the terrain background tint`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        assertEquals("/", buffer.get(0, 2).char) // left border glyph
        assertEquals(Color.WOODS_LIGHT_BG, buffer.get(0, 2).style.bg)
    }

    @Test
    fun `hex does not tint its own top edge but tints its bottom edge`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        // Top edge (row 0) belongs to the hex above — inherited, so DEFAULT here.
        assertEquals(Color.DEFAULT, buffer.get(2, 0).style.bg)
        // Bottom edge (row 4) is this hex's own — tinted.
        assertEquals(Color.WOODS_LIGHT_BG, buffer.get(2, 4).style.bg)
    }

    @Test
    fun `top edge inherits the background of the hex above`() {
        val buffer = ScreenBuffer(10, 10)
        val woods = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)
        val clear = Hex(HexCoordinates(0, 1))

        // The clear hex one ROW_STRIDE down shares its top edge (row 0 at y=4) with the woods
        // hex's bottom edge (row 4 at y=4). Drawn after, it must not overwrite the woods tint.
        HexRenderer.render(buffer, 0, 0, woods, HexHighlight.NONE)
        HexRenderer.render(buffer, 0, HexGeometry.ROW_STRIDE, clear, HexHighlight.NONE)

        assertEquals(Color.WOODS_LIGHT_BG, buffer.get(2, HexGeometry.ROW_STRIDE).style.bg)
    }

    @Test
    fun `reachability dot inherits the terrain background`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.REACHABLE_WALK)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(Color.WOODS_LIGHT_BG, buffer.get(4, 2).style.bg)
    }

    @Test
    fun `facing arrows inherit the terrain background`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)
        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        HexRenderer.renderFacingArrows(buffer, 0, 0, setOf(HexDirection.N), Color.WHITE)

        // N arrow sits at (x+4, y+2)
        assertEquals(Color.WOODS_LIGHT_BG, buffer.get(4, 2).style.bg)
    }

    @Test
    fun `facing numbers inherit the terrain background`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)
        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.NONE)

        HexRenderer.renderFacingNumbers(buffer, 0, 0, setOf(HexDirection.N))

        assertEquals("1", buffer.get(4, 2).char)
        assertEquals(Color.WOODS_LIGHT_BG, buffer.get(4, 2).style.bg)
    }

    @Test
    fun `cursor highlight changes border color to yellow`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.CURSOR)

        assertEquals(Color.BRIGHT_YELLOW, buffer.get(1, 1).style.fg) // '/' border
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(7, 1).style.fg) // '\' border
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(2, 0).style.fg) // '_' top
    }

    @Test
    fun `reachable highlight shows dot marker at center`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.REACHABLE_WALK)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(Color.DEFAULT, buffer.get(4, 2).style.bg)
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

    @Test
    fun `attack range highlight shows gray dot at center`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.ATTACK_RANGE)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(Color.GRAY, buffer.get(4, 2).style.fg)
    }

    @Test
    fun `line of sight highlight shows white dot at center`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.LINE_OF_SIGHT)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(Color.YELLOW, buffer.get(4, 2).style.fg)
    }

    @Test
    fun `line of sight selected highlight shows red icon at center`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(buffer, 0, 0, hex, HexHighlight.LINE_OF_SIGHT_SELECTED)

        assertEquals(targetIcon(), buffer.get(4, 2).char)
        assertEquals(Color.RED, buffer.get(4, 2).style.fg)
    }
}
