package battletech.tui.hex

import battletech.tactical.model.Hex
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Terrain
import battletech.tactical.model.MovementMode
import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color

// Terrain icons (nf-md-tree_outline, nf-md-tree and another Nerd Fonts icons are above U+FFFF, need surrogate pairs)
private val NF_MD_TREE_OUTLINE = String(Character.toChars(0xF0E69))
private val NF_MD_PINE_TREE = String(Character.toChars(0xF0531))
private val NF_MD_WAVES = String(Character.toChars(0xF078D))
private val NF_MD_GRAIN = String(Character.toChars(0xF0D7C))

// Elevation icons (nf-md-numeric_N_box_multiple_outline)
private val NF_MD_NUMERIC_1_BOX_MULTIPLE_OUTLINE = String(Character.toChars(0xF03A5))
private val NF_MD_NUMERIC_2_BOX_MULTIPLE_OUTLINE = String(Character.toChars(0xF03A8))
private val NF_MD_NUMERIC_3_BOX_MULTIPLE_OUTLINE = String(Character.toChars(0xF03AB))
private val NF_MD_NUMERIC_4_BOX_MULTIPLE_OUTLINE = String(Character.toChars(0xF03B2))
private val NF_MD_NUMERIC_5_BOX_MULTIPLE_OUTLINE = String(Character.toChars(0xF03AF))
private val NF_MD_NUMERIC_6_BOX_MULTIPLE_OUTLINE = String(Character.toChars(0xF03B4))
private val NF_MD_NUMERIC_7_BOX_MULTIPLE_OUTLINE = String(Character.toChars(0xF03B7))
private val NF_MD_NUMERIC_8_BOX_MULTIPLE_OUTLINE = String(Character.toChars(0xF03BA))
private val NF_MD_NUMERIC_9_BOX_MULTIPLE_OUTLINE = String(Character.toChars(0xF03BD))

// Facing arrow icons (same codepoints as UnitRenderer)
private val NF_MD_ARROW_UP_THIN_N = String(Character.toChars(0xF09C7))
private val NF_MD_ARROW_UP_THIN_NE = String(Character.toChars(0xF09C5))
private val NF_MD_ARROW_UP_THIN_SE = String(Character.toChars(0xF09B9))
private val NF_MD_ARROW_UP_THIN_S = String(Character.toChars(0xF09BF))
private val NF_MD_ARROW_UP_THIN_SW = String(Character.toChars(0xF09B7))
private val NF_MD_ARROW_UP_THIN_NW = String(Character.toChars(0xF09C3))

public object HexRenderer {

    private fun terrainIcon(terrain: Terrain): String = when (terrain) {
        Terrain.CLEAR       -> ""
        Terrain.LIGHT_WOODS -> NF_MD_TREE_OUTLINE
        Terrain.HEAVY_WOODS -> NF_MD_PINE_TREE
        Terrain.WATER       -> NF_MD_WAVES
        Terrain.ROUGH       -> NF_MD_GRAIN
    }

    private fun elevationIcon(elevation: Int): String = when (elevation) {
        1 -> NF_MD_NUMERIC_1_BOX_MULTIPLE_OUTLINE
        2 -> NF_MD_NUMERIC_2_BOX_MULTIPLE_OUTLINE
        3 -> NF_MD_NUMERIC_3_BOX_MULTIPLE_OUTLINE
        4 -> NF_MD_NUMERIC_4_BOX_MULTIPLE_OUTLINE
        5 -> NF_MD_NUMERIC_5_BOX_MULTIPLE_OUTLINE
        6 -> NF_MD_NUMERIC_6_BOX_MULTIPLE_OUTLINE
        7 -> NF_MD_NUMERIC_7_BOX_MULTIPLE_OUTLINE
        8 -> NF_MD_NUMERIC_8_BOX_MULTIPLE_OUTLINE
        9 -> NF_MD_NUMERIC_9_BOX_MULTIPLE_OUTLINE
        else -> error("No elevation icon for elevation: $elevation")
    }

