package battletech.tactical.model

import kotlin.math.atan2

public object FiringArc {

    public fun forwardHexSides(torsoFacing: HexDirection): Set<HexDirection> = setOf(
        torsoFacing.rotateCounterClockwise(),
        torsoFacing,
        torsoFacing.rotateClockwise(),
    )

    public fun bearingDirection(from: HexCoordinates, to: HexCoordinates): HexDirection {
        val (fx, _, fz) = from.toCube()
        val (tx, _, tz) = to.toCube()
        val dx = tx - fx
        val dz = tz - fz
        // Convert cube coords to 2D cartesian for angle calculation
        // In pointy-top hex: x-axis goes right, z-axis goes down-right
        val px = dx + dz * 0.5
        val py = dz * SQRT3_OVER_2
        // atan2 returns angle from positive x-axis, counterclockwise
        // We want angle from north (positive y in screen = negative py here), clockwise
        val angle = atan2(px, -py)
        val degrees = ((Math.toDegrees(angle) + 360) % 360)
        return when {
            degrees < 30 || degrees >= 330 -> HexDirection.N
            degrees < 90 -> HexDirection.NE
            degrees < 150 -> HexDirection.SE
            degrees < 210 -> HexDirection.S
            degrees < 270 -> HexDirection.SW
            else -> HexDirection.NW
        }
    }

    public fun forwardArc(origin: HexCoordinates, torsoFacing: HexDirection, gameMap: GameMap): Set<HexCoordinates> {
        val forwardSides = forwardHexSides(torsoFacing)
        return gameMap.hexes.keys
            .filter { it != origin }
            .filter { bearingDirection(origin, it) in forwardSides }
            .toSet()
    }

    private const val SQRT3_OVER_2 = 0.8660254037844386

    private fun HexCoordinates.toCube(): Triple<Int, Int, Int> {
        val x = col
        val z = row - (col - (col and 1)) / 2
        val y = -x - z
        return Triple(x, y, z)
    }
}
