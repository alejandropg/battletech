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
        renderElevation(buffer, x, y, hex.elevation, bg)
    }

    private fun contentBackground(terrain: Terrain, highlight: HexHighlight): Color = when (highlight) {
        HexHighlight.CURSOR -> terrainColor(terrain)
        HexHighlight.REACHABLE -> Color.CYAN
        HexHighlight.PATH -> Color.YELLOW
        HexHighlight.ATTACK_RANGE -> Color.RED
        HexHighlight.NONE -> terrainColor(terrain)
    }

    private fun terrainColor(terrain: Terrain): Color = when (terrain) {
        Terrain.CLEAR -> Color.DEFAULT
        Terrain.LIGHT_WOODS -> Color.GREEN
        Terrain.HEAVY_WOODS -> Color.DARK_GREEN
        Terrain.WATER -> Color.BLUE
    }

    private fun renderBorder(buffer: ScreenBuffer, x: Int, y: Int, fg: Color) {
        // Top: " _____ "
        for (i in 1..5) {
            buffer.set(x + i, y, Cell('_', fg))
        }
        // Upper sides: "/     \"
        buffer.set(x, y + 1, Cell('/', fg))
        buffer.set(x + 6, y + 1, Cell('\\', fg))
        // Lower sides: "\     /"
        buffer.set(x, y + 2, Cell('\\', fg))
        buffer.set(x + 6, y + 2, Cell('/', fg))
        // Bottom: " \___/ "
        buffer.set(x + 1, y + 3, Cell('\\', fg))
        for (i in 2..4) {
            buffer.set(x + i, y + 3, Cell('_', fg))
        }
        buffer.set(x + 5, y + 3, Cell('/', fg))
    }

    private fun renderContent(buffer: ScreenBuffer, x: Int, y: Int, bg: Color) {
        // Fill content area (inner cells of rows 1 and 2)
        for (i in 1..5) {
            buffer.set(x + i, y + 1, Cell(' ', Color.DEFAULT, bg))
            buffer.set(x + i, y + 2, Cell(' ', Color.DEFAULT, bg))
        }
    }

    private fun renderElevation(buffer: ScreenBuffer, x: Int, y: Int, elevation: Int, bg: Color) {
        if (elevation != 0) {
            val elevStr = elevation.toString()
            buffer.set(x + 5, y + 1, Cell(elevStr.last(), Color.WHITE, bg))
        }
    }
}
