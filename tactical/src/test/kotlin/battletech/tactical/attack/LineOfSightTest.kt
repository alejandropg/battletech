package battletech.tactical.attack

import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import battletech.tactical.query.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [lineOfSight]:
 *   LIGHT_WOODS = 1 level, HEAVY_WOODS = 2 levels, blocking threshold ≥ 3 levels.
 *
 * All tests use a straight-column layout so the intervening hexes are predictable:
 *   attacker (0,0) → target (0,3): intervening hexes are (0,1) and (0,2).
 *   attacker (0,0) → target (0,2): intervening hex is (0,1).
 */
internal class LineOfSightTest {

    private val attackerPos = HexCoordinates(0, 0)

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun los(
        attackerPos: HexCoordinates = this.attackerPos,
        targetPos: HexCoordinates,
        hexes: Map<HexCoordinates, Hex> = emptyMap(),
    ): LineOfSightResult {
        val attacker = aUnit(id = "attacker", position = attackerPos)
        val target = aUnit(id = "target", position = targetPos)
        return lineOfSight(attacker, target, GameMap(hexes))
    }

    // ── Woods modifier (no blocking) ─────────────────────────────────────────

    @Test
    fun `one intervening heavy woods adds two to modifier and keeps LOS open`() {
        // (0,0) → (0,3): intervening (0,1) and (0,2). Only (0,1) is heavy woods.
        // interveningLevels = 2 < 3 → not blocked; targetHexWoods = 0.
        val hexes = mapOf(HexCoordinates(0, 1) to Hex(HexCoordinates(0, 1), Terrain.HEAVY_WOODS))

        val result = los(targetPos = HexCoordinates(0, 3), hexes = hexes)

        assertFalse(result.blocked)
        assertEquals(2, result.woodsModifier)
    }

    @Test
    fun `target in light woods adds one to modifier — endpoint does not affect blocking`() {
        // (0,0) → (0,2): intervening (0,1) is clear; target (0,2) is light woods.
        val targetPos = HexCoordinates(0, 2)
        val hexes = mapOf(targetPos to Hex(targetPos, Terrain.LIGHT_WOODS))

        val result = los(targetPos = targetPos, hexes = hexes)

        assertFalse(result.blocked)
        assertEquals(1, result.woodsModifier)
    }

    @Test
    fun `intervening light woods plus target heavy woods combines modifiers without blocking`() {
        // interveningLevels = 1 (light at (0,1)) → below threshold;
        // targetHexWoods = 2 (heavy at (0,2)); woodsModifier = 3 but not blocked
        // (only intervening hexes count toward blocking).
        val interveningPos = HexCoordinates(0, 1)
        val targetPos = HexCoordinates(0, 2)
        val hexes = mapOf(
            interveningPos to Hex(interveningPos, Terrain.LIGHT_WOODS),
            targetPos to Hex(targetPos, Terrain.HEAVY_WOODS),
        )

        val result = los(targetPos = targetPos, hexes = hexes)

        assertFalse(result.blocked)
        assertEquals(3, result.woodsModifier) // 1 intervening + 2 target hex
    }

    // ── Woods blocking ────────────────────────────────────────────────────────

    @Test
    fun `two intervening heavy woods hexes block LOS — total 4 levels exceeds threshold`() {
        val hex1 = HexCoordinates(0, 1)
        val hex2 = HexCoordinates(0, 2)
        val hexes = mapOf(
            hex1 to Hex(hex1, Terrain.HEAVY_WOODS),
            hex2 to Hex(hex2, Terrain.HEAVY_WOODS),
        )

        val result = los(targetPos = HexCoordinates(0, 3), hexes = hexes)

        assertTrue(result.blocked)
        assertEquals(0, result.woodsModifier) // blocked: no modifier reported
    }

