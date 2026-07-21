package battletech.tui.hex

import battletech.tactical.model.Hex
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Terrain
import battletech.tactical.model.MovementMode
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public object HexRenderer {

    // Terrain icons (nf-md-tree_outline, nf-md-tree and another Nerd Fonts icons are above U+FFFF, need surrogate pairs)
    private fun terrainIcon(terrain: Terrain): String = when (terrain) {
        Terrain.CLEAR       -> ""
        Terrain.LIGHT_WOODS -> String(Character.toChars(0xF0E69))
        Terrain.HEAVY_WOODS -> String(Character.toChars(0xF0531))
        Terrain.WATER       -> String(Character.toChars(0xF078D))
    }

    // Elevation icons (nf-md-numeric_N_box_multiple_outline)
    private fun elevationIcon(elevation: Int): String = when (elevation) {
        1 -> String(Character.toChars(0xF03A5))
        2 -> String(Character.toChars(0xF03A8))
        3 -> String(Character.toChars(0xF03AB))
        4 -> String(Character.toChars(0xF03B2))
        5 -> String(Character.toChars(0xF03AF))
        6 -> String(Character.toChars(0xF03B4))
        7 -> String(Character.toChars(0xF03B7))
        8 -> String(Character.toChars(0xF03BA))
        9 -> String(Character.toChars(0xF03BD))
        else -> error("No elevation icon for elevation: $elevation")
    }

    // Facing arrow icons (same codepoints as UnitRenderer)
    private fun facingIcon(direction: HexDirection): String = when (direction) {
        HexDirection.N  -> String(Character.toChars(0xF09C7))
        HexDirection.NE -> String(Character.toChars(0xF09C5))
        HexDirection.SE -> String(Character.toChars(0xF09B9))
        HexDirection.S  -> String(Character.toChars(0xF09BF))
        HexDirection.SW -> String(Character.toChars(0xF09B7))
        HexDirection.NW -> String(Character.toChars(0xF09C3))
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
    public fun renderFacingArrows(buffer: ScreenBuffer, x: Int, y: Int, facings: Set<HexDirection>, color: Color, movementMode: MovementMode? = null) {
        if (facings.size == HexDirection.entries.size) {
            val icon = if (movementMode != null) movementModeIcon(movementMode) else "."
            renderOverlayChar(buffer, x, y, icon, color)
            return
        }
        for (direction in facings) {
            val (dx, dy) = facingPosition(direction)
            buffer.set(x + dx, y + dy, Cell(facingIcon(direction), Cell.Style(color, buffer.get(x + dx, y + dy).style.bg)))
        }
    }

    /**
     * Renders number labels (1-6) for available facings during facing selection.
     * Drawn in BRIGHT_YELLOW.
     */
    public fun renderFacingNumbers(buffer: ScreenBuffer, x: Int, y: Int, facings: Set<HexDirection>) {
        for (direction in facings) {
            val (dx, dy) = facingPosition(direction)
            val number = facingNumber(direction)
            buffer.set(x + dx, y + dy, Cell(number, Cell.Style(Color.BRIGHT_YELLOW, buffer.get(x + dx, y + dy).style.bg)))
        }
    }

    public fun render(buffer: ScreenBuffer, x: Int, y: Int, hex: Hex, highlight: HexHighlight, movementMode: MovementMode? = null) {
        val bg = terrainBackground(hex)
        val borderFg =
            if (highlight == HexHighlight.CURSOR) Color.BRIGHT_YELLOW
            else Color.GRAY

        renderBorder(buffer, x, y, borderFg, bg)
        renderContent(buffer, x, y, bg)
        renderTerrain(buffer, x, y, hex.terrain, bg)
        renderElevation(buffer, x, y, hex.elevation, bg)
        when (highlight) {
            HexHighlight.REACHABLE_WALK -> renderOverlayChar(buffer, x, y, ".", Color.WHITE)
            HexHighlight.REACHABLE_RUN -> renderOverlayChar(buffer, x, y, ".", Color.ORANGE)
            HexHighlight.REACHABLE_JUMP -> renderOverlayChar(buffer, x, y, ".", Color.CYAN)
            HexHighlight.ATTACK_RANGE -> renderOverlayChar(buffer, x, y, ".", Color.GRAY)
            HexHighlight.LINE_OF_SIGHT -> renderOverlayChar(buffer, x, y, ".", Color.YELLOW)
            HexHighlight.LINE_OF_SIGHT_SELECTED -> renderOverlayChar(buffer, x, y, targetIcon(), Color.RED)
            HexHighlight.PATH -> {
                val icon = if (movementMode != null) movementModeIcon(movementMode) else "*"
                renderOverlayChar(buffer, x, y, icon, Color.BRIGHT_YELLOW)
            }
            else -> Unit
        }
    }

    private fun renderOverlayChar(buffer: ScreenBuffer, x: Int, y: Int, char: String, color: Color) {
        buffer.set(x + 4, y + 2, Cell(char, Cell.Style(color, buffer.get(x + 4, y + 2).style.bg)))
    }

    // Soft background tint for a hex, driven by terrain. One cell has one background, so when a
    // hex has both a terrain type and elevation the precedence is WATER > woods > elevation: an
    // elevated forest stays green (its height still shows via the elevation number icon), and the
    // brown elevation tint appears only on clear elevated hills.
    private fun terrainBackground(hex: Hex): Color = when {
        hex.terrain == Terrain.WATER       -> if (hex.depth <= 1) Color.WATER_SHALLOW_BG else Color.WATER_DEEP_BG
        hex.terrain == Terrain.LIGHT_WOODS -> Color.WOODS_LIGHT_BG
        hex.terrain == Terrain.HEAVY_WOODS -> Color.WOODS_HEAVY_BG
        hex.elevation >= 1                 -> if (hex.elevation == 1) Color.ELEVATION_LOW_BG else Color.ELEVATION_HIGH_BG
        else                               -> Color.DEFAULT
    }

    private fun renderTerrain(buffer: ScreenBuffer, x: Int, y: Int, terrain: Terrain, bg: Color) {
        val color = when (terrain) {
            Terrain.CLEAR       -> return
            Terrain.LIGHT_WOODS -> Color.GREEN
            Terrain.HEAVY_WOODS -> Color.DARK_GREEN
            Terrain.WATER       -> Color.BLUE
        }
        buffer.set(x + 2, y + 1, Cell(terrainIcon(terrain), Cell.Style(color, bg)))
    }

    // The border glyphs carry the terrain `bg` too, so the whole hexagon reads as filled rather
    // than an outline floating on the terminal background. Adjacent hexes share their edge columns
    // (9-col glyph, 7-col stride, last-write-wins), so a shared edge column adopts the neighbour's
    // tint — negligible with these soft colors.
    private fun renderBorder(buffer: ScreenBuffer, x: Int, y: Int, fg: Color, bg: Color) {
        // Row 0: "  _____  " — the top edge. Its cells coincide with the hex-above's bottom edge
        // (row 4), so keep whatever background is already painted there rather than tinting: the
        // top edge belongs to the upper hex. Tinting it would paint a coloured band protruding
        // above the hexagon.
        for (i in 2..6) {
            buffer.set(x + i, y, Cell("_", Cell.Style(fg, buffer.get(x + i, y).style.bg)))
        }
        val style = Cell.Style(fg, bg)
        // Row 1: " /     \ "
        buffer.set(x + 1, y + 1, Cell("/", style))
        buffer.set(x + 7, y + 1, Cell("\\", style))
        // Row 2: "/       \"
        buffer.set(x, y + 2, Cell("/", style))
        buffer.set(x + 8, y + 2, Cell("\\", style))
        // Row 3: "\       /"
        buffer.set(x, y + 3, Cell("\\", style))
        buffer.set(x + 8, y + 3, Cell("/", style))
        // Row 4: " \_____/ "
        buffer.set(x + 1, y + 4, Cell("\\", style))
        for (i in 2..6) {
            buffer.set(x + i, y + 4, Cell("_", style))
        }
        buffer.set(x + 7, y + 4, Cell("/", style))
    }

    private fun renderContent(buffer: ScreenBuffer, x: Int, y: Int, bg: Color) {
        val style = Cell.Style(Color.DEFAULT, bg)
        val cell = Cell(" ", style)
        // Row 1 content (narrow): x+2..x+6
        for (i in 2..6) {
            buffer.set(x + i, y + 1, cell)
        }
        // Row 2 content (wide): x+1..x+7
        for (i in 1..7) {
            buffer.set(x + i, y + 2, cell)
        }
        // Row 3 content (wide): x+1..x+7
        for (i in 1..7) {
            buffer.set(x + i, y + 3, cell)
        }
    }

    private fun renderElevation(buffer: ScreenBuffer, x: Int, y: Int, elevation: Int, bg: Color) {
        if (elevation == 0) return
        val icon = elevationIcon(elevation)
        buffer.set(x + 6, y + 1, Cell(icon, Cell.Style(Color.WHITE, bg)))
    }

}
