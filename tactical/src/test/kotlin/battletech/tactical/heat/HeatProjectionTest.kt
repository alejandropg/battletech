package battletech.tactical.heat

import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MechLocation
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * [projectHeat] is the TUI's end-of-turn heat preview; [applyHeatPhase] is what the Heat Phase
 * actually applies. They used to be two independent implementations of the same formula and had
 * drifted (the preview ignored engine-crit heat and the water dissipation bonus). [applyHeatPhase]
 * now delegates to [projectHeat] directly, which makes the two agree by construction — so this
 * test asserts against a hand-computed expected value instead of only cross-checking the two
 * functions against each other (a delegation makes that comparison tautological: it would pass
 * even if [projectHeat] itself regressed back to ignoring engine heat or water, since
 * [applyHeatPhase] would silently inherit the same mistake).
 *
 * heatSink is STS 10 (`aUnit`'s default) throughout, so `dissipation = 10 + waterBonus`.
 * ENGINE_CRIT_HEAT_PER_HIT is 5/crit (`CriticalLayout.kt`); `submersionDissipationBonus` is
 * +6 at depth 1, +12 at depth 2+ (`heat/WaterDissipation.kt`).
 */
internal class HeatProjectionTest {

    private val dryMap: GameMap = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0))))

    private fun waterMap(depth: Int): GameMap =
        GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0), depth = depth)))

    private fun heatAfterPhase(unit: CombatUnit, map: GameMap): Int =
        aGameState(units = listOf(unit), hexes = map.hexes).applyHeatPhase().units.byId(unit.id).currentHeat

    /** Asserts both [projectHeat] and [applyHeatPhase] resolve to [expected] — not just each other. */
    private fun assertBothResolveTo(expected: Int, unit: CombatUnit, map: GameMap) {
        assertEquals(expected, projectHeat(unit, map).projected, "projectHeat().projected")
        assertEquals(expected, heatAfterPhase(unit, map), "applyHeatPhase()'s resolved currentHeat")
    }

    @Test
    fun `dry hex, no engine crits, no committed heat`() {
        // 20 + 0 generated - 10 dissipation = 10
        assertBothResolveTo(10, aUnit(currentHeat = 20), dryMap)
    }

    @Test
    fun `dry hex, no engine crits, some committed heat`() {
        // 20 + 1 generated - 10 dissipation = 11
        val unit = aUnit(currentHeat = 20).copy(heatGeneratedThisTurn = listOf(HeatSource("Walking", 1)))
        assertBothResolveTo(11, unit, dryMap)
    }

    @Test
    fun `dry hex, one engine crit, no committed heat`() {
        // 20 + 5 (1 engine crit) generated - 10 dissipation = 15
        val unit = aUnit(currentHeat = 20).copy(criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0)))
        assertBothResolveTo(15, unit, dryMap)
    }

    @Test
    fun `dry hex, two engine crits, some committed heat`() {
        // 20 + (10 engine + 3 weapons) generated - 10 dissipation = 23
        val unit = aUnit(currentHeat = 20)
            .copy(
                criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0, 1)),
                heatGeneratedThisTurn = listOf(HeatSource("Weapons", 3)),
            )
        assertBothResolveTo(23, unit, dryMap)
    }

    @Test
    fun `depth-1 water, no engine crits, no committed heat`() {
        // 20 + 0 generated - (10 + 6 water) dissipation = 4
        assertBothResolveTo(4, aUnit(currentHeat = 20), waterMap(1))
    }

    @Test
    fun `depth-1 water, one engine crit, some committed heat`() {
        // 20 + (5 engine + 3 weapons) generated - (10 + 6 water) dissipation = 12
        val unit = aUnit(currentHeat = 20)
            .copy(
                criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0)),
                heatGeneratedThisTurn = listOf(HeatSource("Weapons", 3)),
            )
        assertBothResolveTo(12, unit, waterMap(1))
    }

    @Test
    fun `depth-2 water, no engine crits, no committed heat`() {
        // 20 + 0 generated - (10 + 12 water) dissipation, floored at 0
        assertBothResolveTo(0, aUnit(currentHeat = 20), waterMap(2))
    }

    @Test
    fun `depth-2 water, two engine crits, some committed heat`() {
        // 20 + (10 engine + 3 weapons) generated - (10 + 12 water) dissipation = 11
        val unit = aUnit(currentHeat = 20)
            .copy(
                criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0, 1)),
                heatGeneratedThisTurn = listOf(HeatSource("Weapons", 3)),
            )
        assertBothResolveTo(11, unit, waterMap(2))
    }
}
