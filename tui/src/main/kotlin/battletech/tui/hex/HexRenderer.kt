package battletech.tui.hex

import battletech.tactical.model.Hex
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tui.hex.HexRenderer.renderOverlayChar
import battletech.tui.hex.HexRenderer.terrainFill
import battletech.tui.icon.depthIcon
import battletech.tui.icon.elevationIcon
import battletech.tui.icon.facingIcon
import battletech.tui.icon.facingNumber
import battletech.tui.icon.movementModeIcon
import battletech.tui.icon.targetIcon
import battletech.tui.icon.terrainIcon
import battletech.tui.screen.BoardRole
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole

public object HexRenderer {

    private fun elevationBadgeBg(elevation: Int): BoardRole = when (elevation) {
        1 -> BoardRole.ELEVATION_1_BADGE_BG
        2 -> BoardRole.ELEVATION_2_BADGE_BG
        else -> BoardRole.ELEVATION_HIGH_BADGE_BG
    }

    // Arrow positions within hex: (col-offset, row-offset) relative to hex origin
    // Row 2: NW(+2), N(+4), NE(+6)
    // Row 3: SW(+2), S(+4), SE(+6)
    private fun facingPosition(direction: HexDirection): Pair<Int, Int> = when (direction) {
        HexDirection.N  -> 4 to 2
        HexDirection.NE -> 6 to 2
        HexDirection.SE -> 6 to 3
        HexDirection.S  -> 4 to 3
        HexDirection.SW -> 2 to 3
        HexDirection.NW -> 2 to 2
    }

    /**
     * Renders facing arrows for reachable facings at a hex.
     * If all 6 facings are reachable, renders a dot at center (same as before).
     * Otherwise, renders individual arrows at their hex positions.
     */
    public fun renderFacingArrows(canvas: Canvas, x: Int, y: Int, facings: Set<HexDirection>, color: BoardRole, movementMode: MovementMode? = null) {
        if (facings.size == HexDirection.entries.size) {
            val icon = if (movementMode != null) movementModeIcon(movementMode) else "."
            renderOverlayChar(canvas, x, y, icon, color)
            return
        }
        for (direction in facings) {
            val (dx, dy) = facingPosition(direction)
            canvas.setFg(x + dx, y + dy, facingIcon(direction), color)
        }
    }

    /**
     * Renders number labels (1-6) for available facings during facing selection.
     * Drawn in [BoardRole.BOARD_ACTIVE] — same role as the cursor and the active path, since facing
     * selection is itself an active-cursor interaction.
     */
    public fun renderFacingNumbers(canvas: Canvas, x: Int, y: Int, facings: Set<HexDirection>) {
        for (direction in facings) {
            val (dx, dy) = facingPosition(direction)
            canvas.setFg(x + dx, y + dy, facingNumber(direction), BoardRole.BOARD_ACTIVE)
        }
    }

    public fun render(canvas: Canvas, x: Int, y: Int, hex: Hex, highlight: HexHighlight, movementMode: MovementMode? = null) {
        val bg = terrainFill(hex)
        val borderFg =
            if (highlight == HexHighlight.CURSOR) BoardRole.BOARD_ACTIVE
            else BoardRole.BOARD_BORDER

        renderBorder(canvas, x, y, borderFg, bg)
        renderContent(canvas, x, y, bg)
        renderTerrain(canvas, x, y, hex.terrain, bg)
        renderLevelBadge(canvas, x, y, hex.elevation, hex.depth)
        when (highlight) {
            HexHighlight.REACHABLE_WALK -> renderOverlayChar(canvas, x, y, ".", BoardRole.MOVE_WALK)
            HexHighlight.REACHABLE_RUN -> renderOverlayChar(canvas, x, y, ".", BoardRole.MOVE_RUN)
            HexHighlight.REACHABLE_JUMP -> renderOverlayChar(canvas, x, y, ".", BoardRole.MOVE_JUMP)
            HexHighlight.ATTACK_RANGE -> renderOverlayChar(canvas, x, y, ".", BoardRole.ATTACK_RANGE)
            HexHighlight.LINE_OF_SIGHT -> renderMarker(canvas, x, y, ".", BoardRole.LINE_OF_SIGHT)
            HexHighlight.LINE_OF_SIGHT_SELECTED -> renderMarker(canvas, x, y, targetIcon(), BoardRole.TARGET_SELECTED)
            HexHighlight.PATH -> {
                val icon = if (movementMode != null) movementModeIcon(movementMode) else "*"
                renderOverlayChar(canvas, x, y, icon, BoardRole.BOARD_ACTIVE)
            }
            else -> Unit
        }
    }

    /** Movement/range/path overlays: the safe bottom-center cell, clear of terrain icon and elevation badge. */
    private fun renderOverlayChar(canvas: Canvas, x: Int, y: Int, char: String, color: BoardRole) {
        canvas.setFg(x + 4, y + 2, char, color)
    }

    /**
     * Line-of-sight and selected-target markers: the safe top-center cell. Distinct from
     * [renderOverlayChar]'s row so a LOS/target marker and a movement overlay drawn on the same
     * hex can never collide — units, which occupy the lower rows (see `UnitRenderer`), cannot
     * overwrite either.
     */
    private fun renderMarker(canvas: Canvas, x: Int, y: Int, char: String, color: BoardRole) {
        canvas.setFg(x + 4, y + 1, char, color)
    }

