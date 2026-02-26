package battletech.tui.view

import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tui.hex.HexGeometry
import battletech.tui.hex.HexHighlight
import battletech.tui.hex.HexLayout
import battletech.tui.hex.HexRenderer
import battletech.tui.hex.UnitRenderer
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class BoardView(
    private val gameState: GameState,
    private val viewport: Viewport,
    private val cursorPosition: HexCoordinates? = null,
    private val hexHighlights: Map<HexCoordinates, HexHighlight> = emptyMap(),
    private val reachableFacings: Map<HexCoordinates, Set<HexDirection>> = emptyMap(),
    private val facingSelectionHex: HexCoordinates? = null,
    private val facingSelectionFacings: Set<HexDirection>? = null,
    private val pathDestination: HexCoordinates? = null,
    private val movementMode: MovementMode? = null,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "TACTICAL MAP")

        val contentX = x + 2
        val contentY = y + 2
        val (colRange, rowRange) = viewport.visibleHexRange()

        for (col in colRange) {
            for (row in rowRange) {
                val coords = HexCoordinates(col, row)
                val hex = gameState.map.hexes[coords] ?: continue

                val (screenX, screenY) = HexLayout.hexToScreen(col, row)
                val drawX = contentX + screenX - viewport.scrollCol * HexGeometry.COL_STRIDE
                val drawY = contentY + screenY - viewport.scrollRow * HexGeometry.ROW_STRIDE

                val baseHighlight = when {
                    coords == cursorPosition && hexHighlights[coords] == HexHighlight.PATH -> HexHighlight.PATH_CURSOR
                    coords == cursorPosition -> HexHighlight.CURSOR
                    coords in hexHighlights -> hexHighlights.getValue(coords)
                    else -> HexHighlight.NONE
                }

                val hasFacingOverlay = coords in reachableFacings || coords == facingSelectionHex
                val highlight = if (hasFacingOverlay && baseHighlight in setOf(
                    HexHighlight.REACHABLE_WALK, HexHighlight.REACHABLE_RUN, HexHighlight.REACHABLE_JUMP,
                )) HexHighlight.NONE else baseHighlight

                HexRenderer.render(buffer, drawX, drawY, hex, highlight, movementMode)

                // Facing overlays (drawn after base render, over the reachability dot)
                when {
                    coords == facingSelectionHex && facingSelectionFacings != null ->
                        HexRenderer.renderFacingNumbers(buffer, drawX, drawY, facingSelectionFacings)
                    coords in reachableFacings && highlight !in setOf(HexHighlight.PATH, HexHighlight.PATH_CURSOR) -> {
                        val facings = reachableFacings.getValue(coords)
                        val color = when {
                            coords == pathDestination -> Color.BRIGHT_YELLOW
                            baseHighlight == HexHighlight.REACHABLE_RUN -> Color.ORANGE
                            baseHighlight == HexHighlight.REACHABLE_JUMP -> Color.CYAN
                            else -> Color.WHITE
                        }
                        val mode = if (coords == pathDestination) movementMode else null
                        HexRenderer.renderFacingArrows(buffer, drawX, drawY, facings, color, mode)
                    }
                }

                val unit = gameState.unitAt(coords)
                if (unit != null) {
                    UnitRenderer.render(
                        buffer, drawX, drawY,
                        unit.name.first(),
                        unit.facing,
                        Color.CYAN,
                    )
                }
            }
        }
    }
}
