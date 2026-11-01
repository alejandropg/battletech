package io.archinaut.battletech.tui.hex

import io.archinaut.battletech.tactical.model.Hex
import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.HexDirection
import io.archinaut.battletech.tactical.model.MovementMode
import io.archinaut.battletech.tactical.model.Terrain
import io.archinaut.battletech.tui.icon.targetIcon
import io.archinaut.battletech.tui.screen.BoardRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import io.archinaut.battletech.tui.view.drawFrame
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.color.StandardRole

internal class HexViewTest {

    @Test
    fun `renders clear hex border characters`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(
            """
            |  _____
            | /     \
            |/       \
            |\       /
            | \_____/
            """.trimMargin(),
            frame.text(),
        )
    }

    // ---- terrain fill roles (theme-independent — assert the role, not a resolved color) --------

    @Test
    fun `clear hex fills with TERRAIN_CLEAR_BG`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.TERRAIN_CLEAR_BG, frame.get(4, 3).bg)
    }

    @Test
    fun `light woods hex fills with TERRAIN_WOODS_LIGHT_BG and icons with TERRAIN_WOODS_LIGHT_ICON`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, frame.get(4, 3).bg)
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_ICON, frame.get(2, 1).fg)
    }

    @Test
    fun `heavy woods hex fills with TERRAIN_WOODS_HEAVY_BG and icons with TERRAIN_WOODS_HEAVY_ICON`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.HEAVY_WOODS)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.TERRAIN_WOODS_HEAVY_BG, frame.get(4, 3).bg)
        assertEquals(BoardRole.TERRAIN_WOODS_HEAVY_ICON, frame.get(2, 1).fg)
    }

    @Test
    fun `rough hex fills with TERRAIN_ROUGH_BG and icons with TERRAIN_ROUGH_ICON`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.ROUGH)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.TERRAIN_ROUGH_BG, frame.get(4, 3).bg)
        assertEquals(BoardRole.TERRAIN_ROUGH_ICON, frame.get(2, 1).fg)
    }

    @Test
    fun `shallow water (depth less than or equal to 1) fills with TERRAIN_WATER_SHALLOW_BG`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = 1)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.TERRAIN_WATER_SHALLOW_BG, frame.get(4, 3).bg)
        assertEquals(BoardRole.TERRAIN_WATER_ICON, frame.get(2, 1).fg)
    }

    @Test
    fun `deep water (depth 2) fills with TERRAIN_WATER_DEEP_BG`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = 2)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.TERRAIN_WATER_DEEP_BG, frame.get(4, 3).bg)
        // The water terrain icon color does not vary with depth — only the fill does.
        assertEquals(BoardRole.TERRAIN_WATER_ICON, frame.get(2, 1).fg)
    }

    @Test
    fun `very deep water (depth greater than or equal to 3) fills with TERRAIN_WATER_VERY_DEEP_BG`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = 3)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.TERRAIN_WATER_VERY_DEEP_BG, frame.get(4, 3).bg)
        assertEquals(BoardRole.TERRAIN_WATER_ICON, frame.get(2, 1).fg)
    }

    @Test
    fun `elevations use solid numeric badge glyphs`() {
        val codePoints = 0xF0F0F..0xF0F17

        for ((elevation, codePoint) in (1..9).zip(codePoints)) {
            val hex = Hex(HexCoordinates(0, 0), elevation = elevation)

            val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

            assertEquals(String(Character.toChars(codePoint)), frame.get(6, 1).symbol, "elevation=$elevation")
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
            val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, depth = depth)

            val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

            val badge = frame.get(6, 1)
            val expectedBg = when {
                depth == 1 -> BoardRole.TERRAIN_WATER_SHALLOW_BG
                depth == 2 -> BoardRole.TERRAIN_WATER_DEEP_BG
                else -> BoardRole.TERRAIN_WATER_VERY_DEEP_BG
            }
            assertEquals(String(Character.toChars(codePoint)), badge.symbol, "depth=$depth")
            assertEquals(expectedBg, badge.bg, "depth=$depth")
        }
    }

    @Test
    fun `elevation badge is rendered instead of depth badge when both are present`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.WATER, elevation = 2, depth = 3)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(String(Character.toChars(0xF0F10)), frame.get(6, 1).symbol)
    }

    @Test
    fun `zero elevation and depth render no badge — content stays the plain terrain fill`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.TERRAIN_CLEAR_BG, frame.get(6, 1).bg)
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
            val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }
            // Content cell away from the badge column (x+6) — the fill, not the badge.
            assertEquals(expectedFill, frame.get(4, 3).bg, "terrain=${hex.terrain} elevation=${hex.elevation}")
        }
    }

    @Test
    fun `a CLEAR hex with no elevation still fills with TERRAIN_CLEAR_BG`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.CLEAR)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.TERRAIN_CLEAR_BG, frame.get(4, 3).bg)
    }

    @Test
    fun `a CLEAR elevated hex fills the WHOLE hex with its elevation tier color, not just the badge cell`() {
        // With no material terrain of its own, an elevated clear hex reads as a hill: the
        // elevation color fills the entire hex (border, content, and badge cell alike), unlike a
        // material terrain, where elevation only tints the single badge cell.
        val cases = listOf(1 to BoardRole.ELEVATION_1_BADGE_BG, 2 to BoardRole.ELEVATION_2_BADGE_BG, 3 to BoardRole.ELEVATION_HIGH_BADGE_BG)
        for ((elevation, expectedFill) in cases) {
            val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.CLEAR, elevation = elevation)

            val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

            assertEquals(expectedFill, frame.get(4, 3).bg, "content cell, elevation=$elevation")
            assertEquals(expectedFill, frame.get(0, 2).bg, "border cell, elevation=$elevation")
            assertEquals(expectedFill, frame.get(6, 1).bg, "badge cell, elevation=$elevation")
        }
    }

    @Test
    fun `elevation 1 badge uses ELEVATION_1_BADGE_BG and ELEVATION_BADGE_FG at the badge cell`() {
        val hex = Hex(HexCoordinates(0, 0), elevation = 1)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        val badge = frame.get(6, 1)
        assertEquals(BoardRole.ELEVATION_1_BADGE_BG, badge.bg)
        assertEquals(BoardRole.ELEVATION_BADGE_FG, badge.fg)
    }

    @Test
    fun `elevation 2 badge uses ELEVATION_2_BADGE_BG`() {
        val hex = Hex(HexCoordinates(0, 0), elevation = 2)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.ELEVATION_2_BADGE_BG, frame.get(6, 1).bg)
    }

    @Test
    fun `elevation 3 and up badge uses ELEVATION_HIGH_BADGE_BG`() {
        for (elevation in listOf(3, 5, 9)) {
            val hex = Hex(HexCoordinates(0, 0), elevation = elevation)

            val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

            assertEquals(BoardRole.ELEVATION_HIGH_BADGE_BG, frame.get(6, 1).bg, "elevation=$elevation")
        }
    }

    @Test
    fun `the badge replaces the terrain fill only for its own cell`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS, elevation = 1)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.ELEVATION_1_BADGE_BG, frame.get(6, 1).bg, "badge cell")
        assertNotEquals(BoardRole.ELEVATION_1_BADGE_BG, frame.get(4, 3).bg, "content cell keeps the terrain fill")
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, frame.get(4, 3).bg)
    }

    // ---- background inheritance across the border / adjacent hexes -----------------------------

    @Test
    fun `border glyphs carry the terrain background tint`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals("/", frame.get(0, 2).symbol) // left border glyph
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, frame.get(0, 2).bg)
    }

    @Test
    fun `hex does not tint its own top edge but tints its bottom edge`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        // Top edge (row 0) belongs to the hex above — this hex leaves its background unset.
        assertEquals(StandardRole.DEFAULT, frame.get(2, 0).bg)
        // Bottom edge (row 4) is this hex's own — tinted.
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, frame.get(2, 4).bg)
    }

    @Test
    fun `top edge inherits the background of the hex above`() {
        val woods = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)
        val clear = Hex(HexCoordinates(0, 1))

        // The clear hex one ROW_STRIDE down shares its top edge (row 0 at y=4) with the woods
        // hex's bottom edge (row 4 at y=4). Drawn after, it must not overwrite the woods tint.
        val frame = drawFrame(10, 10) {
            it.renderWidget(HexView(woods, HexHighlight.NONE), Rect(0, 0, HexGeometry.HEX_WIDTH, HexGeometry.HEX_HEIGHT))
            it.renderWidget(HexView(clear, HexHighlight.NONE), Rect(0, HexGeometry.ROW_STRIDE, HexGeometry.HEX_WIDTH, HexGeometry.HEX_HEIGHT))
        }

        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, frame.get(2, HexGeometry.ROW_STRIDE).bg)
    }

    @Test
    fun `reachability dot inherits the terrain background`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.REACHABLE_WALK), it.area) }

        assertEquals(".", frame.get(4, 2).symbol)
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, frame.get(4, 2).bg)
    }

    @Test
    fun `facing arrows inherit the terrain background`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        val frame = drawFrame(9, 5) {
            it.renderWidget(HexView(hex, HexHighlight.NONE), it.area)
            it.renderWidget(FacingArrowsView(setOf(HexDirection.N), BoardRole.MOVE_WALK), it.area)
        }

        // N arrow sits at (x+4, y+2)
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, frame.get(4, 2).bg)
    }

    @Test
    fun `facing keys inherit the terrain background and render in BOARD_ACTIVE`() {
        val hex = Hex(HexCoordinates(0, 0), terrain = Terrain.LIGHT_WOODS)

        val frame = drawFrame(9, 5) {
            it.renderWidget(HexView(hex, HexHighlight.NONE), it.area)
            it.renderWidget(FacingKeysView(setOf(HexDirection.N)), it.area)
        }

        assertEquals("w", frame.get(4, 2).symbol)
        assertEquals(BoardRole.BOARD_ACTIVE, frame.get(4, 2).fg)
        assertEquals(BoardRole.TERRAIN_WOODS_LIGHT_BG, frame.get(4, 2).bg)
    }

    // ---- cursor / highlight colors --------------------------------------------------------------

    @Test
    fun `cursor highlight changes border color to BOARD_ACTIVE`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.CURSOR), it.area) }

        assertEquals(BoardRole.BOARD_ACTIVE, frame.get(1, 1).fg) // '/' border
        assertEquals(BoardRole.BOARD_ACTIVE, frame.get(7, 1).fg) // '\' border
        assertEquals(BoardRole.BOARD_ACTIVE, frame.get(2, 0).fg) // '_' top
    }

    @Test
    fun `ordinary hex border uses BOARD_BORDER`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.NONE), it.area) }

        assertEquals(BoardRole.BOARD_BORDER, frame.get(1, 1).fg)
    }

    @Test
    fun `reachable walk highlight shows a MOVE_WALK dot at the overlay cell`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.REACHABLE_WALK), it.area) }

        assertEquals(".", frame.get(4, 2).symbol)
        assertEquals(BoardRole.MOVE_WALK, frame.get(4, 2).fg)
    }

    @Test
    fun `reachable run highlight shows a MOVE_RUN dot`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.REACHABLE_RUN), it.area) }

        assertEquals(".", frame.get(4, 2).symbol)
        assertEquals(BoardRole.MOVE_RUN, frame.get(4, 2).fg)
    }

    @Test
    fun `reachable jump highlight shows a MOVE_JUMP dot`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.REACHABLE_JUMP), it.area) }

        assertEquals(".", frame.get(4, 2).symbol)
        assertEquals(BoardRole.MOVE_JUMP, frame.get(4, 2).fg)
    }

    @Test
    fun `path highlight without mode shows star at the movement overlay cell`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.PATH), it.area) }

        assertEquals("*", frame.get(4, 2).symbol)
        assertEquals(BoardRole.BOARD_ACTIVE, frame.get(4, 2).fg)
    }

    @Test
    fun `path highlight with WALK mode shows walk icon`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.PATH, MovementMode.WALK), it.area) }

        assertEquals(String(Character.toChars(0xF0583)), frame.get(4, 2).symbol)
    }

    @Test
    fun `path highlight with RUN mode shows run icon`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.PATH, MovementMode.RUN), it.area) }

        assertEquals(String(Character.toChars(0xF046E)), frame.get(4, 2).symbol)
    }

    @Test
    fun `path highlight with JUMP mode shows jump icon`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.PATH, MovementMode.JUMP), it.area) }

        assertEquals(String(Character.toChars(0xF14DE)), frame.get(4, 2).symbol)
    }

    @Test
    fun `attack range highlight shows an ATTACK_RANGE dot at the movement overlay cell`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.ATTACK_RANGE), it.area) }

        assertEquals(".", frame.get(4, 2).symbol)
        assertEquals(BoardRole.ATTACK_RANGE, frame.get(4, 2).fg)
    }

    // ---- LOS / selected-target markers: relocated to the safe top-center cell (x+4, y+1) --------

    @Test
    fun `line of sight highlight shows a LINE_OF_SIGHT dot at the top-center marker cell`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.LINE_OF_SIGHT), it.area) }

        assertEquals(".", frame.get(4, 1).symbol)
        assertEquals(BoardRole.LINE_OF_SIGHT, frame.get(4, 1).fg)
        // Not at the movement-overlay cell — LOS and movement overlays never collide.
        assertNotEquals(".", frame.get(4, 2).symbol)
    }

    @Test
    fun `line of sight selected highlight shows the target icon at the top-center marker cell`() {
        val hex = Hex(HexCoordinates(0, 0))

        val frame = drawFrame(9, 5) { it.renderWidget(HexView(hex, HexHighlight.LINE_OF_SIGHT_SELECTED), it.area) }

        assertEquals(targetIcon(), frame.get(4, 1).symbol)
        assertEquals(BoardRole.TARGET_SELECTED, frame.get(4, 1).fg)
    }

    @Test
    fun `a unit rendered on the same hex cannot overwrite the top-center LOS marker`() {
        val hex = Hex(HexCoordinates(0, 0))

        // UnitView only ever writes to rows 2 and 3 (id row, facing-arrow row) — never row 1,
        // where the LOS/target marker lives — so the marker must survive a unit drawn on top.
        val frame = drawFrame(9, 5) {
            it.renderWidget(HexView(hex, HexHighlight.LINE_OF_SIGHT), it.area)
            it.renderWidget(UnitView("A1", HexDirection.N, BoardRole.PLAYER_1), it.area)
        }

        assertEquals(".", frame.get(4, 1).symbol, "LOS marker must still be present after the unit renders")
        assertEquals(BoardRole.LINE_OF_SIGHT, frame.get(4, 1).fg)
    }
}
