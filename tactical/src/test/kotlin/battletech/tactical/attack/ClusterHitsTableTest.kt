package battletech.tactical.attack

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Verifies the Cluster Hits Table lookup against the canonical table in
 * `docs/rules/cluster-weapons.md` §1 — The Cluster Hits Table.
 *
 * Representative rows (full table in the doc):
 * Roll |  2 |  6 | 10 | 15 | 20 | 30 | 40
 *   2  |  1 |  2 |  3 |  5 |  6 | 10 | 12
 *   7  |  1 |  4 |  6 |  9 | 12 | 18 | 24
 *  12  |  2 |  6 | 10 | 15 | 20 | 30 | 40
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
        // 31 is between the supported 30 and 40 — never in the table
        assertThrows<IllegalStateException> { ClusterHitsTable.missilesHit(31, 7) }
    }

    @Test
    fun `roll below 2 throws an error`() {
        assertThrows<IllegalArgumentException> { ClusterHitsTable.missilesHit(6, 1) }
    }

    @Test
    fun `roll above 12 throws an error`() {
        assertThrows<IllegalArgumentException> { ClusterHitsTable.missilesHit(6, 13) }
    }

    // ── Newly added sizes (spot-checked against docs/rules/cluster-weapons.md §1) ──

    @Test
    fun `size 3 returns correct value for every roll`() {
        val expected = mapOf(
            2 to 1, 3 to 1, 4 to 1, 5 to 2, 6 to 2,
            7 to 2, 8 to 2, 9 to 2, 10 to 3, 11 to 3, 12 to 3,
        )
        for ((roll, missiles) in expected) {
            assertEquals(missiles, ClusterHitsTable.missilesHit(3, roll), "roll=$roll")
        }
    }

    @Test
    fun `size 7 returns correct value for every roll`() {
        // size 7 was previously missing; now supported
        val expected = mapOf(
            2 to 2, 3 to 2, 4 to 3, 5 to 4, 6 to 4,
            7 to 4, 8 to 4, 9 to 6, 10 to 6, 11 to 7, 12 to 7,
        )
        for ((roll, missiles) in expected) {
            assertEquals(missiles, ClusterHitsTable.missilesHit(7, roll), "roll=$roll")
        }
    }

    @Test
    fun `size 30 roll 2 returns 10`() =
        assertEquals(10, ClusterHitsTable.missilesHit(30, 2))

    @Test
    fun `size 30 roll 9 returns 24`() =
        assertEquals(24, ClusterHitsTable.missilesHit(30, 9))

    @Test
    fun `size 30 roll 12 returns 30`() =
        assertEquals(30, ClusterHitsTable.missilesHit(30, 12))

    @Test
    fun `size 30 returns correct value for every roll`() {
        val expected = mapOf(
            2 to 10, 3 to 10, 4 to 12, 5 to 18, 6 to 18,
            7 to 18, 8 to 18, 9 to 24, 10 to 24, 11 to 30, 12 to 30,
        )
        for ((roll, missiles) in expected) {
            assertEquals(missiles, ClusterHitsTable.missilesHit(30, roll), "roll=$roll")
        }
    }

    @Test
    fun `size 40 roll 2 returns 12`() =
        assertEquals(12, ClusterHitsTable.missilesHit(40, 2))

    @Test
    fun `size 40 roll 4 returns 18`() =
        assertEquals(18, ClusterHitsTable.missilesHit(40, 4))

    @Test
    fun `size 40 roll 12 returns 40 (absolute maximum)`() =
        assertEquals(40, ClusterHitsTable.missilesHit(40, 12))

    @Test
    fun `size 40 returns correct value for every roll`() {
        val expected = mapOf(
            2 to 12, 3 to 12, 4 to 18, 5 to 24, 6 to 24,
            7 to 24, 8 to 24, 9 to 32, 10 to 32, 11 to 40, 12 to 40,
        )
        for ((roll, missiles) in expected) {
            assertEquals(missiles, ClusterHitsTable.missilesHit(40, roll), "roll=$roll")
        }
    }
}
