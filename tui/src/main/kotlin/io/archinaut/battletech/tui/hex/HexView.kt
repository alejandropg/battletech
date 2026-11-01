package io.archinaut.battletech.tui.hex

import io.archinaut.battletech.tactical.model.Hex
import io.archinaut.battletech.tactical.model.MovementMode
import io.archinaut.battletech.tactical.model.Terrain
import io.archinaut.battletech.tui.icon.depthIcon
import io.archinaut.battletech.tui.icon.elevationIcon
import io.archinaut.battletech.tui.icon.movementModeIcon
import io.archinaut.battletech.tui.icon.targetIcon
import io.archinaut.battletech.tui.icon.terrainIcon
import io.archinaut.battletech.tui.screen.BoardRole
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.widget.Widget

/**
 * One hex in its [HexGeometry.HEX_WIDTH] x [HexGeometry.HEX_HEIGHT] area: its outline, terrain
 * fill and icon, elevation or depth badge, and the overlay [highlight] calls for, with a [HexHighlight.PATH]
 * step showing the [movementMode] icon.
 */
internal class HexView(
    private val hex: Hex,
    private val highlight: HexHighlight,
    private val movementMode: MovementMode? = null,
) : Widget {

    override fun render(canvas: Canvas) {
        val bg = terrainFill(hex)
        val borderFg = if (highlight == HexHighlight.CURSOR) BoardRole.BOARD_ACTIVE else BoardRole.BOARD_BORDER

        renderBorder(canvas, borderFg, bg)
        renderContent(canvas, bg)
        renderTerrain(canvas, hex.terrain, bg)
        renderLevelBadge(canvas, hex.elevation, hex.depth)
        when (highlight) {
            HexHighlight.REACHABLE_WALK -> renderOverlay(canvas, ".", BoardRole.MOVE_WALK)
            HexHighlight.REACHABLE_RUN -> renderOverlay(canvas, ".", BoardRole.MOVE_RUN)
            HexHighlight.REACHABLE_JUMP -> renderOverlay(canvas, ".", BoardRole.MOVE_JUMP)
            HexHighlight.ATTACK_RANGE -> renderOverlay(canvas, ".", BoardRole.ATTACK_RANGE)
            HexHighlight.LINE_OF_SIGHT -> renderMarker(canvas, ".", BoardRole.LINE_OF_SIGHT)
            HexHighlight.LINE_OF_SIGHT_SELECTED -> renderMarker(canvas, targetIcon(), BoardRole.TARGET_SELECTED)
            HexHighlight.PATH -> {
                val icon = if (movementMode != null) movementModeIcon(movementMode) else "*"
                renderOverlay(canvas, icon, BoardRole.BOARD_ACTIVE)
            }
            else -> Unit
        }
    }

    private fun renderOverlay(canvas: Canvas, char: String, color: BoardRole) {
        canvas.setString(HexCells.OVERLAY_X, HexCells.OVERLAY_Y, char, Style(fg = color))
    }

    private fun renderMarker(canvas: Canvas, char: String, color: BoardRole) {
        canvas.setString(HexCells.MARKER_X, HexCells.MARKER_Y, char, Style(fg = color))
    }

    // The border glyphs carry the terrain `bg` too, so the whole hexagon reads as filled rather
    // than an outline floating on the terminal background. Adjacent hexes share their edge columns
    // (9-col glyph, 7-col stride, last-write-wins), so a shared edge column adopts the neighbour's
    // tint — negligible with these soft colors.
    private fun renderBorder(canvas: Canvas, fg: BoardRole, bg: BoardRole) {
        // Row 0: "  _____  " — the top edge. Its cells coincide with the hex-above's bottom edge
        // (row 4), so keep whatever background is already painted there rather than tinting: the
        // top edge belongs to the upper hex. Tinting it would paint a coloured band protruding
        // above the hexagon.
        for (i in 2..6) {
            canvas.setString(i, 0, "_", Style(fg = fg))
        }
        val style = Style(fg, bg)
        // Row 1: " /     \ "
        canvas.set(1, 1, "/", style)
        canvas.set(7, 1, "\\", style)
        // Row 2: "/       \"
        canvas.set(0, 2, "/", style)
        canvas.set(8, 2, "\\", style)
        // Row 3: "\       /"
        canvas.set(0, 3, "\\", style)
        canvas.set(8, 3, "/", style)
        // Row 4: " \_____/ "
        canvas.set(1, 4, "\\", style)
        for (i in 2..6) {
            canvas.set(i, 4, "_", style)
        }
        canvas.set(7, 4, "/", style)
    }

    private fun renderContent(canvas: Canvas, bg: BoardRole) {
        val style = Style(StandardRole.DEFAULT, bg)
        // Row 1 content (narrow): 2..6
        for (i in 2..6) {
            canvas.set(i, 1, " ", style)
        }
        // Rows 2 and 3 content (wide): 1..7
        for (row in 2..3) {
            for (i in 1..7) {
                canvas.set(i, row, " ", style)
            }
        }
    }

    private fun renderTerrain(canvas: Canvas, terrain: Terrain, bg: BoardRole) {
        val color = when (terrain) {
            Terrain.CLEAR       -> return
            Terrain.LIGHT_WOODS -> BoardRole.TERRAIN_WOODS_LIGHT_ICON
            Terrain.HEAVY_WOODS -> BoardRole.TERRAIN_WOODS_HEAVY_ICON
            Terrain.WATER       -> BoardRole.TERRAIN_WATER_ICON
            Terrain.ROUGH       -> BoardRole.TERRAIN_ROUGH_ICON
        }
        canvas.set(2, 1, terrainIcon(terrain), Style(color, bg))
    }

    /**
     * The elevation/depth badge glyph. Elevation takes precedence when both values are non-zero.
     * An elevation glyph uses its tier's badge background. A depth glyph changes only the cell's
     * foreground, preserving the background painted by [terrainFill].
     * A hex with neither elevation nor depth renders no badge at all, leaving the terrain fill
     * intact either way.
     */
    private fun renderLevelBadge(canvas: Canvas, elevation: Int, depth: Int) {
        if (elevation != 0) {
            val style = Style(BoardRole.ELEVATION_BADGE_FG, elevationBadgeBg(elevation))
            canvas.set(6, 1, elevationIcon(elevation), style)
        } else if (depth != 0) {
            canvas.setString(6, 1, depthIcon(depth), Style(fg = BoardRole.ELEVATION_BADGE_FG))
        }
    }

    private companion object {

        /**
         * The hex's whole-hex fill. A material terrain (woods, water, rough) always wins outright —
         * elevation never changes it, so an elevated forest stays green and its height shows only via
         * the elevation badge. A CLEAR hex has no material of its own, so elevation gets to fill the
         * whole hex instead of just the badge cell: an elevated clear hex reads as a hill, not a plain
         * with a small numbered sticker on it. WATER's depth tiers are the only terrain sub-case.
         */
        private fun terrainFill(hex: Hex): BoardRole = when (hex.terrain) {
            Terrain.LIGHT_WOODS -> BoardRole.TERRAIN_WOODS_LIGHT_BG
            Terrain.HEAVY_WOODS -> BoardRole.TERRAIN_WOODS_HEAVY_BG
            Terrain.WATER       -> when {
                hex.depth <= 1 -> BoardRole.TERRAIN_WATER_SHALLOW_BG
                hex.depth == 2 -> BoardRole.TERRAIN_WATER_DEEP_BG
                else -> BoardRole.TERRAIN_WATER_VERY_DEEP_BG
            }
            Terrain.ROUGH       -> BoardRole.TERRAIN_ROUGH_BG
            Terrain.CLEAR       -> if (hex.elevation > 0) elevationBadgeBg(hex.elevation) else BoardRole.TERRAIN_CLEAR_BG
        }

        private fun elevationBadgeBg(elevation: Int): BoardRole = when (elevation) {
            1 -> BoardRole.ELEVATION_1_BADGE_BG
            2 -> BoardRole.ELEVATION_2_BADGE_BG
            else -> BoardRole.ELEVATION_HIGH_BADGE_BG
        }
    }
}
