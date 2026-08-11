package battletech.tui.view

import battletech.tactical.model.GameMap
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
import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color

/**
 * The tactical map's content: coordinate labels, every hex, highlight, and unit glyph, drawn at
 * its unscrolled screen position. Purely content — chrome and scrolling belong to the
 * [Bordered]/[Scrolled] that wraps this view (see its construction in `RunLoop.renderFrame`); the
 * only thing this view contributes to scrolling is marking the cursor hex as focus via
 * [Canvas.markFocus], so auto-follow keeps it visible.
 */
internal class BoardView(
    private val state: PlayerGameState,
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

    override fun render(canvas: Canvas) {
        val (mapWidth, mapHeight) = mapSize(state.map)
        renderCoordinates(canvas, mapWidth, mapHeight)

        for ((coords, hex) in state.map.hexes) {
            val (rawX, rawY) = HexLayout.hexToScreen(coords.col, coords.row)
            val x = MAP_ORIGIN_X + rawX
            val y = MAP_ORIGIN_Y + rawY

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

            HexRenderer.render(canvas, x, y, hex, highlight, movementMode)

            // Facing overlays (drawn after base render, over the reachability dot)
            when {
                coords == cursorPosition && facingSelectionFacings != null ->
                    HexRenderer.renderFacingNumbers(canvas, x, y, facingSelectionFacings)

                coords in reachableFacings && highlight != HexHighlight.PATH -> {
                    val facings = reachableFacings.getValue(coords)
                    val color = when {
                        coords == pathDestination -> Color.BRIGHT_YELLOW
                        baseHighlight == HexHighlight.REACHABLE_RUN -> Color.ORANGE
                        baseHighlight == HexHighlight.REACHABLE_JUMP -> Color.CYAN
                        else -> Color.WHITE
                    }
                    val mode = if (coords == pathDestination) movementMode else null
                    HexRenderer.renderFacingArrows(canvas, x, y, facings, color, mode)
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
                    canvas, x, y,
                    glyph,
                    unit.facing,
                    renderColor,
                    torsoFacing = torsoFacing,
                    isDestroyed = unit.isDestroyed,
                )
            }

            if (coords == cursorPosition) {
                canvas.markFocus(x, y, HexGeometry.HEX_WIDTH, HexGeometry.HEX_HEIGHT)
            }
        }
    }

    private fun renderCoordinates(canvas: Canvas, mapWidth: Int, mapHeight: Int) {
        val coordinates = state.map.hexes.keys
        val maxCol = coordinates.maxOfOrNull { it.col } ?: return
        val maxRow = coordinates.maxOfOrNull { it.row } ?: return

        for (col in 0..maxCol) {
            val label = coordinateLabel(col + 1)
            val centerX = MAP_ORIGIN_X + col * HexGeometry.COL_STRIDE + HexGeometry.HEX_WIDTH / 2
            val labelX = centerX - label.length / 2
            canvas.writeString(labelX, 0, label, COORDINATE_STYLE)
            canvas.writeString(
                labelX,
                MAP_ORIGIN_Y + mapHeight + BOTTOM_LABEL_GAP,
                label,
                COORDINATE_STYLE,
            )
        }

        for (row in 0..maxRow) {
            val label = coordinateLabel(row + 1)
            val labelY = MAP_ORIGIN_Y + row * HexGeometry.ROW_STRIDE + HexGeometry.HEX_HEIGHT / 2
            canvas.writeString(0, labelY, label, COORDINATE_STYLE)
            canvas.writeString(MAP_ORIGIN_X + mapWidth + SIDE_LABEL_GAP, labelY, label, COORDINATE_STYLE)
        }
    }

    private fun coordinateLabel(value: Int): String = value.toString().padStart(2, '0')

    internal companion object {
        /** Horizontal and vertical margins reserved for the map's 1-based coordinate labels. */
        private const val SIDE_LABEL_WIDTH: Int = 2
        private const val SIDE_LABEL_GAP: Int = 2

        internal const val MAP_ORIGIN_X: Int = SIDE_LABEL_WIDTH + SIDE_LABEL_GAP
        internal const val MAP_ORIGIN_Y: Int = 1
        internal const val BOTTOM_LABEL_GAP: Int = 1

        private val COORDINATE_STYLE: Cell.Style = Cell.Style(Color.DARK_GRAY)

        /** The unscrolled content size needed to draw [map] and its coordinate labels. */
        internal fun contentSize(map: GameMap): Pair<Int, Int> {
            val (mapWidth, mapHeight) = mapSize(map)
            val width = mapWidth + MAP_ORIGIN_X + SIDE_LABEL_GAP + SIDE_LABEL_WIDTH
            val height = mapHeight + MAP_ORIGIN_Y + BOTTOM_LABEL_GAP + 1
            return width to height
        }

        private fun mapSize(map: GameMap): Pair<Int, Int> {
            val coords = map.hexes.keys
            val maxCol = coords.maxOfOrNull { it.col } ?: 0
            val maxRow = coords.maxOfOrNull { it.row } ?: 0
            val width = maxCol * HexGeometry.COL_STRIDE + HexGeometry.HEX_WIDTH
            val height = maxRow * HexGeometry.ROW_STRIDE + HexGeometry.ODD_COL_ROW_OFFSET + HexGeometry.HEX_HEIGHT
            return width to height
        }
    }
}
