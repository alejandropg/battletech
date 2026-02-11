package battletech.tactical.model

import kotlin.math.abs
import kotlin.math.max

/**
 * Hexagonal coordinates using the "odd-q" offset coordinate system (pointy top).
 *
 * Visualization of the coordinate system:
 * ```text
 *      ___     ___     ___
 *     /0,0\___/2,0\___/4,0\___
 *     \___/1,0\___/3,0\___/5,0\
 *     /0,1\___/2,1\___/4,1\___/
 *     \___/1,1\___/3,1\___/5,1\
 *     /0,2\___/2,2\___/4,2\___/
 *     \___/1,2\___/3,2\___/5,2\
 *         \___/   \___/   \___/
 * ```
 */
public data class HexCoordinates(
    public val col: Int,
    public val row: Int
) {

    public fun neighbor(direction: HexDirection): HexCoordinates {
        val isEvenCol = col % 2 == 0
        val (dc, dr) = if (isEvenCol) EVEN_COL_OFFSETS[direction.ordinal] else ODD_COL_OFFSETS[direction.ordinal]
        return HexCoordinates(col + dc, row + dr)
    }

    public fun neighbors(): List<HexCoordinates> = HexDirection.entries.map { neighbor(it) }

    public fun distanceTo(other: HexCoordinates): Int {
        val (ax, ay, az) = toCube()
        val (bx, by, bz) = other.toCube()
        return max(
            max(abs(ax - bx), abs(ay - by)),
            abs(az - bz)
        )
    }

    private fun toCube(): Triple<Int, Int, Int> {
        val x = col
        val z = row - (col - (col and 1)) / 2
        val y = -x - z
        return Triple(x, y, z)
    }

    private companion object {
        //                              N       NE      SE      S       SW      NW
        val EVEN_COL_OFFSETS = arrayOf(0 to -1, 1 to -1, 1 to 0, 0 to 1, -1 to 0, -1 to -1)
        val ODD_COL_OFFSETS = arrayOf(0 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0)
    }
}
