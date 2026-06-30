package battletech.tactical.attack

/**
 * Cluster Hits Table from `docs/rules/cluster-weapons.md` §1 — The Cluster Hits Table.
 *
 * Given a launcher [size] and a 2d6 total, returns the number of missiles that connect.
 * Supported sizes: 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
 *                  21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 40.
 *
 * Representative rows (see full table in the doc):
 * ```
 * Roll |  2 |  6 | 10 | 15 | 20 | 30 | 40
 *   2  |  1 |  2 |  3 |  5 |  6 | 10 | 12
 *   7  |  1 |  4 |  6 |  9 | 12 | 18 | 24
 *  12  |  2 |  6 | 10 | 15 | 20 | 30 | 40
 * ```
 */
public object ClusterHitsTable {

    /**
     * Returns the number of missiles that hit for a given launcher [size] and [roll2d6Total].
     *
     * @param size the launcher size (supported: 2–30 and 40; see `docs/rules/cluster-weapons.md`)
     * @param roll2d6Total the 2d6 total (2..12)
     */
    public fun missilesHit(size: Int, roll2d6Total: Int): Int {
        require(roll2d6Total in 2..12) { "Invalid 2d6 total: $roll2d6Total (must be 2..12)" }
        val col = TABLE[size]
            ?: error("Unknown cluster size: $size (supported: 2–30 and 40)")
        return col[roll2d6Total - 2]
    }

    // Each IntArray has 11 entries for rolls 2,3,4,5,6,7,8,9,10,11,12 (index = roll - 2).
    // Values transcribed from docs/rules/cluster-weapons.md §1 — The Cluster Hits Table.
    private val TABLE: Map<Int, IntArray> = mapOf(
        2  to intArrayOf( 1,  1,  1,  1,  1,  1,  2,  2,  2,  2,  2),
        3  to intArrayOf( 1,  1,  1,  2,  2,  2,  2,  2,  3,  3,  3),
        4  to intArrayOf( 1,  2,  2,  2,  2,  3,  3,  3,  3,  4,  4),
        5  to intArrayOf( 1,  2,  2,  3,  3,  3,  3,  4,  4,  5,  5),
        6  to intArrayOf( 2,  2,  3,  3,  4,  4,  4,  5,  5,  6,  6),
        7  to intArrayOf( 2,  2,  3,  4,  4,  4,  4,  6,  6,  7,  7),
        8  to intArrayOf( 3,  3,  4,  4,  5,  5,  5,  6,  6,  8,  8),
        9  to intArrayOf( 3,  3,  4,  5,  5,  5,  5,  7,  7,  9,  9),
        10 to intArrayOf( 3,  3,  4,  6,  6,  6,  6,  8,  8, 10, 10),
        11 to intArrayOf( 4,  4,  5,  7,  7,  7,  7,  9,  9, 11, 11),
        12 to intArrayOf( 4,  4,  5,  8,  8,  8,  8, 10, 10, 12, 12),
        13 to intArrayOf( 4,  4,  5,  8,  8,  8,  8, 11, 11, 13, 13),
        14 to intArrayOf( 5,  5,  6,  9,  9,  9,  9, 11, 11, 14, 14),
        15 to intArrayOf( 5,  5,  6,  9,  9,  9,  9, 12, 12, 15, 15),
        16 to intArrayOf( 5,  5,  7, 10, 10, 10, 10, 13, 13, 16, 16),
        17 to intArrayOf( 5,  5,  7, 10, 10, 10, 10, 14, 14, 17, 17),
        18 to intArrayOf( 6,  6,  8, 11, 11, 11, 11, 14, 14, 18, 18),
        19 to intArrayOf( 6,  6,  8, 11, 11, 11, 11, 15, 15, 19, 19),
        20 to intArrayOf( 6,  6,  9, 12, 12, 12, 12, 16, 16, 20, 20),
        21 to intArrayOf( 7,  7,  9, 13, 13, 13, 13, 17, 17, 21, 21),
        22 to intArrayOf( 7,  7,  9, 14, 14, 14, 14, 18, 18, 22, 22),
        23 to intArrayOf( 7,  7, 10, 15, 15, 15, 15, 19, 19, 23, 23),
        24 to intArrayOf( 8,  8, 10, 16, 16, 16, 16, 20, 20, 24, 24),
        25 to intArrayOf( 8,  8, 10, 16, 16, 16, 16, 21, 21, 25, 25),
        26 to intArrayOf( 9,  9, 11, 17, 17, 17, 17, 21, 21, 26, 26),
        27 to intArrayOf( 9,  9, 11, 17, 17, 17, 17, 22, 22, 27, 27),
        28 to intArrayOf( 9,  9, 11, 17, 17, 17, 17, 23, 23, 28, 28),
        29 to intArrayOf(10, 10, 12, 18, 18, 18, 18, 23, 23, 29, 29),
        30 to intArrayOf(10, 10, 12, 18, 18, 18, 18, 24, 24, 30, 30),
        40 to intArrayOf(12, 12, 18, 24, 24, 24, 24, 32, 32, 40, 40),
    )
}
