package battletech.tactical.model

public enum class HexDirection {
    N, NE, SE, S, SW, NW;

    public fun rotateClockwise(): HexDirection = entries[(ordinal + 1) % DIRECTION_COUNT]

    public fun rotateCounterClockwise(): HexDirection = entries[(ordinal + DIRECTION_COUNT - 1) % DIRECTION_COUNT]

    public fun turnCostTo(other: HexDirection): Int {
        val diff = (other.ordinal - ordinal + DIRECTION_COUNT) % DIRECTION_COUNT
        return minOf(diff, DIRECTION_COUNT - diff)
    }

    private companion object {
        const val DIRECTION_COUNT: Int = 6
    }
}
