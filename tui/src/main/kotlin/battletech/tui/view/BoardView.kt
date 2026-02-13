package battletech.tui.view

import battletech.tui.hex.HexHighlight
import battletech.tui.hex.HexLayout
import battletech.tui.hex.HexRenderer
import battletech.tui.hex.UnitRenderer
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection

public class BoardView(
    private val gameState: GameState,
    private val viewport: Viewport,
    private val cursorPosition: HexCoordinates? = null,
    private val hexHighlights: Map<HexCoordinates, HexHighlight> = emptyMap(),
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        val (colRange, rowRange) = viewport.visibleHexRange()

        for (col in colRange) {
            for (row in rowRange) {
                val coords = HexCoordinates(col, row)
                val hex = gameState.map.hexes[coords] ?: continue

                val (screenX, screenY) = HexLayout.hexToScreen(col, row)
                val drawX = x + screenX - viewport.scrollCol * 8
                val drawY = y + screenY - viewport.scrollRow * 4

                val highlight = when {
                    coords == cursorPosition -> HexHighlight.CURSOR
                    coords in hexHighlights -> hexHighlights.getValue(coords)
                    else -> HexHighlight.NONE
                }

                HexRenderer.render(buffer, drawX, drawY, hex, highlight)

                val unit = gameState.units.find { it.position == coords }
                if (unit != null) {
                    UnitRenderer.render(
                        buffer, drawX, drawY,
                        unit.name.first(),
                        HexDirection.N,
                        Color.CYAN,
                    )
                }
            }
        }
    }
}
