package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.query.PlayerGameState
import battletech.tui.hex.HexGeometry
import battletech.tui.hex.HexHighlight
import battletech.tui.hex.HexLayout
import battletech.tui.hex.HexRenderer
import battletech.tui.hex.UnitRenderer
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class BoardView(
    private val state: PlayerGameState,
    private val viewport: Viewport,
    private val cursorPosition: HexCoordinates? = null,
    private val hexHighlights: Map<HexCoordinates, HexHighlight> = emptyMap(),
    private val reachableFacings: Map<HexCoordinates, Set<HexDirection>> = emptyMap(),
    private val facingSelectionFacings: Set<HexDirection>? = null,
    private val pathDestination: HexCoordinates? = null,
    private val movementMode: MovementMode? = null,
    private val torsoFacings: Map<HexCoordinates, HexDirection> = emptyMap(),
    private val validTargetPositions: Set<HexCoordinates> = emptySet(),
    private val selectedTargetPosition: HexCoordinates? = null,
) : View {

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, "TACTICAL MAP")

        val contentX = x + 2
        val contentY = y + 2
        val (colRange, rowRange) = viewport.visibleHexRange()

        for (col in colRange) {
            for (row in rowRange) {
                val coords = HexCoordinates(col, row)
                val hex = state.map.hexes[coords] ?: continue

                val (screenX, screenY) = HexLayout.hexToScreen(col, row)
                val drawX = contentX + screenX - viewport.scrollCol * HexGeometry.COL_STRIDE
                val drawY = contentY + screenY - viewport.scrollRow * HexGeometry.ROW_STRIDE

                val baseHighlight = when {
                    coords == cursorPosition -> HexHighlight.CURSOR
                    coords in hexHighlights -> hexHighlights.getValue(coords)
                    else -> HexHighlight.NONE
                }

                val hasFacingOverlay = coords in reachableFacings || coords == cursorPosition
                val highlight =
                    if (hasFacingOverlay && baseHighlight in setOf(
                            HexHighlight.REACHABLE_WALK, HexHighlight.REACHABLE_RUN, HexHighlight.REACHABLE_JUMP,
                        )
                    ) HexHighlight.NONE
                    else baseHighlight

                HexRenderer.render(buffer, drawX, drawY, hex, highlight, movementMode)

                // Facing overlays (drawn after base render, over the reachability dot)
                when {
                    coords == cursorPosition && facingSelectionFacings != null ->
                        HexRenderer.renderFacingNumbers(buffer, drawX, drawY, facingSelectionFacings)

                    coords in reachableFacings && highlight != HexHighlight.PATH -> {
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

                val unit = state.units.at(coords)
                if (unit != null) {
                    val isValidTarget = coords in validTargetPositions
                    val isSelectedTarget = coords == selectedTargetPosition
                    val unitColor = when {
                        isSelectedTarget -> Color.RED
                        isValidTarget -> Color.YELLOW
                        else -> when (unit.owner) {
                            PlayerId.PLAYER_1 -> Color.BLUE
                            PlayerId.PLAYER_2 -> Color.MAGENTA
                        }
                    }
                    val effectiveTorso = torsoFacings[coords] ?: unit.torsoFacing
                    val torsoFacing = if (effectiveTorso != unit.facing) effectiveTorso else null
                    // Prone units (still active) are drawn with a lowercased id;
                    // otherwise the unit's id is used as-is.
                    val id = unit.id.value.take(2)
                    val glyph = if (unit.isProne) id.lowercase() else id
                    val renderColor = if (unit.isDestroyed) Color.GRAY else unitColor
                    UnitRenderer.render(
                        buffer, drawX, drawY,
                        glyph,
                        unit.facing,
                        renderColor,
                        torsoFacing = torsoFacing,
                        isDestroyed = unit.isDestroyed,
                    )
                }
            }
        }
    }
}
