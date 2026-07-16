package battletech.tactical.attack

import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.attack.weapon.SubmergedWeaponRule
import battletech.tactical.dice.DiceRoller
import battletech.tactical.heat.applyHeatPhase
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MechLocation
import battletech.tactical.model.Terrain
import battletech.tactical.model.unitWaterDepth
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.aWeapon
import battletech.tactical.query.anArmorLayout
import battletech.tactical.session.HeatPhaseHandler
import battletech.tactical.session.PilotHit
import battletech.tactical.session.TurnState
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.WeaponKind
import battletech.tactical.unit.WeaponModel
import battletech.tactical.unit.Weapon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for all water and depth effects:
 *   - depth-1 partial cover (+3 to-hit, leg hits suppressed) via [lineOfSight]
 *   - depth-2 weapon restriction via [SubmergedWeaponRule]
 *   - submerged heat-sink dissipation bonus via [battletech.tactical.heat.GameStateHeatTransform]
 *   - drowning (prone in depth-2 water → 1 pilot hit per Heat Phase) via [HeatPhaseHandler]
 *   - [unitWaterDepth] shared query
 */
internal class WaterDepthEffectsTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun depth1Hex(pos: HexCoordinates) = Hex(pos, Terrain.CLEAR, elevation = 0, depth = 1)
    private fun depth2Hex(pos: HexCoordinates) = Hex(pos, Terrain.CLEAR, elevation = 0, depth = 2)

    // ── unitWaterDepth shared query ───────────────────────────────────────────

    @Test
    fun `unitWaterDepth returns 0 when hex is absent from map`() {
        val unit = aUnit(position = HexCoordinates(5, 5))
        val gameState = aGameState(units = listOf(unit), hexes = emptyMap())
        assertEquals(0, unitWaterDepth(unit.position, gameState.map))
    }

    @Test
    fun `unitWaterDepth returns depth from hex`() {
        val pos = HexCoordinates(2, 3)
        val unit = aUnit(position = pos)
        val gameState = aGameState(units = listOf(unit), hexes = mapOf(pos to depth2Hex(pos)))
        assertEquals(2, unitWaterDepth(unit.position, gameState.map))
    }

    // ── Depth-1 water: partial cover ─────────────────────────────────────────

    @Test
    fun `target in depth-1 water gives +3 terrain modifier from partial cover`() {
        val attackerPos = HexCoordinates(0, 0)
        val targetPos = HexCoordinates(1, 0)
        val attacker = aUnit(id = "attacker", gunnerySkill = 4, position = attackerPos)
        val target = aUnit(id = "target", position = targetPos)
        val weapon = aWeapon()
        val gameState = aGameState(
            units = listOf(attacker, target),
            hexes = mapOf(targetPos to depth1Hex(targetPos)),
        )

        val mods = weaponToHitModifiers(attacker, target, weapon, 1, isPrimaryTarget = true, gameState.map)

        // partialCover = true → woodsModifier 0 + partialCover +3 = 3
        assertEquals(3, mods.first { it.label == "terrain" }.amount)
    }

    @Test
    fun `target in depth-1 water is flagged as partial cover in LOS result`() {
        val attackerPos = HexCoordinates(0, 0)
        val targetPos = HexCoordinates(1, 0)
        val attacker = aUnit(id = "attacker", position = attackerPos)
        val target = aUnit(id = "target", position = targetPos)
        val gameState = aGameState(
            units = listOf(attacker, target),
            hexes = mapOf(targetPos to depth1Hex(targetPos)),
        )

        val result = lineOfSight(attacker.position, target.position, gameState.map)

        assertTrue(result.partialCover)
        assertFalse(result.blocked)
    }

    @Test
    fun `target in depth-2 water does NOT get partial cover (fully submerged)`() {
        // Depth-2 = fully submerged; partial cover only applies at depth-1.
        val attackerPos = HexCoordinates(0, 0)
        val targetPos = HexCoordinates(1, 0)
        val attacker = aUnit(id = "attacker", position = attackerPos)
        val target = aUnit(id = "target", position = targetPos)
        val gameState = aGameState(
            units = listOf(attacker, target),
            hexes = mapOf(targetPos to depth2Hex(targetPos)),
        )

        val result = lineOfSight(attacker.position, target.position, gameState.map)

        assertFalse(result.partialCover)
    }

    @Test
    fun `a leg hit against a depth-1 water target deals no armor damage`() {
        // Gunnery 2 + short range 0 + terrain +3 = target 5.
        // Seeded dice: to-hit 2+3=5 (exact hit), location 3+2=5 (RIGHT_LEG).
        // The leg hit must be suppressed by partial cover.
        val attackerPos = HexCoordinates(0, 0)
        val targetPos = HexCoordinates(1, 0)
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 2,
            position = attackerPos,
        )
        val initialRightLeg = 15
        val target = aUnit(
            id = "target",
            position = targetPos,
            facing = FiringArc.bearingDirection(targetPos, attackerPos),
            armor = anArmorLayout(rightLeg = initialRightLeg),
        )
        val gameState = aGameState(
            units = listOf(attacker, target),
            hexes = mapOf(targetPos to depth1Hex(targetPos)),
        )
        val declaration = AttackDeclaration(
            attackerId = attacker.id,
            targetId = target.id,
            weaponIndex = 0,
            isPrimary = true,
        )

        // dice order: to-hit 2d6 (d1=2, d2=3 → sum 5 = target 5, hit), location 2d6 (d1=3, d2=2 → sum 5 = RIGHT_LEG)
        val roller = DiceRoller.deterministic(2, 3, 3, 2)
        val (newState, results) = resolveAttacks(listOf(declaration), gameState, roller)

        val result = results.single()
        assertTrue(result is AttackResult.Hit)
        assertEquals(MechLocation.RIGHT_LEG, (result as AttackResult.Hit).locationHits.first().location)
        assertTrue(result.partialCover)

        // Leg hit is suppressed: right leg armor must be unchanged.
        val updatedTarget = newState.unitById(target.id)!!
        assertEquals(initialRightLeg, updatedTarget.armor.rightLeg)
    }

    @Test
    fun `a non-leg hit against a depth-1 water target does deal damage normally`() {
        // Same setup as above but location roll 7 → CENTER_TORSO (not suppressed).
        val attackerPos = HexCoordinates(0, 0)
        val targetPos = HexCoordinates(1, 0)
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 2,
            position = attackerPos,
        )
        val initialCt = 20
        val target = aUnit(
            id = "target",
            position = targetPos,
            facing = FiringArc.bearingDirection(targetPos, attackerPos),
            armor = anArmorLayout(centerTorso = initialCt),
        )
        val gameState = aGameState(
            units = listOf(attacker, target),
            hexes = mapOf(targetPos to depth1Hex(targetPos)),
        )
        val declaration = AttackDeclaration(
            attackerId = attacker.id,
            targetId = target.id,
            weaponIndex = 0,
            isPrimary = true,
        )

        // to-hit 2+3=5 (hit), location 4+3=7 (CENTER_TORSO)
        val roller = DiceRoller.deterministic(2, 3, 4, 3)
        val (newState, results) = resolveAttacks(listOf(declaration), gameState, roller)

        val result = results.single()
        assertTrue(result is AttackResult.Hit)
        assertEquals(MechLocation.CENTER_TORSO, (result as AttackResult.Hit).locationHits.first().location)
        assertTrue(result.partialCover)

        // CT is NOT a leg — damage applies normally. Weapon damage = 5.
        val updatedTarget = newState.unitById(target.id)!!
        assertEquals(initialCt - 5, updatedTarget.armor.centerTorso)
    }

    // ── Depth-2: weapon restriction ──────────────────────────────────────────

    @Test
    fun `SubmergedWeaponRule blocks surface weapon when attacker is at depth 2`() {
        val pos = HexCoordinates(0, 0)
        val attacker = aUnit(id = "attacker", position = pos)
        val target = aUnit(id = "target", position = HexCoordinates(1, 0))
        val weapon = aWeapon() // underwaterCapable = false by default
        val gameState = aGameState(
            units = listOf(attacker, target),
            hexes = mapOf(pos to depth2Hex(pos)),
        )
        val context = aWeaponAttackContext(actor = attacker, gameState = gameState, target = target, weapon = weapon)

        val result = SubmergedWeaponRule().evaluate(context)

        assertThat(result).isInstanceOf(battletech.tactical.query.RuleResult.Unsatisfied::class.java)
        assertThat((result as battletech.tactical.query.RuleResult.Unsatisfied).reason)
            .isInstanceOf(battletech.tactical.session.RuleRejection.AttackerSubmerged::class.java)
        assertEquals(2, (result.reason as battletech.tactical.session.RuleRejection.AttackerSubmerged).depth)
    }

    @Test
    fun `SubmergedWeaponRule allows surface weapon at depth 1 (legs only submerged)`() {
        val pos = HexCoordinates(0, 0)
        val attacker = aUnit(id = "attacker", position = pos)
        val target = aUnit(id = "target", position = HexCoordinates(1, 0))
        val weapon = aWeapon() // underwaterCapable = false
        val gameState = aGameState(
            units = listOf(attacker, target),
            hexes = mapOf(pos to depth1Hex(pos)),
        )
        val context = aWeaponAttackContext(actor = attacker, gameState = gameState, target = target, weapon = weapon)

        val result = SubmergedWeaponRule().evaluate(context)

        assertThat(result).isEqualTo(battletech.tactical.query.RuleResult.Satisfied)
    }

    @Test
    fun `SubmergedWeaponRule allows underwater-capable weapon at depth 2`() {
        val pos = HexCoordinates(0, 0)
        val attacker = aUnit(id = "attacker", position = pos)
        val target = aUnit(id = "target", position = HexCoordinates(1, 0))
        // Weapon marked as underwaterCapable = true
        val underwaterWeapon = Weapon(
            model = WeaponModel(
                name = "UW Torpedo",
                damage = 6,
                heat = 2,
                shortRange = 3,
                mediumRange = 6,
                longRange = 9,
                kind = WeaponKind.Energy,
                underwaterCapable = true,
            ),
        )
        val gameState = aGameState(
            units = listOf(attacker, target),
            hexes = mapOf(pos to depth2Hex(pos)),
        )
        val context = aWeaponAttackContext(actor = attacker, gameState = gameState, target = target, weapon = underwaterWeapon)

        val result = SubmergedWeaponRule().evaluate(context)

        assertThat(result).isEqualTo(battletech.tactical.query.RuleResult.Satisfied)
    }

    @Test
    fun `SubmergedWeaponRule allows weapon when attacker is on dry land`() {
        val attacker = aUnit(id = "attacker", position = HexCoordinates(0, 0))
        val target = aUnit(id = "target", position = HexCoordinates(1, 0))
        val weapon = aWeapon()
        val gameState = aGameState(units = listOf(attacker, target)) // no hexes = no water
        val context = aWeaponAttackContext(actor = attacker, gameState = gameState, target = target, weapon = weapon)

        val result = SubmergedWeaponRule().evaluate(context)

        assertThat(result).isEqualTo(battletech.tactical.query.RuleResult.Satisfied)
    }

    // ── Submerged heat-sink dissipation bonus ─────────────────────────────────

    @Test
    fun `unit in depth-1 water dissipates 6 extra heat`() {
        // currentHeat = 20, base dissipation = 10, depth-1 bonus = 6
        // expected: max(0, 20 - 10 - 6) = 4
        val pos = HexCoordinates(0, 0)
        val unit = aUnit(
            position = pos,
            currentHeat = 20,
            heatSink = HeatSink(HeatSinkType.STS, 10),
        )
        val gameState = aGameState(
            units = listOf(unit),
            hexes = mapOf(pos to depth1Hex(pos)),
        )

        val folded = gameState.applyHeatPhase()

        assertEquals(4, folded.units[0].currentHeat)
    }

    @Test
    fun `unit in depth-2 water dissipates 12 extra heat`() {
        // currentHeat = 20, base dissipation = 10, depth-2 bonus = 12
        // expected: max(0, 20 - 10 - 12) = 0
        val pos = HexCoordinates(0, 0)
        val unit = aUnit(
            position = pos,
            currentHeat = 20,
            heatSink = HeatSink(HeatSinkType.STS, 10),
        )
        val gameState = aGameState(
            units = listOf(unit),
            hexes = mapOf(pos to depth2Hex(pos)),
        )

        val folded = gameState.applyHeatPhase()

        assertEquals(0, folded.units[0].currentHeat)
    }

    @Test
    fun `unit on dry land receives no water dissipation bonus`() {
        // currentHeat = 20, base dissipation = 10, no bonus
        // expected: max(0, 20 - 10) = 10
        val pos = HexCoordinates(0, 0)
        val unit = aUnit(
            position = pos,
            currentHeat = 20,
            heatSink = HeatSink(HeatSinkType.STS, 10),
        )
        val gameState = aGameState(units = listOf(unit), hexes = emptyMap())

        val folded = gameState.applyHeatPhase()

        assertEquals(10, folded.units[0].currentHeat)
    }

    @Test
    fun `water dissipation bonus does not drive heat below zero`() {
        // currentHeat = 5, base dissipation = 10, depth-2 bonus = 12
        // expected: max(0, 5 - 10 - 12) = 0
        val pos = HexCoordinates(0, 0)
        val unit = aUnit(
            position = pos,
            currentHeat = 5,
            heatSink = HeatSink(HeatSinkType.STS, 10),
        )
        val gameState = aGameState(
            units = listOf(unit),
            hexes = mapOf(pos to depth2Hex(pos)),
        )

        val folded = gameState.applyHeatPhase()

        assertEquals(0, folded.units[0].currentHeat)
    }

    // ── Drowning: prone unit in depth-2+ water ────────────────────────────────

    private val heatHandler = HeatPhaseHandler()

    private fun runHeatPhase(unit: battletech.tactical.unit.CombatUnit, gameState: battletech.tactical.model.GameState, roller: DiceRoller) =
        heatHandler.onEntry(gameState, TurnState.NULL, roller)

    @Test
    fun `prone unit in depth-2 water takes 1 pilot hit per heat phase`() {
        // Unit: prone, depth-2, 0 heat → no power/ammo dice consumed.
        // Drowning: applyPilotHit → consciousness check 2d6.
        // pilotHits 0+1=1, target 3; roll (1,1)=2 < 3 → knocked unconscious.
        val pos = HexCoordinates(0, 0)
        val unit = aUnit(
            position = pos,
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
        ).copy(isProne = true)
        val gameState = aGameState(
            units = listOf(unit),
            hexes = mapOf(pos to depth2Hex(pos)),
        )

        // Dice: consciousness check d6(1)+d6(1) = 2 < 3 → fails
        val outcome = runHeatPhase(unit, gameState, DiceRoller.deterministic(1, 1))

        assertEquals(1, outcome.state.units[0].pilotHits)
        assertFalse(outcome.state.units[0].isPilotConscious)
        assertThat(outcome.events).anyMatch { it is PilotHit.Checked && it.pilotHits == 1 }
    }

    @Test
    fun `standing (not prone) unit in depth-2 water does NOT drown`() {
        // Only prone units drown; a standing unit in deep water keeps fighting.
        val pos = HexCoordinates(0, 0)
        val unit = aUnit(
            position = pos,
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
        ) // isProne = false by default
        val gameState = aGameState(
            units = listOf(unit),
            hexes = mapOf(pos to depth2Hex(pos)),
        )

        // No dice consumed (no heat effects, no drowning).
        val outcome = runHeatPhase(unit, gameState, DiceRoller.deterministic())

        assertEquals(0, outcome.state.units[0].pilotHits)
        assertTrue(outcome.state.units[0].isPilotConscious)
        assertThat(outcome.events).noneMatch { it is PilotHit }
    }

    @Test
    fun `prone unit in depth-1 water does NOT drown (drowning requires depth-2)`() {
        val pos = HexCoordinates(0, 0)
        val unit = aUnit(
            position = pos,
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
        ).copy(isProne = true)
        val gameState = aGameState(
            units = listOf(unit),
            hexes = mapOf(pos to depth1Hex(pos)),
        )

        val outcome = runHeatPhase(unit, gameState, DiceRoller.deterministic())

        assertEquals(0, outcome.state.units[0].pilotHits)
        assertThat(outcome.events).noneMatch { it is PilotHit }
    }

    @Test
    fun `prone unit in dry land does NOT drown`() {
        val pos = HexCoordinates(0, 0)
        val unit = aUnit(
            position = pos,
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
        ).copy(isProne = true)
        val gameState = aGameState(units = listOf(unit), hexes = emptyMap())

        val outcome = runHeatPhase(unit, gameState, DiceRoller.deterministic())

        assertEquals(0, outcome.state.units[0].pilotHits)
        assertThat(outcome.events).noneMatch { it is PilotHit }
    }

    @Test
    fun `repeated drowning accumulates pilot hits until PILOT_DEAD`() {
        // Run the heat phase 6 times on a prone depth-2 unit.
        // Each turn: 1 pilot hit (consciousness check dice consumed per hit).
        // After 5 hits: consciousness targets 3,5,7,10,11 — all passed with d6(6)+d6(6)=12.
        // After 6th hit: PILOT_DEATH_THRESHOLD reached, no consciousness roll (dice not consumed).
        // Dice consumed per hit round (when pilot stays conscious): d6+d6.
        // After 5 hits (each passing with 6,6): pilot is at 5 hits, conscious.
        // 6th hit: pilotHits 5+1=6 >= PILOT_DEATH_THRESHOLD → dead, no dice consumed.
        val pos = HexCoordinates(0, 0)
        var unit = aUnit(
            position = pos,
            currentHeat = 0,
            heatSink = HeatSink(HeatSinkType.STS, 0),
        ).copy(isProne = true)
        var gameState = aGameState(
            units = listOf(unit),
            hexes = mapOf(pos to depth2Hex(pos)),
        )

        // 5 turns of drowning, each passing the consciousness check (roll 6+6=12).
        // Dice order: 5 rolls × 2d6 = 10 dice, all 6.
        // 6th turn: no dice (pilot dies without a roll).
        val passingDice = List(10) { 6 } // 5 × 2d6, each 6+6=12
        val roller = DiceRoller.deterministic(passingDice)
        repeat(5) {
            val outcome = heatHandler.onEntry(gameState, TurnState.NULL, roller)
            unit = outcome.state.units[0]
            gameState = gameState.copy(units = listOf(unit))
        }
        assertEquals(5, unit.pilotHits)
        assertTrue(unit.isPilotConscious)

        // 6th turn: reaches PILOT_DEATH_THRESHOLD, no consciousness roll dice.
        val finalOutcome = heatHandler.onEntry(gameState, TurnState.NULL, DiceRoller.deterministic())
        val finalUnit = finalOutcome.state.units[0]
        assertEquals(6, finalUnit.pilotHits)
        // PilotHit is still emitted, as the fatal variant (no consciousness roll).
        assertThat(finalOutcome.events).anyMatch { it is PilotHit.Fatal && it.pilotHits == 6 }
    }
}
