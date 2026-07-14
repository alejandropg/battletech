package battletech.tactical.attack

import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Terrain
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.aWeapon
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.WeaponModels
import battletech.tactical.unit.Weapon
import battletech.tactical.dice.DiceRoller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for the three new to-hit modifiers added in Task 1:
 * attacker movement, target movement (TMM), and minimum range.
 */
internal class WeaponToHitModifiersTest {

    private val attackerPos = HexCoordinates(0, 0)
    private val targetPos = HexCoordinates(1, 0) // distance 1

    private fun baseAttacker(movement: MovementThisTurn = MovementThisTurn.Stationary) =
        aUnit(id = "attacker", gunnerySkill = 4, position = attackerPos)
            .copy(movementThisTurn = movement)

    private fun baseTarget(
        movement: MovementThisTurn = MovementThisTurn.Stationary,
        position: HexCoordinates = targetPos,
    ) = aUnit(id = "target", position = position).copy(movementThisTurn = movement)

    private fun modifiersFor(
        attacker: battletech.tactical.unit.CombatUnit = baseAttacker(),
        target: battletech.tactical.unit.CombatUnit = baseTarget(),
        weapon: Weapon = aWeapon(),
        distance: Int = attacker.position.distanceTo(target.position),
        gameState: GameState = aGameState(),
    ) = weaponToHitModifiers(attacker, target, weapon, distance, isPrimaryTarget = true, gameState = gameState)

    // -------------------------------------------------------------------------
    // Attacker movement modifier
    // -------------------------------------------------------------------------

    @Test
    fun `stationary attacker contributes zero to attacker move modifier`() {
        val mods = modifiersFor(attacker = baseAttacker(MovementThisTurn.Stationary))

        assertEquals(0, mods.first { it.label == "attacker move" }.amount)
    }

    @Test
    fun `running attacker adds two to attacker move modifier`() {
        val mods = modifiersFor(attacker = baseAttacker(MovementThisTurn.Moved(MovementMode.RUN, 5)))

        assertEquals(2, mods.first { it.label == "attacker move" }.amount)
    }

    @Test
    fun `jumping attacker adds three to attacker move modifier`() {
        val mods = modifiersFor(attacker = baseAttacker(MovementThisTurn.Moved(MovementMode.JUMP, 3)))

        assertEquals(3, mods.first { it.label == "attacker move" }.amount)
    }

    // -------------------------------------------------------------------------
    // Target movement modifier (TMM)
    // -------------------------------------------------------------------------

    @Test
    fun `stationary target contributes zero to target move modifier`() {
        val mods = modifiersFor(target = baseTarget(MovementThisTurn.Stationary))

        assertEquals(0, mods.first { it.label == "target move" }.amount)
    }

    @Test
    fun `target that ran five hexes adds two to target move modifier`() {
        val mods = modifiersFor(target = baseTarget(MovementThisTurn.Moved(MovementMode.RUN, 5)))

        assertEquals(2, mods.first { it.label == "target move" }.amount)
    }

    @Test
    fun `target that jumped four hexes adds two to target move modifier`() {
        // 4 hexes → band +1, jump bonus +1 = +2
        val mods = modifiersFor(target = baseTarget(MovementThisTurn.Moved(MovementMode.JUMP, 4)))

        assertEquals(2, mods.first { it.label == "target move" }.amount)
    }

    // -------------------------------------------------------------------------
    // Minimum range modifier
    // -------------------------------------------------------------------------

    @Test
    fun `weapon at or beyond minimum range adds zero min range penalty`() {
        // PPC min range 3, distance 3 → at min range, no penalty
        val ppc = Weapon(model = WeaponModels.ppc)
        val target = baseTarget(position = HexCoordinates(3, 0))

        val mods = modifiersFor(target = target, weapon = ppc, distance = 3)

        assertEquals(0, mods.first { it.label == "min range" }.amount)
    }

    @Test
    fun `PPC at distance one inside min range three adds three`() {
        // minimumRange 3, distance 1 → 3 - 1 + 1 = 3
        val ppc = Weapon(model = WeaponModels.ppc)

        val mods = modifiersFor(weapon = ppc, distance = 1)

        assertEquals(3, mods.first { it.label == "min range" }.amount)
    }

    @Test
    fun `LRM at distance two inside min range six adds five`() {
        // minimumRange 6, distance 2 → 6 - 2 + 1 = 5
        val lrm = Weapon(model = WeaponModels.lrm5)

        val mods = modifiersFor(weapon = lrm, distance = 2)

        assertEquals(5, mods.first { it.label == "min range" }.amount)
    }

    @Test
    fun `weapon with no minimum range never adds min range penalty`() {
        // Medium laser has minimumRange 0 — never penalised at any distance
        val ml = Weapon(model = WeaponModels.mediumLaser)

        val mods = modifiersFor(weapon = ml, distance = 1)

        assertEquals(0, mods.first { it.label == "min range" }.amount)
    }

    // -------------------------------------------------------------------------
    // Combined stacking case — asserts final targetNumber
    // -------------------------------------------------------------------------