    private fun facingIcon(direction: HexDirection): String = when (direction) {
        HexDirection.N  -> NF_MD_ARROW_UP_THIN_N
        HexDirection.NE -> NF_MD_ARROW_UP_THIN_NE
        HexDirection.SE -> NF_MD_ARROW_UP_THIN_SE
        HexDirection.S  -> NF_MD_ARROW_UP_THIN_S
        HexDirection.SW -> NF_MD_ARROW_UP_THIN_SW
        HexDirection.NW -> NF_MD_ARROW_UP_THIN_NW
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

    // Number mapping: 1=N, 2=NE, 3=SE, 4=S, 5=SW, 6=NW
    private fun facingNumber(direction: HexDirection): String = when (direction) {
        HexDirection.N  -> "1"
        HexDirection.NE -> "2"
        HexDirection.SE -> "3"
        HexDirection.S  -> "4"
        HexDirection.SW -> "5"
        HexDirection.NW -> "6"
    }

    /**
     * Renders facing arrows for reachable facings at a hex.
     * If all 6 facings are reachable, renders a dot at center (same as before).
     * Otherwise, renders individual arrows at their hex positions.
     */
    public fun renderFacingArrows(canvas: Canvas, x: Int, y: Int, facings: Set<HexDirection>, color: Color, movementMode: MovementMode? = null) {
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
     * Drawn in BRIGHT_YELLOW.
     */
    public fun renderFacingNumbers(canvas: Canvas, x: Int, y: Int, facings: Set<HexDirection>) {
        for (direction in facings) {
            val (dx, dy) = facingPosition(direction)
            canvas.setFg(x + dx, y + dy, facingNumber(direction), Color.BRIGHT_YELLOW)
        }
    }

    public fun render(canvas: Canvas, x: Int, y: Int, hex: Hex, highlight: HexHighlight, movementMode: MovementMode? = null) {
        val bg = terrainBackground(hex)
        val borderFg =
            if (highlight == HexHighlight.CURSOR) Color.BRIGHT_YELLOW
            else Color.GRAY

        renderBorder(canvas, x, y, borderFg, bg)
        renderContent(canvas, x, y, bg)
        renderTerrain(canvas, x, y, hex.terrain, bg)
        renderElevation(canvas, x, y, hex.elevation, bg)
        when (highlight) {
            HexHighlight.REACHABLE_WALK -> renderOverlayChar(canvas, x, y, ".", Color.WHITE)
            HexHighlight.REACHABLE_RUN -> renderOverlayChar(canvas, x, y, ".", Color.ORANGE)
            HexHighlight.REACHABLE_JUMP -> renderOverlayChar(canvas, x, y, ".", Color.CYAN)
            HexHighlight.ATTACK_RANGE -> renderOverlayChar(canvas, x, y, ".", Color.GRAY)
            HexHighlight.LINE_OF_SIGHT -> renderOverlayChar(canvas, x, y, ".", Color.YELLOW)
            HexHighlight.LINE_OF_SIGHT_SELECTED -> renderOverlayChar(canvas, x, y, targetIcon(), Color.RED)
            HexHighlight.PATH -> {
                val icon = if (movementMode != null) movementModeIcon(movementMode) else "*"
                renderOverlayChar(canvas, x, y, icon, Color.BRIGHT_YELLOW)
            }
            else -> Unit
        }
    }

    private fun renderOverlayChar(canvas: Canvas, x: Int, y: Int, char: String, color: Color) {
        canvas.setFg(x + 4, y + 2, char, color)
    }

    // Soft background tint for a hex, driven by terrain. One cell has one background, so when a
    // hex has both a terrain type and elevation the precedence is WATER > woods > elevation: an
    // elevated forest stays green (its height still shows via the elevation number icon), and the
    // brown elevation tint appears only on clear elevated hills.
    private fun terrainBackground(hex: Hex): Color = when {
        hex.terrain == Terrain.WATER       -> if (hex.depth <= 1) Color.WATER_SHALLOW_BG else Color.WATER_DEEP_BG
        hex.terrain == Terrain.LIGHT_WOODS -> Color.WOODS_LIGHT_BG
        hex.terrain == Terrain.HEAVY_WOODS -> Color.WOODS_HEAVY_BG
        hex.terrain == Terrain.ROUGH       -> Color.ROUGH_BG
        hex.elevation == 1                 -> Color.ELEVATION_1_BG
        hex.elevation == 2                 -> Color.ELEVATION_2_BG
        hex.elevation >= 3                 -> Color.ELEVATION_HIGH_BG
        else                               -> Color.DEFAULT
    }

    private fun renderTerrain(canvas: Canvas, x: Int, y: Int, terrain: Terrain, bg: Color) {
        val color = when (terrain) {
            Terrain.CLEAR       -> return
            Terrain.LIGHT_WOODS -> Color.GREEN
            Terrain.HEAVY_WOODS -> Color.DARK_GREEN
            Terrain.WATER       -> Color.BLUE
            Terrain.ROUGH       -> Color.BROWN
        }
        canvas.set(x + 2, y + 1, Cell(terrainIcon(terrain), Cell.Style(color, bg)))
    }

    // The border glyphs carry the terrain `bg` too, so the whole hexagon reads as filled rather
    // than an outline floating on the terminal background. Adjacent hexes share their edge columns
    // (9-col glyph, 7-col stride, last-write-wins), so a shared edge column adopts the neighbour's
    // tint — negligible with these soft colors.
    private fun renderBorder(canvas: Canvas, x: Int, y: Int, fg: Color, bg: Color) {
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

    private fun renderContent(canvas: Canvas, x: Int, y: Int, bg: Color) {
        val style = Cell.Style(Color.DEFAULT, bg)
        val cell = Cell(" ", style)
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

    private fun renderElevation(canvas: Canvas, x: Int, y: Int, elevation: Int, bg: Color) {
        if (elevation == 0) return
        val icon = elevationIcon(elevation)
        canvas.set(x + 6, y + 1, Cell(icon, Cell.Style(Color.WHITE, bg)))
    }

}
