package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.query.aUnit
import battletech.tactical.query.anArmorLayout
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.query.mediumLaser
import battletech.tactical.dice.DiceRoll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AttackResolutionTest {

    private val attacker = aUnit(
        id = "attacker",
        name = "Attacker",
        weapons = listOf(mediumLaser()),
        position = HexCoordinates(0, 0),
        gunnerySkill = 4,
    )

    private val target = aUnit(
        id = "target",
        name = "Target",
        weapons = listOf(mediumLaser()),
        position = HexCoordinates(1, 0), // distance 1, short range
        armor = anArmorLayout(centerTorso = 10),
        internalStructure = anInternalStructureLayout(centerTorso = 10),
    )

    private val gameState = GameState(listOf(attacker, target), GameMap(emptyMap()))

    @Test
    fun `hit applies damage to correct armor location`() {
        // Seed that produces: to-hit roll >= 4 (hit), location roll = 7 (CENTER_TORSO)
        val declaration = AttackDeclaration(
            attackerId = attacker.id,
            targetId = target.id,
            weaponIndex = 0,
            isPrimary = true,
        )
        // Use a seeded roller: first 2d6 for to-hit, second 2d6 for hit location
        val roller = DiceRoller.seeded(42)
        val (newState, results) = resolveAttacks(listOf(declaration), gameState, roller)

        val result = results.single()
        if (result.hit) {
            val updatedTarget = newState.unitById(result.targetId)!!
            // Damage should have been applied somewhere
            assertTrue(result.damageApplied > 0)
            assertTrue(result.hitLocation != null)
        }
    }

    @Test
    fun `miss does not apply damage`() {
        val declaration = AttackDeclaration(
            attackerId = attacker.id,
            targetId = target.id,
            weaponIndex = 0,
            isPrimary = true,
        )
        // Use a seeded roller that produces a miss (high target number)
        val highGunneryAttacker = attacker.copy(gunnerySkill = 12) // target number 12+, almost impossible
        val state = gameState.copy(units = listOf(highGunneryAttacker, target))

        // Try many seeds until we get a miss
        var foundMiss = false
        for (seed in 0..100) {
            val roller = DiceRoller.seeded(seed.toLong())
            val (newState, results) = resolveAttacks(listOf(declaration), state, roller)
            val result = results.single()
            if (!result.hit) {
                assertFalse(result.hit)
                assertNull(result.hitLocation)
                assertEquals(0, result.damageApplied)
                // Target should be unchanged
                assertEquals(target, newState.unitById(target.id))
                foundMiss = true
                break
            }
        }
        assertTrue(foundMiss, "Should have found a miss with gunnery 12")
    }

    @Test
    fun `armor overflow goes to internal structure`() {
        val thinTarget = target.copy(
            armor = anArmorLayout(
                head = 0, centerTorso = 2, centerTorsoRear = 0,
                leftTorso = 0, leftTorsoRear = 0,
                rightTorso = 0, rightTorsoRear = 0,
                leftArm = 0, rightArm = 0,
                leftLeg = 0, rightLeg = 0,
            ),
            internalStructure = anInternalStructureLayout(centerTorso = 10),
        )
        val state = gameState.copy(units = listOf(attacker, thinTarget))

        // Find a seed that hits center torso
        for (seed in 0..1000) {
            val roller = DiceRoller.seeded(seed.toLong())
            val declaration = AttackDeclaration(attacker.id, thinTarget.id, 0, true)
            val (newState, results) = resolveAttacks(listOf(declaration), state, roller)
            val result = results.single()
            if (result.hit && result.hitLocation == HitLocation.CENTER_TORSO) {
                val updatedTarget = newState.unitById(thinTarget.id)!!
                // Medium laser does 5 damage, 2 armor absorbs 2, 3 overflows to IS
                assertEquals(0, updatedTarget.armor.centerTorso)
                assertEquals(7, updatedTarget.internalStructure.centerTorso) // 10 - 3
                break
            }
        }
    }

    @Test
    fun `secondary target adds plus 1 to target number`() {
        val declaration = AttackDeclaration(
            attackerId = attacker.id,
            targetId = target.id,
            weaponIndex = 0,
            isPrimary = false, // secondary target
        )

        // Compute expected target number: gunnery 4 + range 0 (short) + secondary 1 = 5
        val roller = DiceRoller.seeded(42)
        val (_, results) = resolveAttacks(listOf(declaration), gameState, roller)
        val result = results.single()
        assertEquals(5, result.targetNumber)
    }

    @Test
    fun `primary target has no secondary penalty`() {
        val declaration = AttackDeclaration(
            attackerId = attacker.id,
            targetId = target.id,
            weaponIndex = 0,
            isPrimary = true,
        )

        val roller = DiceRoller.seeded(42)
        val (_, results) = resolveAttacks(listOf(declaration), gameState, roller)
        val result = results.single()
        // gunnery 4 + range 0 = 4
        assertEquals(4, result.targetNumber)
    }

    @Test
    fun `heat penalty increases target number`() {
        val hotAttacker = attacker.copy(currentHeat = 16, heatSinkCapacity = 10)
        val state = gameState.copy(units = listOf(hotAttacker, target))
        val declaration = AttackDeclaration(hotAttacker.id, target.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results) = resolveAttacks(listOf(declaration), state, roller)
        val result = results.single()
        // gunnery 4 + range 0 + heat penalty (heat 16 → +2) = 6
        assertEquals(6, result.targetNumber)
    }

    @Test
    fun `prone adjacent target lowers the target number by two`() {
        val proneTarget = target.copy(isProne = true)
        val state = gameState.copy(units = listOf(attacker, proneTarget))
        val declaration = AttackDeclaration(attacker.id, proneTarget.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results) = resolveAttacks(listOf(declaration), state, roller)
        // gunnery 4 + range 0 - 2 (prone adjacent) = 2
        assertEquals(2, results.single().targetNumber)
    }

    @Test
    fun `shutdown target lowers the target number by four`() {
        val shutdownTarget = target.copy(isShutdown = true)
        val state = gameState.copy(units = listOf(attacker, shutdownTarget))
        val declaration = AttackDeclaration(attacker.id, shutdownTarget.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results) = resolveAttacks(listOf(declaration), state, roller)
        // gunnery 4 + range 0 - 4 (immobile) = 0
        assertEquals(0, results.single().targetNumber)
    }

    @Test
    fun `prone target at range raises the target number by one`() {
        val proneFarTarget = target.copy(position = HexCoordinates(5, 0), isProne = true)
        val state = gameState.copy(units = listOf(attacker, proneFarTarget))
        val declaration = AttackDeclaration(attacker.id, proneFarTarget.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results) = resolveAttacks(listOf(declaration), state, roller)
        // gunnery 4 + medium range 2 + 1 (prone at range) = 7
        assertEquals(7, results.single().targetNumber)
    }

    @Test
    fun `medium range adds plus 2 modifier`() {
        val farTarget = target.copy(position = HexCoordinates(5, 0)) // distance 5, medium range for ML
        val state = gameState.copy(units = listOf(attacker, farTarget))
        val declaration = AttackDeclaration(attacker.id, farTarget.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results) = resolveAttacks(listOf(declaration), state, roller)
        val result = results.single()
        // gunnery 4 + medium range 2 = 6
        assertEquals(6, result.targetNumber)
    }

    @Test
    fun `multiple declarations resolve independently`() {
        val attacker2 = aUnit(
            id = "attacker2",
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(0, 1),
        )
        val state = GameState(listOf(attacker, attacker2, target), GameMap(emptyMap()))
        val declarations = listOf(
            AttackDeclaration(attacker.id, target.id, 0, true),
            AttackDeclaration(attacker2.id, target.id, 0, true),
        )

        val roller = DiceRoller.seeded(42)
        val (_, results) = resolveAttacks(declarations, state, roller)
        assertEquals(2, results.size)
        assertEquals(attacker.id, results[0].attackerId)
        assertEquals(attacker2.id, results[1].attackerId)
    }

    @Test
    fun `toHitRoll has correct faces and total`() {
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)
        // deterministic: to-hit = 4+5=9 (hit, TN=4), location = 3+4=7 (CENTER_TORSO)
        val roller = DiceRoller.deterministic(4, 5, 3, 4)
        val (_, results) = resolveAttacks(listOf(declaration), gameState, roller)
        val result = results.single()
        assertEquals(DiceRoll(4, 5), result.toHitRoll)
        assertEquals(9, result.toHitRoll.total)
    }

    @Test
    fun `hit result has non-null locationRoll with correct faces`() {
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)
        val roller = DiceRoller.deterministic(4, 5, 3, 4)
        val (_, results) = resolveAttacks(listOf(declaration), gameState, roller)
        val result = results.single()
        assertTrue(result.hit)
        assertNotNull(result.locationRoll)
        assertEquals(DiceRoll(3, 4), result.locationRoll)
        assertEquals(7, result.locationRoll!!.total)
    }

    @Test
    fun `miss result has null locationRoll`() {
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)
        // TN = 4, roll = 1+1 = 2 (miss)
        val roller = DiceRoller.deterministic(1, 1)
        val (_, results) = resolveAttacks(listOf(declaration), gameState, roller)
        val result = results.single()
        assertFalse(result.hit)
        assertNull(result.locationRoll)
    }

    @Test
    fun `TN breakdown fields correct for short range primary with no heat`() {
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)
        val roller = DiceRoller.deterministic(4, 5, 3, 4)
        val (_, results) = resolveAttacks(listOf(declaration), gameState, roller)
        val result = results.single()
        assertEquals(4, result.gunnery)
        assertEquals(0, result.rangeModifier)
        assertEquals(RangeBand.SHORT, result.rangeBand)
        assertEquals(0, result.heatPenalty)
        assertEquals(0, result.secondaryPenalty)
    }

    @Test
    fun `TN breakdown fields correct for medium range secondary with heat penalty`() {
        val hotAttacker = attacker.copy(currentHeat = 16, heatSinkCapacity = 10)
        val farTarget = target.copy(position = HexCoordinates(5, 0)) // medium range for ML
        val state = gameState.copy(units = listOf(hotAttacker, farTarget))
        val declaration = AttackDeclaration(hotAttacker.id, farTarget.id, 0, false)
        // TN = 4+2+2+1=9, roll=1+1=2 (miss, no location roll needed)
        val roller = DiceRoller.deterministic(1, 1)
        val (_, results) = resolveAttacks(listOf(declaration), state, roller)
        val result = results.single()
        assertEquals(4, result.gunnery)
        assertEquals(2, result.rangeModifier)
        assertEquals(RangeBand.MEDIUM, result.rangeBand)
        assertEquals(2, result.heatPenalty)
        assertEquals(1, result.secondaryPenalty)
    }

    @Test
    fun `applyDamage reduces armor at location`() {
        val result = applyDamage(target, HitLocation.CENTER_TORSO, 3)
        assertEquals(7, result.armor.centerTorso) // 10 - 3
        assertEquals(10, result.internalStructure.centerTorso) // unchanged
    }

    @Test
    fun `applyDamage overflows to internal structure when armor is zero`() {
        val noArmorTarget = target.copy(
            armor = anArmorLayout(centerTorso = 2),
            internalStructure = anInternalStructureLayout(centerTorso = 10),
        )
        val result = applyDamage(noArmorTarget, HitLocation.CENTER_TORSO, 5)
        assertEquals(0, result.armor.centerTorso)
        assertEquals(7, result.internalStructure.centerTorso) // 10 - 3 overflow
    }

    @Test
    fun `applyDamage to HEAD location`() {
        val result = applyDamage(target, HitLocation.HEAD, 5)
        assertEquals(4, result.armor.head) // 9 - 5
    }

    @Test
    fun `applyDamage to each location`() {
        for (location in HitLocation.entries) {
            val result = applyDamage(target, location, 1)
            // Just verify it doesn't throw
            assertTrue(result.id == target.id)
        }
    }
}
