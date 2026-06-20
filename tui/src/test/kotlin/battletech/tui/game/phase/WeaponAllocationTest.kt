package battletech.tui.game.phase

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.model.HexDirection
import battletech.tactical.unit.UnitId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class WeaponAllocationTest {

    // ── Fixtures ────────────────────────────────────────────────────────────

    private fun weaponInfo(
        index: Int,
        available: Boolean = true,
    ) = WeaponTargetInfo(
        weaponIndex = index,
        weaponName = "ML-$index",
        targetDiceRoll = 6,
        damage = 5,
        modifiers = emptyList(),
        available = available,
    )

    private fun targetInfo(
        id: String,
        vararg weapons: WeaponTargetInfo,
    ) = TargetInfo(
        unitId = UnitId(id),
        unitName = id,
        weapons = weapons.toList(),
    )

    private val t1 = UnitId("t1")
    private val t2 = UnitId("t2")
    private val t3 = UnitId("t3")

    /** Two targets, one weapon each (indices 0 and 1 on the attacker). */
    private fun twoTargets() = listOf(
        targetInfo("t1", weaponInfo(0), weaponInfo(1)),
        targetInfo("t2", weaponInfo(2)),
    )

    // ── toggle ───────────────────────────────────────────────────────────────

    @Nested
    inner class ToggleTest {

        @Test
        fun `toggle on unavailable weapon is a no-op`() {
            val targets = listOf(targetInfo("t1", weaponInfo(0, available = false)))
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
            )
            val result = alloc.toggle(targets)
            assertEquals(alloc, result)
        }

        @Test
        fun `toggle assigns weapon to target`() {
            val targets = listOf(targetInfo("t1", weaponInfo(0)))
            val alloc = WeaponAllocation(torsoFacing = HexDirection.N)
            val result = alloc.toggle(targets)
            assertTrue(result.weaponAssignments[t1]?.contains(0) == true)
        }

        @Test
        fun `toggle second time removes weapon from target`() {
            val targets = listOf(targetInfo("t1", weaponInfo(0)))
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(t1 to setOf(0)),
                primaryTargetId = t1,
            )
            val result = alloc.toggle(targets)
            assertTrue(result.weaponAssignments[t1]?.contains(0) != true)
        }

        @Test
        fun `toggle first assignment promotes target to primary`() {
            val targets = listOf(targetInfo("t1", weaponInfo(0)))
            val alloc = WeaponAllocation(torsoFacing = HexDirection.N)
            assertNull(alloc.primaryTargetId)
            val result = alloc.toggle(targets)
            assertEquals(t1, result.primaryTargetId)
        }

        @Test
        fun `toggle does not change primary when already set to another target`() {
            val targets = twoTargets()
            // t1 is already primary with weapon 0 assigned; cursor on t2 weapon 2
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(t1 to setOf(0)),
                primaryTargetId = t1,
                cursorTargetIndex = 1,   // cursor on t2
                cursorWeaponIndex = 0,   // weapon index 2 on t2
            )
            val result = alloc.toggle(targets)
            assertEquals(t1, result.primaryTargetId)  // primary unchanged
            assertTrue(result.weaponAssignments[t2]?.contains(2) == true)
        }

        @Test
        fun `toggle off last weapon on primary clears primaryTargetId`() {
            val targets = listOf(targetInfo("t1", weaponInfo(0)))
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(t1 to setOf(0)),
                primaryTargetId = t1,
            )
            val result = alloc.toggle(targets)
            assertNull(result.primaryTargetId)
        }

        @Test
        fun `toggle off last weapon on primary promotes secondary`() {
            // t1=primary with weapon 0; t2 has weapon 2
            // cursor on t1/weapon 0 → removing t1's last weapon → t2 becomes primary
            val targets = listOf(
                targetInfo("t1", weaponInfo(0)),
                targetInfo("t2", weaponInfo(2)),
            )
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(t1 to setOf(0), t2 to setOf(2)),
                primaryTargetId = t1,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
            )
            val result = alloc.toggle(targets)
            assertEquals(t2, result.primaryTargetId)
        }

        @Test
        fun `toggle when weapon assigned to another target is a no-op`() {
            // weapon 0 is already assigned to t1; cursor is on t2 where weapon 0 also appears
            val targets = listOf(
                targetInfo("t1", weaponInfo(0)),
                targetInfo("t2", weaponInfo(0)),  // same weapon index from attacker
            )
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(t1 to setOf(0)),
                primaryTargetId = t1,
                cursorTargetIndex = 1,
                cursorWeaponIndex = 0,
            )
            val result = alloc.toggle(targets)
            assertEquals(alloc, result)
        }

        @Test
        fun `toggle on empty targets list is a no-op`() {
            val alloc = WeaponAllocation(torsoFacing = HexDirection.N)
            assertEquals(alloc, alloc.toggle(emptyList()))
        }
    }

    // ── twist ────────────────────────────────────────────────────────────────

    @Nested
    inner class TwistTest {

        @Test
        fun `twist updates torso facing`() {
            val alloc = WeaponAllocation(torsoFacing = HexDirection.N)
            val result = alloc.twist(HexDirection.NE, emptyList(), emptySet())
            assertEquals(HexDirection.NE, result.torsoFacing)
        }

        @Test
        fun `twist prunes assignments to targets no longer valid`() {
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(t1 to setOf(0), t2 to setOf(1)),
                primaryTargetId = t1,
            )
            // After twist only t2 remains valid
            val result = alloc.twist(HexDirection.NE, emptyList(), setOf(t2))
            assertTrue(t1 !in result.weaponAssignments)
            assertTrue(t2 in result.weaponAssignments)
        }

        @Test
        fun `twist clears primary when primary target no longer valid`() {
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(t1 to setOf(0)),
                primaryTargetId = t1,
            )
            val result = alloc.twist(HexDirection.NE, emptyList(), emptySet())
            assertNull(result.primaryTargetId)
        }

        @Test
        fun `twist retains primary when primary target still valid`() {
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(t1 to setOf(0)),
                primaryTargetId = t1,
            )
            val result = alloc.twist(HexDirection.NE, emptyList(), setOf(t1))
            assertEquals(t1, result.primaryTargetId)
        }

        @Test
        fun `twist clamps cursorTargetIndex when target list shrinks`() {
            val targets = listOf(
                targetInfo("t1", weaponInfo(0)),
                targetInfo("t2", weaponInfo(1)),
            )
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 1,
                cursorWeaponIndex = 0,
            )
            // After twist only one target remains
            val oneTarget = listOf(targetInfo("t1", weaponInfo(0)))
            val result = alloc.twist(HexDirection.NE, oneTarget, setOf(t1))
            assertEquals(0, result.cursorTargetIndex)
        }

        @Test
        fun `twist clamps cursorWeaponIndex to max for new target`() {
            val targets = listOf(targetInfo("t1", weaponInfo(0), weaponInfo(1), weaponInfo(2)))
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 2,
            )
            // After twist target has only 1 weapon
            val newTargets = listOf(targetInfo("t1", weaponInfo(0)))
            val result = alloc.twist(HexDirection.NE, newTargets, setOf(t1))
            assertEquals(0, result.cursorWeaponIndex)
        }

        @Test
        fun `twist with empty new target list resets cursors to 0`() {
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 3,
                cursorWeaponIndex = 2,
            )
            val result = alloc.twist(HexDirection.NE, emptyList(), emptySet())
            assertEquals(0, result.cursorTargetIndex)
            assertEquals(0, result.cursorWeaponIndex)
        }
    }

    // ── navigate ─────────────────────────────────────────────────────────────

    @Nested
    inner class NavigateTest {

        /** targets: t1[w0, w1], t2[w2] → flat = (0,0),(0,1),(1,0) */
        private fun threeWeaponTargets() = listOf(
            targetInfo("t1", weaponInfo(0), weaponInfo(1)),
            targetInfo("t2", weaponInfo(2)),
        )

        @Test
        fun `navigate +1 moves to next weapon`() {
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
            )
            val result = alloc.navigate(+1, threeWeaponTargets())
            assertEquals(0, result.cursorTargetIndex)
            assertEquals(1, result.cursorWeaponIndex)
        }

        @Test
        fun `navigate +1 wraps across target boundary`() {
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 1,
            )
            val result = alloc.navigate(+1, threeWeaponTargets())
            assertEquals(1, result.cursorTargetIndex)
            assertEquals(0, result.cursorWeaponIndex)
        }

        @Test
        fun `navigate +1 from last wraps to first`() {
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 1,
                cursorWeaponIndex = 0,
            )
            val result = alloc.navigate(+1, threeWeaponTargets())
            assertEquals(0, result.cursorTargetIndex)
            assertEquals(0, result.cursorWeaponIndex)
        }

        @Test
        fun `navigate -1 wraps from first to last`() {
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
            )
            val result = alloc.navigate(-1, threeWeaponTargets())
            assertEquals(1, result.cursorTargetIndex)
            assertEquals(0, result.cursorWeaponIndex)
        }

        @Test
        fun `navigate on empty targets is a no-op`() {
            val alloc = WeaponAllocation(torsoFacing = HexDirection.N)
            assertEquals(alloc, alloc.navigate(+1, emptyList()))
        }

        @Test
        fun `navigate on targets with no weapons is a no-op`() {
            val targets = listOf(targetInfo("t1" /* no weapons */))
            val alloc = WeaponAllocation(torsoFacing = HexDirection.N)
            assertEquals(alloc, alloc.navigate(+1, targets))
        }
    }

    // ── clickTarget ──────────────────────────────────────────────────────────

    @Nested
    inner class ClickTargetTest {

        @Test
        fun `clickTarget on valid target jumps cursor`() {
            val targets = listOf(
                targetInfo("t1", weaponInfo(0)),
                targetInfo("t2", weaponInfo(1)),
            )
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
            )
            val result = alloc.clickTarget(t2, targets)
            assertEquals(1, result.cursorTargetIndex)
            assertEquals(0, result.cursorWeaponIndex)
        }

        @Test
        fun `clickTarget on invalid id is a no-op`() {
            val targets = listOf(targetInfo("t1", weaponInfo(0)))
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
            )
            val result = alloc.clickTarget(t3, targets)
            assertEquals(alloc, result)
        }

        @Test
        fun `clickTarget resets weapon cursor to 0`() {
            val targets = listOf(
                targetInfo("t1", weaponInfo(0), weaponInfo(1)),
                targetInfo("t2", weaponInfo(2)),
            )
            val alloc = WeaponAllocation(
                torsoFacing = HexDirection.N,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 1,
            )
            val result = alloc.clickTarget(t2, targets)
            assertEquals(1, result.cursorTargetIndex)
            assertEquals(0, result.cursorWeaponIndex)
        }
    }
}