    /**
     * The hex's whole-hex fill. A material terrain (woods, water, rough) always wins outright —
     * elevation never changes it, so an elevated forest stays green and its height shows only via
     * the elevation badge. A CLEAR hex has no material of its own, so elevation gets to fill the
     * whole hex instead of just the badge cell: an elevated clear hex reads as a hill, not a plain
     * with a small numbered sticker on it. WATER's shallow/deep split is the only terrain sub-case.
     */
    private fun terrainFill(hex: Hex): BoardRole = when (hex.terrain) {
        Terrain.LIGHT_WOODS -> BoardRole.TERRAIN_WOODS_LIGHT_BG
        Terrain.HEAVY_WOODS -> BoardRole.TERRAIN_WOODS_HEAVY_BG
        Terrain.WATER       -> if (hex.depth <= 1) BoardRole.TERRAIN_WATER_SHALLOW_BG else BoardRole.TERRAIN_WATER_DEEP_BG
        Terrain.ROUGH       -> BoardRole.TERRAIN_ROUGH_BG
        Terrain.CLEAR       -> if (hex.elevation > 0) elevationBadgeBg(hex.elevation) else BoardRole.TERRAIN_CLEAR_BG
    }

    // The border glyphs carry the terrain `bg` too, so the whole hexagon reads as filled rather
    // than an outline floating on the terminal background. Adjacent hexes share their edge columns
    // (9-col glyph, 7-col stride, last-write-wins), so a shared edge column adopts the neighbour's
    // tint — negligible with these soft colors.
    private fun renderBorder(canvas: Canvas, x: Int, y: Int, fg: BoardRole, bg: BoardRole) {
        // Row 0: "  _____  " — the top edge. Its cells coincide with the hex-above's bottom edge
        // (row 4), so keep whatever background is already painted there rather than tinting: the
        // top edge belongs to the upper hex. Tinting it would paint a coloured band protruding
        // above the hexagon.
        for (i in 2..6) {
            canvas.setFg(x + i, y, "_", fg)
        }
        val style = Cell.Style(fg, bg)
        // Row 1: " /     \ "
        canvas.set(x + 1, y + 1, Cell("/", style))
        canvas.set(x + 7, y + 1, Cell("\\", style))
        // Row 2: "/       \"
        canvas.set(x, y + 2, Cell("/", style))
        canvas.set(x + 8, y + 2, Cell("\\", style))
        // Row 3: "\       /"
        canvas.set(x, y + 3, Cell("\\", style))
        canvas.set(x + 8, y + 3, Cell("/", style))
        // Row 4: " \_____/ "
        canvas.set(x + 1, y + 4, Cell("\\", style))
        for (i in 2..6) {
            canvas.set(x + i, y + 4, Cell("_", style))
        }
        canvas.set(x + 7, y + 4, Cell("/", style))
    }

    private fun renderContent(canvas: Canvas, x: Int, y: Int, bg: BoardRole) {
        val style = Cell.Style(ChromeRole.DEFAULT, bg)
        val cell = Cell(style = style)
        // Row 1 content (narrow): x+2..x+6
        for (i in 2..6) {
            canvas.set(x + i, y + 1, cell)
        }
        // Row 2 content (wide): x+1..x+7
        for (i in 1..7) {
            canvas.set(x + i, y + 2, cell)
        }
        // Row 3 content (wide): x+1..x+7
        for (i in 1..7) {
            canvas.set(x + i, y + 3, cell)
        }
    }

    private fun renderTerrain(canvas: Canvas, x: Int, y: Int, terrain: Terrain, bg: BoardRole) {
        val color = when (terrain) {
            Terrain.CLEAR       -> return
            Terrain.LIGHT_WOODS -> BoardRole.TERRAIN_WOODS_LIGHT_ICON
            Terrain.HEAVY_WOODS -> BoardRole.TERRAIN_WOODS_HEAVY_ICON
            Terrain.WATER       -> BoardRole.TERRAIN_WATER_ICON
            Terrain.ROUGH       -> BoardRole.TERRAIN_ROUGH_ICON
        }
        canvas.set(x + 2, y + 1, Cell(terrainIcon(terrain), Cell.Style(color, bg)))
    }

    /**
     * The elevation/depth badge glyph. Elevation takes precedence when both values are non-zero.
     * An elevation glyph uses its tier's badge background. A depth glyph changes only the cell's
     * foreground, preserving the background painted by [terrainFill].
     * A hex with neither elevation nor depth renders no badge at all, leaving the terrain fill
     * intact either way.
     */
    private fun renderLevelBadge(canvas: Canvas, x: Int, y: Int, elevation: Int, depth: Int) {
        if (elevation != 0) {
            val style = Cell.Style(BoardRole.ELEVATION_BADGE_FG, elevationBadgeBg(elevation))
            canvas.set(x + 6, y + 1, Cell(elevationIcon(elevation), style))
        } else if (depth != 0) {
            canvas.setFg(x + 6, y + 1, depthIcon(depth), BoardRole.ELEVATION_BADGE_FG)
        }
    }

}
