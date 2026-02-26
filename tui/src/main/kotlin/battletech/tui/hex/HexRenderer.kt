package battletech.tui.hex

import battletech.tactical.model.Hex
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public object HexRenderer {

    // nf-md-tree_outline, nf-md-tree and another Nerd Fonts icons are above U+FFFF, need surrogate pairs
    private val ICON_LIGHT_WOODS = String(Character.toChars(0xF0E69))
    private val ICON_HEAVY_WOODS = String(Character.toChars(0xF0531))
    private val ICON_WATER = String(Character.toChars(0xF078D))

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

    // Movement mode icons (nf-md-walk, nf-md-run-fast, nf-md-rocket-launch)
    private fun movementModeIcon(mode: MovementMode): String = when (mode) {
        MovementMode.WALK -> String(Character.toChars(0xF0583))
        MovementMode.RUN  -> String(Character.toChars(0xF046E))
        MovementMode.JUMP -> String(Character.toChars(0xF14DE))
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
            buffer.set(x + dx, y + dy, Cell(facingIcon(direction), color))
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
            buffer.set(x + dx, y + dy, Cell(number, Color.BRIGHT_YELLOW))
        }
    }

    public fun render(buffer: ScreenBuffer, x: Int, y: Int, hex: Hex, highlight: HexHighlight, movementMode: MovementMode? = null) {
        val bg = contentBackground(highlight)
        val borderFg =
            if (highlight == HexHighlight.CURSOR || highlight == HexHighlight.PATH_CURSOR) Color.BRIGHT_YELLOW
            else Color.DEFAULT

        renderBorder(buffer, x, y, borderFg)
        renderContent(buffer, x, y, bg)
        renderTerrainIcon(buffer, x, y, hex.terrain, bg)
        renderElevation(buffer, x, y, hex.elevation, bg)
        when (highlight) {
            HexHighlight.REACHABLE_WALK -> renderOverlayChar(buffer, x, y, ".", Color.WHITE)
            HexHighlight.REACHABLE_RUN -> renderOverlayChar(buffer, x, y, ".", Color.ORANGE)
            HexHighlight.REACHABLE_JUMP -> renderOverlayChar(buffer, x, y, ".", Color.CYAN)
            HexHighlight.ATTACK_RANGE -> renderOverlayChar(buffer, x, y, ".", Color.WHITE)
            HexHighlight.PATH, HexHighlight.PATH_CURSOR -> {
                val icon = if (movementMode != null) movementModeIcon(movementMode) else "*"
                renderOverlayChar(buffer, x, y, icon, Color.BRIGHT_YELLOW)
            }
            else -> Unit
        }
    }

    private fun renderOverlayChar(buffer: ScreenBuffer, x: Int, y: Int, char: String, color: Color) {
        buffer.set(x + 4, y + 2, Cell(char, color))
    }

    private fun contentBackground(highlight: HexHighlight): Color = Color.DEFAULT

    private fun renderTerrainIcon(buffer: ScreenBuffer, x: Int, y: Int, terrain: Terrain, bg: Color) {
        when (terrain) {
            Terrain.CLEAR -> Unit
            Terrain.LIGHT_WOODS ->
                buffer.set(x + 2, y + 1, Cell(ICON_LIGHT_WOODS, Color.GREEN, bg))

            Terrain.HEAVY_WOODS ->
                buffer.set(x + 2, y + 1, Cell(ICON_HEAVY_WOODS, Color.DARK_GREEN, bg))

            Terrain.WATER ->
                buffer.set(x + 2, y + 1, Cell(ICON_WATER, Color.BLUE, bg))
        }
    }

    private fun renderBorder(buffer: ScreenBuffer, x: Int, y: Int, fg: Color) {
        // Row 0: "  _____  "
        for (i in 2..6) {
            buffer.set(x + i, y, Cell("_", fg))
        }
        // Row 1: " /     \ "
        buffer.set(x + 1, y + 1, Cell("/", fg))
        buffer.set(x + 7, y + 1, Cell("\\", fg))
        // Row 2: "/       \"
        buffer.set(x, y + 2, Cell("/", fg))
        buffer.set(x + 8, y + 2, Cell("\\", fg))
        // Row 3: "\       /"
        buffer.set(x, y + 3, Cell("\\", fg))
        buffer.set(x + 8, y + 3, Cell("/", fg))
        // Row 4: " \_____/ "
        buffer.set(x + 1, y + 4, Cell("\\", fg))
        for (i in 2..6) {
            buffer.set(x + i, y + 4, Cell("_", fg))
        }
        buffer.set(x + 7, y + 4, Cell("/", fg))
    }

    private fun renderContent(buffer: ScreenBuffer, x: Int, y: Int, bg: Color) {
        // Row 1 content (narrow): x+2..x+6
        for (i in 2..6) {
            buffer.set(x + i, y + 1, Cell(" ", Color.DEFAULT, bg))
        }
        // Row 2 content (wide): x+1..x+7
        for (i in 1..7) {
            buffer.set(x + i, y + 2, Cell(" ", Color.DEFAULT, bg))
        }
        // Row 3 content (wide): x+1..x+7
        for (i in 1..7) {
            buffer.set(x + i, y + 3, Cell(" ", Color.DEFAULT, bg))
        }
    }

    private fun renderElevation(buffer: ScreenBuffer, x: Int, y: Int, elevation: Int, bg: Color) {
        if (elevation == 0) return
        val icon = elevationIcon(elevation)
        buffer.set(x + 6, y + 1, Cell(icon, Color.WHITE, bg))
    }

}
