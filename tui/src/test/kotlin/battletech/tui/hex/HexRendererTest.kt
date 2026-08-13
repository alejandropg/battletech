package battletech.tui.hex

import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tui.screen.BoardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer
import tenter.screen.UiRole

internal class HexRendererTest {

    @Test
    fun `renders clear hex border characters`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

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

    // ---- terrain fill roles (theme-independent — assert the role, not a resolved color) --------

    @Test
    fun `clear hex fills with TERRAIN_CLEAR_BG`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.TERRAIN_CLEAR_BG, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `light woods hex fills with TERRAIN_WOODS_LIGHT_BG and icons with TERRAIN_WOODS_LIGHT_ICON`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, buffer.get(4, 3).style.bg)
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_ICON, buffer.get(2, 1).style.fg)
    }

    @Test
    fun `heavy woods hex fills with TERRAIN_WOODS_HEAVY_BG and icons with TERRAIN_WOODS_HEAVY_ICON`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.HEAVY_WOODS)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.TERRAIN_WOODS_HEAVY_BG, buffer.get(4, 3).style.bg)
        assertEquals(BoardRole.TERRAIN_WOODS_HEAVY_ICON, buffer.get(2, 1).style.fg)
    }

    @Test
    fun `rough hex fills with TERRAIN_ROUGH_BG and icons with TERRAIN_ROUGH_ICON`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.ROUGH)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.TERRAIN_ROUGH_BG, buffer.get(4, 3).style.bg)
        assertEquals(BoardRole.TERRAIN_ROUGH_ICON, buffer.get(2, 1).style.fg)
    }

    @Test
    fun `shallow water (depth less than or equal to 1) fills with TERRAIN_WATER_SHALLOW_BG`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = 1)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.TERRAIN_WATER_SHALLOW_BG, buffer.get(4, 3).style.bg)
        assertEquals(BoardRole.TERRAIN_WATER_ICON, buffer.get(2, 1).style.fg)
    }

    @Test
    fun `deep water (depth greater than or equal to 2) fills with TERRAIN_WATER_DEEP_BG`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = 3)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.TERRAIN_WATER_DEEP_BG, buffer.get(4, 3).style.bg)
        // The water terrain icon color does not vary with depth — only the fill does.
        assertEquals(BoardRole.TERRAIN_WATER_ICON, buffer.get(2, 1).style.fg)
    }

    @Test
    fun `elevations use solid numeric badge glyphs`() {
        val codePoints = 0xF0F0F..0xF0F17

        for ((elevation, codePoint) in (1..9).zip(codePoints)) {
            val buffer = ScreenBuffer(10, 6)
            val hex = Hex(HexCoordinates(0, 0), elevation = elevation)

            HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

            assertEquals(String(Character.toChars(codePoint)), buffer.get(6, 1).char, "elevation=$elevation")
        }
    }

    @Test
    fun `depths use outline numeric badge glyphs`() {
        val codePoints = listOf(
            0xF03A5,
            0xF03A8,
            0xF03AB,
            0xF03B2,
            0xF03AF,
            0xF03B4,
            0xF03B7,
            0xF03BA,
            0xF03BD,
        )

        for ((depth, codePoint) in (1..9).zip(codePoints)) {
            val buffer = ScreenBuffer(10, 6)
            val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = depth)

            HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

            val badge = buffer.get(6, 1)
            val expectedBg = if (depth == 1) BoardRole.TERRAIN_WATER_SHALLOW_BG else BoardRole.TERRAIN_WATER_DEEP_BG
            assertEquals(String(Character.toChars(codePoint)), badge.char, "depth=$depth")
            assertEquals(expectedBg, badge.style.bg, "depth=$depth")
        }
    }

    @Test
    fun `elevation badge is rendered instead of depth badge when both are present`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, elevation = 2, depth = 3)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(String(Character.toChars(0xF0F10)), buffer.get(6, 1).char)
    }

    @Test
    fun `zero elevation and depth render no badge — content stays the plain terrain fill`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.TERRAIN_CLEAR_BG, buffer.get(6, 1).style.bg)
    }

    @Test
    fun `elevation never changes a material terrain's fill (woods, water, rough)`() {
        val cases = listOf(
            Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS, elevation = 2) to BoardRole.TERRAIN_WOODS_LIGHT_BG,
            Hex(HexCoordinates(0, 0), terrain = Terrain.HEAVY_WOODS, elevation = 1) to BoardRole.TERRAIN_WOODS_HEAVY_BG,
            Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = 1, elevation = 3) to BoardRole.TERRAIN_WATER_SHALLOW_BG,
            Hex(HexCoordinates(0, 0), terrain = Terrain.ROUGH, elevation = 2) to BoardRole.TERRAIN_ROUGH_BG,
        )
        for ((hex, expectedFill) in cases) {
            val buffer = ScreenBuffer(10, 6)
            HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)
            // Content cell away from the badge column (x+6) — the fill, not the badge.
            assertEquals(expectedFill, buffer.get(4, 3).style.bg, "terrain=${hex.terrain} elevation=${hex.elevation}")
        }
    }

    @Test
    fun `a CLEAR hex with no elevation still fills with TERRAIN_CLEAR_BG`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.CLEAR, elevation = 0)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.TERRAIN_CLEAR_BG, buffer.get(4, 3).style.bg)
    }

    @Test
    fun `a CLEAR elevated hex fills the WHOLE hex with its elevation tier color, not just the badge cell`() {
        // With no material terrain of its own, an elevated clear hex reads as a hill: the
        // elevation color fills the entire hex (border, content, and badge cell alike), unlike a
        // material terrain, where elevation only tints the single badge cell.
        val cases = listOf(1 to BoardRole.ELEVATION_1_BADGE_BG, 2 to BoardRole.ELEVATION_2_BADGE_BG, 3 to BoardRole.ELEVATION_HIGH_BADGE_BG)
        for ((elevation, expectedFill) in cases) {
            val buffer = ScreenBuffer(10, 6)
            val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.CLEAR, elevation = elevation)

            HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

            assertEquals(expectedFill, buffer.get(4, 3).style.bg, "content cell, elevation=$elevation")
            assertEquals(expectedFill, buffer.get(0, 2).style.bg, "border cell, elevation=$elevation")
            assertEquals(expectedFill, buffer.get(6, 1).style.bg, "badge cell, elevation=$elevation")
        }
    }

    @Test
    fun `elevation 1 badge uses ELEVATION_1_BADGE_BG and ELEVATION_BADGE_FG at the badge cell`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), elevation = 1)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        val badge = buffer.get(6, 1)
        assertEquals(BoardRole.ELEVATION_1_BADGE_BG, badge.style.bg)
        assertEquals(BoardRole.ELEVATION_BADGE_FG, badge.style.fg)
    }

    @Test
    fun `elevation 2 badge uses ELEVATION_2_BADGE_BG`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), elevation = 2)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.ELEVATION_2_BADGE_BG, buffer.get(6, 1).style.bg)
    }

    @Test
    fun `elevation 3 and up badge uses ELEVATION_HIGH_BADGE_BG`() {
        for (elevation in listOf(3, 5, 9)) {
            val buffer = ScreenBuffer(10, 6)
            val hex = Hex(HexCoordinates(0, 0), elevation = elevation)

            HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

            assertEquals(BoardRole.ELEVATION_HIGH_BADGE_BG, buffer.get(6, 1).style.bg, "elevation=$elevation")
        }
    }

    @Test
    fun `the badge replaces the terrain fill only for its own cell`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS, elevation = 1)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.ELEVATION_1_BADGE_BG, buffer.get(6, 1).style.bg, "badge cell")
        assertNotEquals(BoardRole.ELEVATION_1_BADGE_BG, buffer.get(4, 3).style.bg, "content cell keeps the terrain fill")
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, buffer.get(4, 3).style.bg)
    }

    // ---- background inheritance across the border / adjacent hexes -----------------------------

    @Test
    fun `border glyphs carry the terrain background tint`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals("/", buffer.get(0, 2).char) // left border glyph
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, buffer.get(0, 2).style.bg)
    }

    @Test
    fun `hex does not tint its own top edge but tints its bottom edge`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        // Top edge (row 0) belongs to the hex above — inherited, so DEFAULT here.
        assertEquals(UiRole.DEFAULT, buffer.get(2, 0).style.bg)
        // Bottom edge (row 4) is this hex's own — tinted.
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, buffer.get(2, 4).style.bg)
    }

    @Test
    fun `top edge inherits the background of the hex above`() {
        val buffer = ScreenBuffer(10, 10)
        val woods = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)
        val clear = Hex(HexCoordinates(0, 1))

        // The clear hex one ROW_STRIDE down shares its top edge (row 0 at y=4) with the woods
        // hex's bottom edge (row 4 at y=4). Drawn after, it must not overwrite the woods tint.
        HexRenderer.render(Canvas.of(buffer), 0, 0, woods, HexHighlight.NONE)
        HexRenderer.render(Canvas.of(buffer), 0, HexGeometry.ROW_STRIDE, clear, HexHighlight.NONE)

        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, buffer.get(2, HexGeometry.ROW_STRIDE).style.bg)
    }

    @Test
    fun `reachability dot inherits the terrain background`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.REACHABLE_WALK)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, buffer.get(4, 2).style.bg)
    }

    @Test
    fun `facing arrows inherit the terrain background`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)
        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        HexRenderer.renderFacingArrows(Canvas.of(buffer), 0, 0, setOf(HexDirection.N), BoardRole.MOVE_WALK)

        // N arrow sits at (x+4, y+2)
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, buffer.get(4, 2).style.bg)
    }

    @Test
    fun `facing numbers inherit the terrain background and render in BOARD_ACTIVE`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)
        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        HexRenderer.renderFacingNumbers(Canvas.of(buffer), 0, 0, setOf(HexDirection.N))

        assertEquals("1", buffer.get(4, 2).char)
        assertEquals(BoardRole.BOARD_ACTIVE, buffer.get(4, 2).style.fg)
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, buffer.get(4, 2).style.bg)
    }

    // ---- cursor / highlight colors --------------------------------------------------------------

    @Test
    fun `cursor highlight changes border color to BOARD_ACTIVE`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.CURSOR)

        assertEquals(BoardRole.BOARD_ACTIVE, buffer.get(1, 1).style.fg) // '/' border
        assertEquals(BoardRole.BOARD_ACTIVE, buffer.get(7, 1).style.fg) // '\' border
        assertEquals(BoardRole.BOARD_ACTIVE, buffer.get(2, 0).style.fg) // '_' top
    }

    @Test
    fun `ordinary hex border uses BOARD_BORDER`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.NONE)

        assertEquals(BoardRole.BOARD_BORDER, buffer.get(1, 1).style.fg)
    }

    @Test
    fun `reachable walk highlight shows a MOVE_WALK dot at the overlay cell`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.REACHABLE_WALK)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(BoardRole.MOVE_WALK, buffer.get(4, 2).style.fg)
    }

    @Test
    fun `reachable run highlight shows a MOVE_RUN dot`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.REACHABLE_RUN)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(BoardRole.MOVE_RUN, buffer.get(4, 2).style.fg)
    }

    @Test
    fun `reachable jump highlight shows a MOVE_JUMP dot`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.REACHABLE_JUMP)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(BoardRole.MOVE_JUMP, buffer.get(4, 2).style.fg)
    }

    @Test
    fun `path highlight without mode shows star at the movement overlay cell`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.PATH)

        assertEquals("*", buffer.get(4, 2).char)
        assertEquals(BoardRole.BOARD_ACTIVE, buffer.get(4, 2).style.fg)
    }

    @Test
    fun `path highlight with WALK mode shows walk icon`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.PATH, MovementMode.WALK)

        assertEquals(String(Character.toChars(0xF0583)), buffer.get(4, 2).char)
    }

    @Test
    fun `path highlight with RUN mode shows run icon`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.PATH, MovementMode.RUN)

        assertEquals(String(Character.toChars(0xF046E)), buffer.get(4, 2).char)
    }

    @Test
    fun `path highlight with JUMP mode shows jump icon`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.PATH, MovementMode.JUMP)

        assertEquals(String(Character.toChars(0xF14DE)), buffer.get(4, 2).char)
    }

    @Test
    fun `attack range highlight shows an ATTACK_RANGE dot at the movement overlay cell`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.ATTACK_RANGE)

        assertEquals(".", buffer.get(4, 2).char)
        assertEquals(BoardRole.ATTACK_RANGE, buffer.get(4, 2).style.fg)
    }

    // ---- LOS / selected-target markers: relocated to the safe top-center cell (x+4, y+1) --------

    @Test
    fun `line of sight highlight shows a LINE_OF_SIGHT dot at the top-center marker cell`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.LINE_OF_SIGHT)

        assertEquals(".", buffer.get(4, 1).char)
        assertEquals(BoardRole.LINE_OF_SIGHT, buffer.get(4, 1).style.fg)
        // Not at the movement-overlay cell — LOS and movement overlays never collide.
        assertNotEquals(".", buffer.get(4, 2).char)
    }

    @Test
    fun `line of sight selected highlight shows the target icon at the top-center marker cell`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.LINE_OF_SIGHT_SELECTED)

        assertEquals(targetIcon(), buffer.get(4, 1).char)
        assertEquals(BoardRole.TARGET_SELECTED, buffer.get(4, 1).style.fg)
    }

    @Test
    fun `a unit rendered on the same hex cannot overwrite the top-center LOS marker`() {
        val buffer = ScreenBuffer(10, 6)
        val hex = Hex(HexCoordinates(0, 0))

        HexRenderer.render(Canvas.of(buffer), 0, 0, hex, HexHighlight.LINE_OF_SIGHT)
        // UnitRenderer only ever writes to rows 2 and 3 (id row, facing-arrow row) — never row 1,
        // where the LOS/target marker lives — so the marker must survive a unit drawn on top.
        UnitRenderer.render(Canvas.of(buffer), 0, 0, "A1", HexDirection.N, BoardRole.PLAYER_1)

        assertEquals(".", buffer.get(4, 1).char, "LOS marker must still be present after the unit renders")
        assertEquals(BoardRole.LINE_OF_SIGHT, buffer.get(4, 1).style.fg)
    }
}