    @Test
    fun `combined min range plus TMM plus range bracket stacks correctly into target number`() {
        // Setup: LRM-5 (min range 6, short range 7)
        //   attacker: gunnery 4, stationary
        //   target: ran 5 hexes (TMM +2), position at distance 2
        //   distance 2 < short range 7 → range = short → +0
        //   min range penalty: 6 - 2 + 1 = +5
        //   TMM: +2
        //   attacker move: +0
        //   Expected TN = 4 + 0 (range short) + 0 (heat) + 0 (secondary) + 0 (prone) + 0 (immobile)
        //                   + 0 (sensors) + 0 (attacker move) + 2 (target move) + 5 (min range) = 11
        val lrm = Weapon(model = WeaponModels.lrm5)
        val attacker = baseAttacker(MovementThisTurn.Stationary)
        val target = baseTarget(
            movement = MovementThisTurn.Moved(MovementMode.RUN, 5),
            position = HexCoordinates(2, 0),
        )

        val mods = modifiersFor(attacker = attacker, target = target, weapon = lrm, distance = 2)
        val targetNumber = attacker.gunnerySkill + mods.total()

        assertEquals(2, mods.first { it.label == "target move" }.amount)
        assertEquals(5, mods.first { it.label == "min range" }.amount)
        assertEquals(0, mods.first { it.label == "attacker move" }.amount)
        assertEquals(11, targetNumber)
    }

    // -------------------------------------------------------------------------
    // Integration: modifiers appear on AttackResult
    // -------------------------------------------------------------------------

    @Test
    fun `attack result carries attacker move target move and min range fields`() {
        val lrm = Weapon(model = WeaponModels.lrm5)
        val attacker = baseAttacker(MovementThisTurn.Moved(MovementMode.RUN, 5))
            .copy(weapons = listOf(lrm))
        val target = baseTarget(
            movement = MovementThisTurn.Moved(MovementMode.JUMP, 4),
            position = HexCoordinates(2, 0),
        )
        val state = aGameState(units = listOf(attacker, target))
        val declaration = AttackDeclaration(
            attackerId = attacker.id,
            targetId = target.id,
            weaponIndex = 0,
            isPrimary = true,
        )

        val roller = DiceRoller.deterministic(1, 1) // guaranteed miss (roll = 2), no location roll needed
        val (_, results) = resolveAttacks(listOf(declaration), state, roller)
        val result = results.single()

        // attacker ran → +2
        assertEquals(2, result.attackerMoveModifier)
        // target jumped 4 hexes → band +1, jump +1 = +2
        assertEquals(2, result.targetMoveModifier)
        // LRM min range 6, distance 2 → 6 - 2 + 1 = 5
        assertEquals(5, result.minRangeModifier)
    }

    // -------------------------------------------------------------------------
    // Terrain modifier
    // -------------------------------------------------------------------------

    @Test
    fun `target in light woods adds one to terrain modifier`() {
        // Attacker (0,0), target (0,1): adjacent, no intervening hexes.
        // Target hex is LIGHT_WOODS → targetHexWoods = 1.
        val tPos = HexCoordinates(0, 1)
        val hexes = mapOf(tPos to Hex(tPos, Terrain.LIGHT_WOODS))
        val target = baseTarget(position = tPos)

        val mods = modifiersFor(target = target, gameState = aGameState(hexes = hexes))

        assertEquals(1, mods.first { it.label == "terrain" }.amount)
    }

    @Test
    fun `one intervening heavy woods adds two to terrain modifier without blocking`() {
        // Attacker (0,0), target (0,3): intervening (0,1) and (0,2).
        // (0,1) = HEAVY_WOODS → 2 levels < 3 → open; woodsModifier = 2.
        val tPos = HexCoordinates(0, 3)
        val iPos = HexCoordinates(0, 1)
        val hexes = mapOf(iPos to Hex(iPos, Terrain.HEAVY_WOODS))
        val attacker = baseAttacker()
        val target = baseTarget(position = tPos)

        val mods = modifiersFor(
            attacker = attacker,
            target = target,
            distance = attacker.position.distanceTo(target.position),
            gameState = aGameState(hexes = hexes),
        )

        assertEquals(2, mods.first { it.label == "terrain" }.amount)
    }

    @Test
    fun `partial cover adds three to terrain modifier`() {
        // Attacker at elevation 1 (0,0), intervening hex (0,1) at elevation 1, target (0,2) at elevation 0.
        // Partial cover: obstacle one level above target and at/below attacker.
        val aPos = HexCoordinates(0, 0)
        val iPos = HexCoordinates(0, 1)
        val tPos = HexCoordinates(0, 2)
        val hexes = mapOf(
            aPos to Hex(aPos, Terrain.CLEAR, elevation = 1),
            iPos to Hex(iPos, Terrain.CLEAR, elevation = 1),
        )
        val attacker = baseAttacker().copy(position = aPos)
        val target = baseTarget(position = tPos)

        val mods = modifiersFor(
            attacker = attacker,
            target = target,
            distance = aPos.distanceTo(tPos),
            gameState = aGameState(hexes = hexes),
        )

        // 0 (no woods) + 3 (partial cover) = 3
        assertEquals(3, mods.first { it.label == "terrain" }.amount)
    }

    @Test
    fun `no terrain modifier with empty map and clear hexes`() {
        val mods = modifiersFor()

        assertEquals(0, mods.first { it.label == "terrain" }.amount)
    }
}
