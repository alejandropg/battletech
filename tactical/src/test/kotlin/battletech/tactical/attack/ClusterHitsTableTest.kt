package battletech.tactical.attack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Verifies the Cluster Hits Table lookup against the canonical table in
 * `docs/missing-rules.md` §Cluster-Hit Weapons.
 *
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
 */
internal class ClusterHitsTableTest {

    // ── Corner cases ──────────────────────────────────────────────────────────

    @Test
    fun `size 2 roll 2 returns 1 (minimum entry)`() =
        assertEquals(1, ClusterHitsTable.missilesHit(2, 2))

    @Test
    fun `size 20 roll 12 returns 20 (maximum entry)`() =
        assertEquals(20, ClusterHitsTable.missilesHit(20, 12))

    @Test
    fun `size 20 roll 4 returns 9`() =
        assertEquals(9, ClusterHitsTable.missilesHit(20, 4))

    @Test
    fun `size 20 roll 2 returns 6`() =
        assertEquals(6, ClusterHitsTable.missilesHit(20, 2))

    @Test
    fun `size 20 roll 9 returns 16`() =
        assertEquals(16, ClusterHitsTable.missilesHit(20, 9))

    @Test
    fun `size 2 roll 12 returns 2`() =
        assertEquals(2, ClusterHitsTable.missilesHit(2, 12))

    // ── All rows for size 6 ───────────────────────────────────────────────────

    @Test
    fun `size 6 returns correct value for every roll`() {
        val expected = mapOf(
            2 to 2, 3 to 2, 4 to 3, 5 to 3, 6 to 4,
            7 to 4, 8 to 4, 9 to 5, 10 to 5, 11 to 6, 12 to 6,
        )
        for ((roll, missiles) in expected) {
            assertEquals(missiles, ClusterHitsTable.missilesHit(6, roll), "roll=$roll")
        }
    }

    // ── All rows for size 10 ──────────────────────────────────────────────────

    @Test
    fun `size 10 returns correct value for every roll`() {
        val expected = mapOf(
            2 to 3, 3 to 3, 4 to 4, 5 to 6, 6 to 6,
            7 to 6, 8 to 6, 9 to 8, 10 to 8, 11 to 10, 12 to 10,
        )
        for ((roll, missiles) in expected) {
            assertEquals(missiles, ClusterHitsTable.missilesHit(10, roll), "roll=$roll")
        }
    }

    // ── All rows for size 15 ──────────────────────────────────────────────────

    @Test
    fun `size 15 returns correct value for every roll`() {
        val expected = mapOf(
            2 to 5, 3 to 5, 4 to 6, 5 to 9, 6 to 9,
            7 to 9, 8 to 9, 9 to 12, 10 to 12, 11 to 15, 12 to 15,
        )
        for ((roll, missiles) in expected) {
            assertEquals(missiles, ClusterHitsTable.missilesHit(15, roll), "roll=$roll")
        }
    }

    // ── All rows for size 20 ──────────────────────────────────────────────────

    @Test
    fun `size 20 returns correct value for every roll`() {
        val expected = mapOf(
            2 to 6, 3 to 6, 4 to 9, 5 to 12, 6 to 12,
            7 to 12, 8 to 12, 9 to 16, 10 to 16, 11 to 20, 12 to 20,
        )
        for ((roll, missiles) in expected) {
            assertEquals(missiles, ClusterHitsTable.missilesHit(20, roll), "roll=$roll")
        }
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    @Test
    fun `unknown size throws an error`() {
        assertThrows<IllegalStateException> { ClusterHitsTable.missilesHit(7, 7) }
    }

    @Test
    fun `roll below 2 throws an error`() {
        assertThrows<IllegalArgumentException> { ClusterHitsTable.missilesHit(6, 1) }
    }

    @Test
    fun `roll above 12 throws an error`() {
        assertThrows<IllegalArgumentException> { ClusterHitsTable.missilesHit(6, 13) }
    }
}
