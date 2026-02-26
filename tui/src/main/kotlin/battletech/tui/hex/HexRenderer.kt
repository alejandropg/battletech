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
    private val ELEVATION_ICONS = mapOf(
        1 to String(Character.toChars(0xF03A5)),
        2 to String(Character.toChars(0xF03A8)),
        3 to String(Character.toChars(0xF03AB)),
        4 to String(Character.toChars(0xF03B2)),
        5 to String(Character.toChars(0xF03AF)),
        6 to String(Character.toChars(0xF03B4)),
        7 to String(Character.toChars(0xF03B7)),
        8 to String(Character.toChars(0xF03BA)),
        9 to String(Character.toChars(0xF03BD)),
    )

    // Movement mode icons (nf-md-walk, nf-md-run-fast, nf-md-rocket-launch)
    private val MOVEMENT_MODE_ICONS: Map<MovementMode, String> = mapOf(
        MovementMode.WALK to String(Character.toChars(0xF0583)),
        MovementMode.RUN to String(Character.toChars(0xF046E)),
        MovementMode.JUMP to String(Character.toChars(0xF14DE)),
    )

    // Facing arrow icons (same codepoints as UnitRenderer)
    private val FACING_ICONS: Map<HexDirection, String> = mapOf(
        HexDirection.N  to String(Character.toChars(0xF09C7)),
        HexDirection.NE to String(Character.toChars(0xF09C5)),
        HexDirection.SE to String(Character.toChars(0xF09B9)),
        HexDirection.S  to String(Character.toChars(0xF09BF)),
        HexDirection.SW to String(Character.toChars(0xF09B7)),
        HexDirection.NW to String(Character.toChars(0xF09C3)),
    )

    // Arrow positions within hex: (col-offset, row-offset) relative to hex origin
    // Row 2: NW(+2), N(+4), NE(+6)
    // Row 3: SW(+2), S(+4), SE(+6)
    private val FACING_POSITIONS: Map<HexDirection, Pair<Int, Int>> = mapOf(
        HexDirection.N  to (4 to 2),
        HexDirection.NE to (6 to 2),
        HexDirection.SE to (6 to 3),
        HexDirection.S  to (4 to 3),
        HexDirection.SW to (2 to 3),
        HexDirection.NW to (2 to 2),
    )

    // Number mapping: 1=N, 2=NE, 3=SE, 4=S, 5=SW, 6=NW
    private val FACING_NUMBERS: Map<HexDirection, String> = mapOf(
        HexDirection.N  to "1",
        HexDirection.NE to "2",
        HexDirection.SE to "3",
        HexDirection.S  to "4",
        HexDirection.SW to "5",
        HexDirection.NW to "6",
    )

    /**
     * Renders facing arrows for reachable facings at a hex.
     * If all 6 facings are reachable, renders a dot at center (same as before).
     * Otherwise, renders individual arrows at their hex positions.
     */
    public fun renderFacingArrows(buffer: ScreenBuffer, x: Int, y: Int, facings: Set<HexDirection>, color: Color, movementMode: MovementMode? = null) {
        if (facings.size == HexDirection.entries.size) {
            val icon = if (movementMode != null) MOVEMENT_MODE_ICONS[movementMode]!! else "."
            renderOverlayChar(buffer, x, y, icon, color)
            return
        }
        for (direction in facings) {
            val (dx, dy) = FACING_POSITIONS[direction]!!
            buffer.set(x + dx, y + dy, Cell(FACING_ICONS[direction]!!, color))
        }
    }

    /**
     * Renders number labels (1-6) for available facings during facing selection.
     * Drawn in BRIGHT_YELLOW.
     */
    public fun renderFacingNumbers(buffer: ScreenBuffer, x: Int, y: Int, facings: Set<HexDirection>) {
        for (direction in facings) {
            val (dx, dy) = FACING_POSITIONS[direction]!!
            val number = FACING_NUMBERS[direction]!!
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
                val icon = if (movementMode != null) MOVEMENT_MODE_ICONS[movementMode]!! else "*"
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
        val icon = ELEVATION_ICONS[elevation] ?: return
        buffer.set(x + 6, y + 1, Cell(icon, Color.WHITE, bg))
    }

}
