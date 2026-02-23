package battletech.tui.hex

import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.model.Hex
import battletech.tactical.model.Terrain

public object HexRenderer {

    public fun render(buffer: ScreenBuffer, x: Int, y: Int, hex: Hex, highlight: HexHighlight) {
        val bg = contentBackground(hex.terrain, highlight)
        val borderFg = if (highlight == HexHighlight.CURSOR) Color.BRIGHT_YELLOW else Color.DEFAULT

        renderBorder(buffer, x, y, borderFg)
        renderContent(buffer, x, y, bg)
        renderTerrainIcon(buffer, x, y, hex.terrain, bg)
        renderElevation(buffer, x, y, hex.elevation, bg)
    }

    private fun contentBackground(terrain: Terrain, highlight: HexHighlight): Color = when (highlight) {
        HexHighlight.REACHABLE -> Color.CYAN
        HexHighlight.PATH -> Color.YELLOW
        HexHighlight.ATTACK_RANGE -> Color.RED
        HexHighlight.CURSOR, HexHighlight.NONE -> Color.DEFAULT
    }

    // nf-md-tree_outline, nf-md-tree and another Nerd Fonts icons are above U+FFFF, need surrogate pairs
    private val ICON_LIGHT_WOODS = String(Character.toChars(0xF0E69))
    private val ICON_HEAVY_WOODS = String(Character.toChars(0xF0531))
    private val ICON_WATER = String(Character.toChars(0xF078D))

    private fun renderTerrainIcon(buffer: ScreenBuffer, x: Int, y: Int, terrain: Terrain, bg: Color) {
        when (terrain) {
            Terrain.CLEAR -> Unit
            Terrain.LIGHT_WOODS ->
                buffer.set(x + 4, y + 2, Cell(ICON_LIGHT_WOODS, Color.GREEN, bg))
            Terrain.HEAVY_WOODS -> {
                buffer.set(x + 4, y + 2, Cell(ICON_HEAVY_WOODS, Color.DARK_GREEN, bg))
            }
            Terrain.WATER ->
                buffer.set(x + 4, y + 2, Cell(ICON_WATER, Color.BLUE, bg))
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
        if (elevation != 0) {
            val elevStr = elevation.toString()
            buffer.set(x + 6, y + 1, Cell(elevStr.last().toString(), Color.WHITE, bg))
        }
    }
}
