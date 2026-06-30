package battletech.tactical.attack

/**
 * Cluster Hits Table from `docs/missing-rules.md` §Cluster-Hit Weapons.
 *
 * Given a launcher [size] and a 2d6 total, returns the number of missiles that connect.
 * Supported sizes: 2, 4, 5, 6, 10, 15, 20.
 *
 * ```
 * Roll | 2 | 4 | 5 | 6 | 10 | 15 | 20
 *   2  | 1 | 1 | 1 | 2 |  3 |  5 |  6
 *   3  | 1 | 2 | 2 | 2 |  3 |  5 |  6
 *   4  | 1 | 2 | 2 | 3 |  4 |  6 |  9
 *   5  | 1 | 2 | 3 | 3 |  6 |  9 | 12
 *   6  | 1 | 2 | 3 | 4 |  6 |  9 | 12
 *   7  | 1 | 3 | 3 | 4 |  6 |  9 | 12
 *   8  | 2 | 3 | 3 | 4 |  6 |  9 | 12
 *   9  | 2 | 3 | 4 | 5 |  8 | 12 | 16
 *  10  | 2 | 3 | 4 | 5 |  8 | 12 | 16
 *  11  | 2 | 4 | 5 | 6 | 10 | 15 | 20
 *  12  | 2 | 4 | 5 | 6 | 10 | 15 | 20
 * ```
 */
public object ClusterHitsTable {

    /**
     * Returns the number of missiles that hit for a given launcher [size] and [roll2d6Total].
     *
     * @param size the launcher size (supported: 2, 4, 5, 6, 10, 15, 20)
     * @param roll2d6Total the 2d6 total (2..12)
     */
    public fun missilesHit(size: Int, roll2d6Total: Int): Int {
        require(roll2d6Total in 2..12) { "Invalid 2d6 total: $roll2d6Total (must be 2..12)" }
        val col = TABLE[size] ?: error("Unknown cluster size: $size (supported: 2, 4, 5, 6, 10, 15, 20)")
        return col[roll2d6Total - 2]
    }

    // Each IntArray has 11 entries for rolls 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 (index = roll - 2).
    private val TABLE: Map<Int, IntArray> = mapOf(
        2  to intArrayOf(1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2),
        4  to intArrayOf(1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4),
        5  to intArrayOf(1, 2, 2, 3, 3, 3, 3, 4, 4, 5, 5),
        6  to intArrayOf(2, 2, 3, 3, 4, 4, 4, 5, 5, 6, 6),
        10 to intArrayOf(3, 3, 4, 6, 6, 6, 6, 8, 8, 10, 10),
        15 to intArrayOf(5, 5, 6, 9, 9, 9, 9, 12, 12, 15, 15),
        20 to intArrayOf(6, 6, 9, 12, 12, 12, 12, 16, 16, 20, 20),
    )
}
