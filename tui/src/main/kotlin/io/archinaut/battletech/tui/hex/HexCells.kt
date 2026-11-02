package io.archinaut.battletech.tui.hex

import io.archinaut.battletech.tactical.model.HexDirection

/** Where the overlays sit inside one hex's [HexGeometry.HEX_WIDTH] x [HexGeometry.HEX_HEIGHT] area. */
internal object HexCells {

    /** Movement, range, and path overlays: the bottom-center cell, clear of the terrain icon and elevation badge. */
    internal const val OVERLAY_X: Int = 4
    internal const val OVERLAY_Y: Int = 2

    /**
     * Line-of-sight and selected-target markers: the top-center cell, a row above the overlay so
     * the two never collide; units occupy the lower rows and cannot overwrite either.
     */
    internal const val MARKER_X: Int = 4
    internal const val MARKER_Y: Int = 1

    // Row 2: NW(+2), N(+4), NE(+6)
    // Row 3: SW(+2), S(+4), SE(+6)
    /** The cell, as (column, row), that shows [direction]'s facing arrow or key. */
    internal fun facing(direction: HexDirection): Pair<Int, Int> = when (direction) {
        HexDirection.N  -> 4 to 2
        HexDirection.NE -> 6 to 2
        HexDirection.SE -> 6 to 3
        HexDirection.S  -> 4 to 3
        HexDirection.SW -> 2 to 3
        HexDirection.NW -> 2 to 2
    }
}