    @Test
    fun `one heavy plus one light intervening woods block LOS — total exactly 3 levels`() {
        val hex1 = HexCoordinates(0, 1)
        val hex2 = HexCoordinates(0, 2)
        val hexes = mapOf(
            hex1 to Hex(hex1, Terrain.HEAVY_WOODS), // 2 levels
            hex2 to Hex(hex2, Terrain.LIGHT_WOODS),  // 1 level → total 3 ≥ 3
        )

        val result = los(targetPos = HexCoordinates(0, 3), hexes = hexes)

        assertTrue(result.blocked)
        assertFalse(result.partialCover)
    }

    // ── Elevation blocking ────────────────────────────────────────────────────

    @Test
    fun `intervening hex higher than both endpoints blocks LOS`() {
        // Attacker and target default to elevation 0; intervening hex at elevation 2.
        val interveningPos = HexCoordinates(0, 1)
        val hexes = mapOf(interveningPos to Hex(interveningPos, Terrain.CLEAR, elevation = 2))

        val result = los(targetPos = HexCoordinates(0, 2), hexes = hexes)

        assertTrue(result.blocked)
        assertEquals(interveningPos, result.blockerHex)
    }

    @Test
    fun `intervening hex higher than target but not attacker does not elevation-block`() {
        // Attacker elevation = 2 (via its hex), intervening = 1, target = 0.
        // hexElev (1) > targetElev (0) but hexElev (1) is NOT > attackerElev (2).
        val attackerPos = HexCoordinates(0, 0)
        val interveningPos = HexCoordinates(0, 1)
        val targetPos = HexCoordinates(0, 2)
        val hexes = mapOf(
            attackerPos to Hex(attackerPos, Terrain.CLEAR, elevation = 2),
            interveningPos to Hex(interveningPos, Terrain.CLEAR, elevation = 1),
        )

        val result = los(attackerPos = attackerPos, targetPos = targetPos, hexes = hexes)

        assertFalse(result.blocked)
    }

    // ── Partial cover ─────────────────────────────────────────────────────────

    @Test
    fun `obstacle one level above target and at attacker elevation creates partial cover`() {
        // Attacker at elevation 1, intervening at elevation 1, target at elevation 0.
        // hexElev (1) > targetElev (0) AND hexElev (1) ≤ attackerElev (1) → partial cover.
        val attackerPos = HexCoordinates(0, 0)
        val interveningPos = HexCoordinates(0, 1)
        val targetPos = HexCoordinates(0, 2)
        val hexes = mapOf(
            attackerPos to Hex(attackerPos, Terrain.CLEAR, elevation = 1),
            interveningPos to Hex(interveningPos, Terrain.CLEAR, elevation = 1),
        )

        val result = los(attackerPos = attackerPos, targetPos = targetPos, hexes = hexes)

        assertFalse(result.blocked)
        assertTrue(result.partialCover)
    }

    @Test
    fun `no partial cover when attacker and target are at the same elevation as obstacle`() {
        // All elevations equal → no obstacle above target.
        val interveningPos = HexCoordinates(0, 1)
        val hexes = mapOf(interveningPos to Hex(interveningPos, Terrain.CLEAR, elevation = 0))

        val result = los(targetPos = HexCoordinates(0, 2), hexes = hexes)

        assertFalse(result.partialCover)
    }

    @Test
    fun `partial cover yields zero woodsModifier when no woods are present`() {
        val attackerPos = HexCoordinates(0, 0)
        val interveningPos = HexCoordinates(0, 1)
        val targetPos = HexCoordinates(0, 2)
        val hexes = mapOf(
            attackerPos to Hex(attackerPos, Terrain.CLEAR, elevation = 1),
            interveningPos to Hex(interveningPos, Terrain.CLEAR, elevation = 1),
        )

        val result = los(attackerPos = attackerPos, targetPos = targetPos, hexes = hexes)

        assertEquals(0, result.woodsModifier) // no woods
        assertTrue(result.partialCover)
    }

    @Test
    fun `adjacent hexes have no intervening hexes so no blocking possible`() {
        // distance 1 → lineTo returns only the two endpoints, intervening is empty.
        val result = los(targetPos = HexCoordinates(0, 1), hexes = emptyMap())

        assertFalse(result.blocked)
        assertFalse(result.partialCover)
        assertEquals(0, result.woodsModifier)
    }
}
