package battletech.tui.view

import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.query.PlayerGameState
import battletech.tui.hex.HexGeometry
import battletech.tui.hex.HexHighlight
import battletech.tui.hex.HexLayout
import battletech.tui.hex.HexRenderer
import battletech.tui.hex.UnitRenderer
import battletech.tui.screen.BoardRole
import battletech.tactical.unit.UnitRoster
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.view.Bordered
import tenter.view.View
import tenter.view.Viewport

/**
 * The tactical map's content: coordinate labels, every hex, highlight, and unit glyph, drawn at
 * its unscrolled screen position. Purely content — chrome and scrolling belong to the
 * [Bordered]/[Viewport] that wraps this view (see its construction in `RunLoop.renderFrame`); the
 * only thing this view contributes to scrolling is marking the cursor hex for reveal via
 * [Canvas.markReveal], so auto-follow keeps it visible.
 */
internal class BoardView(
    private val state: PlayerGameState,
    private val cursorPosition: HexCoordinates? = null,
    private val hexHighlights: Map<HexCoordinates, HexHighlight> = emptyMap(),
    private val reachableFacings: Map<HexCoordinates, Set<HexDirection>> = emptyMap(),
    private val facingSelectionFacings: Set<HexDirection>? = null,
    private val pathDestination: HexCoordinates? = null,
    private val movementMode: MovementMode? = null,
    private val draftTorsoFacings: Map<HexCoordinates, HexDirection> = emptyMap(),
    private val validTargetPositions: Set<HexCoordinates> = emptySet(),
    private val selectedTargetPosition: HexCoordinates? = null,
) : View {

    override fun draw(canvas: Canvas) {
        val (mapWidth, mapHeight) = mapSize(state.map)
        renderCoordinates(canvas, mapWidth, mapHeight)

        for ((coords, hex) in state.map.hexes) {
            val (rawX, rawY) = HexLayout.hexToScreen(coords.col, coords.row)
            val x = MAP_ORIGIN_X + rawX
            val y = MAP_ORIGIN_Y + rawY

            val baseHighlight = when (coords) {
                cursorPosition -> HexHighlight.CURSOR
                in hexHighlights -> hexHighlights.getValue(coords)
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
            when (coords) {
                cursorPosition if facingSelectionFacings != null ->
                    HexRenderer.renderFacingKeys(canvas, x, y, facingSelectionFacings)

                in reachableFacings if highlight != HexHighlight.PATH -> {
                    val facings = reachableFacings.getValue(coords)
                    val color = when {
                        coords == pathDestination -> BoardRole.BOARD_ACTIVE
                        baseHighlight == HexHighlight.REACHABLE_RUN -> BoardRole.MOVE_RUN
                        baseHighlight == HexHighlight.REACHABLE_JUMP -> BoardRole.MOVE_JUMP
                        else -> BoardRole.MOVE_WALK
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
                    isSelectedTarget -> BoardRole.TARGET_SELECTED
                    isValidTarget -> BoardRole.TARGET_VALID
                    else -> playerColor(unit.owner)
                }
                val draftTorso = draftTorsoFacings[coords]
                val effectiveTorso = draftTorso ?: unit.torsoFacing
                val torsoFacing = if (effectiveTorso != unit.facing) effectiveTorso else null
                // Prone units (still active) are drawn with a lowercased id;
                // otherwise the unit's id is used as-is.
                val id = unit.id.value.take(2)
                val glyph = if (unit.isProne) id.lowercase() else id
                val renderColor = if (unit.isDestroyed) BoardRole.DESTROYED else unitColor
                UnitRenderer.render(
                    canvas, x, y,
                    glyph,
                    unit.facing,
                    renderColor,
                    torsoFacing = torsoFacing,
                    torsoColor = if (draftTorso != null) ChromeRole.DRAFT else renderColor,
                    isDestroyed = unit.isDestroyed,
                )
            }

            if (coords == cursorPosition) {
                canvas.markReveal(x, y, HexGeometry.HEX_WIDTH, HexGeometry.HEX_HEIGHT)
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
        private const val SIDE_LABEL_WIDTH: Int = 2
        private const val SIDE_LABEL_GAP: Int = 2

        internal const val MAP_ORIGIN_X: Int = SIDE_LABEL_WIDTH + SIDE_LABEL_GAP
        internal const val MAP_ORIGIN_Y: Int = 1
        internal const val BOTTOM_LABEL_GAP: Int = 1

        /** A board-only preview with the same map content and no deployed units. */
        internal fun preview(map: GameMap): BoardView =
            BoardView(PlayerGameState(UnitRoster(emptyList()), map))

        private val COORDINATE_STYLE: Cell.Style = Cell.Style(ChromeRole.TEXT_SUBTLE)

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
