package battletech.tactical.model

import kotlin.math.abs
import kotlin.math.max

public data class HexCoordinates(public val col: Int, public val row: Int) {

    public fun distanceTo(other: HexCoordinates): Int {
        val (ax, ay, az) = toCube()
        val (bx, by, bz) = other.toCube()
        return max(max(abs(ax - bx), abs(ay - by)), abs(az - bz))
    }

    private fun toCube(): Triple<Int, Int, Int> {
        val x = col
        val z = row - (col - (col and 1)) / 2
        val y = -x - z
        return Triple(x, y, z)
    }
}
