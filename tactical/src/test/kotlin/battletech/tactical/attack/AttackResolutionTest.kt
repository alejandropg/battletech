package battletech.tactical.attack

import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Terrain
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.anArmorLayout
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.query.mediumLaser
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.UnitRoster
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    private val gameState = GameState(UnitRoster(listOf(attacker, target)), GameMap(emptyMap()))

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
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), gameState, roller)

        val result = results.single()
        if (result is AttackResult.Hit) {
            // Damage should have been applied somewhere
            assertTrue(result.damageApplied > 0)
            assertTrue(result.locationHits.isNotEmpty())
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
        val state = gameState.copy(units = UnitRoster(listOf(highGunneryAttacker, target)))

        // Try many seeds until we get a miss
        var foundMiss = false
        for (seed in 0..100) {
            val roller = DiceRoller.seeded(seed.toLong())
            val (newState, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
            val result = results.single()
            if (result is AttackResult.Miss) {
                // Target should be unchanged
                assertEquals(target, newState.units.byId(target.id))
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
        val state = gameState.copy(units = UnitRoster(listOf(attacker, thinTarget)))

        // Find a seed that hits center torso
        for (seed in 0..1000) {
            val roller = DiceRoller.seeded(seed.toLong())
            val declaration = AttackDeclaration(attacker.id, thinTarget.id, 0, true)
            val (newState, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
            val result = results.single()
            if (result is AttackResult.Hit && result.locationHits.first().location == HitLocation.CENTER_TORSO) {
                val updatedTarget = newState.units.byId(thinTarget.id)
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
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), gameState, roller)
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
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), gameState, roller)
        val result = results.single()
        // gunnery 4 + range 0 = 4
        assertEquals(4, result.targetNumber)
    }

    @Test
    fun `heat penalty increases target number`() {
        val hotAttacker = attacker.copy(currentHeat = 16, heatSink = HeatSink(HeatSinkType.STS, 10))
        val state = gameState.copy(units = UnitRoster(listOf(hotAttacker, target)))
        val declaration = AttackDeclaration(hotAttacker.id, target.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
        val result = results.single()
        // gunnery 4 + range 0 + heat penalty (heat 16 → +2) = 6
        assertEquals(6, result.targetNumber)
    }

    @Test
    fun `prone adjacent target lowers the target number by two`() {
        val proneTarget = target.copy(isProne = true)
        val state = gameState.copy(units = UnitRoster(listOf(attacker, proneTarget)))
        val declaration = AttackDeclaration(attacker.id, proneTarget.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
        // gunnery 4 + range 0 - 2 (prone adjacent) = 2
        assertEquals(2, results.single().targetNumber)
    }

    @Test
    fun `shutdown target lowers the target number by four`() {
        val shutdownTarget = target.copy(isShutdown = true)
        val state = gameState.copy(units = UnitRoster(listOf(attacker, shutdownTarget)))
        val declaration = AttackDeclaration(attacker.id, shutdownTarget.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
        // gunnery 4 + range 0 - 4 (immobile) = 0
        assertEquals(0, results.single().targetNumber)
    }

    @Test
    fun `prone target at range raises the target number by one`() {
        val proneFarTarget = target.copy(position = HexCoordinates(5, 0), isProne = true)
        val state = gameState.copy(units = UnitRoster(listOf(attacker, proneFarTarget)))
        val declaration = AttackDeclaration(attacker.id, proneFarTarget.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
        // gunnery 4 + medium range 2 + 1 (prone at range) = 7
        assertEquals(7, results.single().targetNumber)
    }

    @Test
    fun `1 sensor crit raises the target number by two`() {
        // HEAD framework: Sensors at indices 1 and 4 (docs/rules/armor-damage.md §3).
        val blindedAttacker = attacker.copy(
            criticalHits = mapOf(battletech.tactical.model.MechLocation.HEAD to setOf(1)),
        )
        val state = gameState.copy(units = UnitRoster(listOf(blindedAttacker, target)))
        val declaration = AttackDeclaration(blindedAttacker.id, target.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
        val result = results.single()
        // gunnery 4 + range 0 + 2 (1 sensor crit) = 6
        assertEquals(6, result.targetNumber)
        assertEquals(2, result.modifiers.amountOf(ToHitFactor.SENSORS))
    }

    @Test
    fun `medium range adds plus 2 modifier`() {
        val farTarget = target.copy(position = HexCoordinates(5, 0)) // distance 5, medium range for ML
        val state = gameState.copy(units = UnitRoster(listOf(attacker, farTarget)))
        val declaration = AttackDeclaration(attacker.id, farTarget.id, 0, true)

        val roller = DiceRoller.seeded(42)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
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
        val state = GameState(UnitRoster(listOf(attacker, attacker2, target)), GameMap(emptyMap()))
        val declarations = listOf(
            AttackDeclaration(attacker.id, target.id, 0, true),
            AttackDeclaration(attacker2.id, target.id, 0, true),
        )

        val roller = DiceRoller.seeded(42)
        val (_, results, _) = resolveAttacksWithCrits(declarations, state, roller)
        assertEquals(2, results.size)
        assertEquals(attacker.id, results[0].attackerId)
        assertEquals(attacker2.id, results[1].attackerId)
    }

    @Test
    fun `toHitRoll has correct faces and total`() {
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)
        // deterministic: to-hit = 4+5=9 (hit, TN=4), location = 3+4=7 (CENTER_TORSO)
        val roller = DiceRoller.deterministic(4, 5, 3, 4)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), gameState, roller)
        val result = results.single()
        assertEquals(DiceRoll(4, 5), result.toHitRoll)
        assertEquals(9, result.toHitRoll.total)
    }

    @Test
    fun `hit result has non-null locationRoll with correct faces`() {
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)
        val roller = DiceRoller.deterministic(4, 5, 3, 4)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), gameState, roller)
        val result = results.single()
        assertTrue(result is AttackResult.Hit)
        val locationRoll = (result as AttackResult.Hit).locationHits.first().locationRoll
        assertEquals(DiceRoll(3, 4), locationRoll)
        assertEquals(7, locationRoll.total)
    }

    @Test
    fun `miss result has null locationRoll`() {
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)
        // TN = 4, roll = 1+1 = 2 (miss)
        val roller = DiceRoller.deterministic(1, 1)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), gameState, roller)
        val result = results.single()
        assertTrue(result is AttackResult.Miss)
    }

    @Test
    fun `TN breakdown fields correct for short range primary with no heat`() {
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)
        val roller = DiceRoller.deterministic(4, 5, 3, 4)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), gameState, roller)
        val result = results.single()
        assertEquals(4, result.gunnery)
        assertEquals(0, result.modifiers.amountOf(ToHitFactor.RANGE))
        assertEquals(RangeBand.SHORT, result.rangeBand)
        assertEquals(0, result.modifiers.amountOf(ToHitFactor.HEAT))
        assertEquals(0, result.modifiers.amountOf(ToHitFactor.SECONDARY_TARGET))
    }

    @Test
    fun `TN breakdown fields correct for medium range secondary with heat penalty`() {
        val hotAttacker = attacker.copy(currentHeat = 16, heatSink = HeatSink(HeatSinkType.STS, 10))
        val farTarget = target.copy(position = HexCoordinates(5, 0)) // medium range for ML
        val state = gameState.copy(units = UnitRoster(listOf(hotAttacker, farTarget)))
        val declaration = AttackDeclaration(hotAttacker.id, farTarget.id, 0, false)
        // TN = 4+2+2+1=9, roll=1+1=2 (miss, no location roll needed)
        val roller = DiceRoller.deterministic(1, 1)
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
        val result = results.single()
        assertEquals(4, result.gunnery)
        assertEquals(2, result.modifiers.amountOf(ToHitFactor.RANGE))
        assertEquals(RangeBand.MEDIUM, result.rangeBand)
        assertEquals(2, result.modifiers.amountOf(ToHitFactor.HEAT))
        assertEquals(1, result.modifiers.amountOf(ToHitFactor.SECONDARY_TARGET))
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

    @Test
    fun `applyDamage wrapper returns same unit as resolveDamage for a simple armor-only hit`() {
        val viaWrapper = applyDamage(target, HitLocation.CENTER_TORSO, 3)
        val viaResolve = resolveDamage(target, HitLocation.CENTER_TORSO, 3).unit
        assertEquals(viaResolve, viaWrapper)
        assertEquals(7, viaWrapper.armor.centerTorso)
    }

    @Test
    fun `IS absorbs overflow without reaching zero - no transfer`() {
        val unit = aUnit(
            armor = anArmorLayout(leftArm = 2),
            internalStructure = anInternalStructureLayout(leftArm = 10),
        )
        // 5 damage: 2 armor absorbed, 3 to IS, IS 10 -> 7, no destruction, no transfer
        val resolution = resolveDamage(unit, HitLocation.LEFT_ARM, 5)

        assertEquals(0, resolution.unit.armor.leftArm)
        assertEquals(7, resolution.unit.internalStructure.leftArm)
        assertEquals(1, resolution.steps.size)
        val step = resolution.steps.single()
        assertEquals(HitLocation.LEFT_ARM, step.location)
        assertEquals(2, step.armorDamage)
        assertEquals(3, step.structureDamage)
        assertFalse(step.destroyed)
    }

    @Test
    fun `arm destroyed transfers overflow to same-side torso armor first`() {
        val unit = aUnit(
            armor = anArmorLayout(leftArm = 2, leftTorso = 8),
            internalStructure = anInternalStructureLayout(leftArm = 6, leftTorso = 20),
        )
        // 12 damage to left arm: 2 armor, 6 IS (destroyed), excess 4 transfers to left torso armor
        val resolution = resolveDamage(unit, HitLocation.LEFT_ARM, 12)

        assertEquals(0, resolution.unit.armor.leftArm)
        assertEquals(0, resolution.unit.internalStructure.leftArm)
        assertEquals(4, resolution.unit.armor.leftTorso) // 8 - 4
        assertEquals(20, resolution.unit.internalStructure.leftTorso) // untouched

        assertEquals(2, resolution.steps.size)
        val armStep = resolution.steps[0]
        assertEquals(HitLocation.LEFT_ARM, armStep.location)
        assertEquals(2, armStep.armorDamage)
        assertEquals(6, armStep.structureDamage)
        assertTrue(armStep.destroyed)

        val torsoStep = resolution.steps[1]
        assertEquals(HitLocation.LEFT_TORSO, torsoStep.location)
        assertEquals(4, torsoStep.armorDamage)
        assertEquals(0, torsoStep.structureDamage)
        assertFalse(torsoStep.destroyed)
    }

    @Test
    fun `cascade from arm through side torso to center torso when both blow through`() {
        val unit = aUnit(
            armor = anArmorLayout(leftArm = 2, leftTorso = 1, centerTorso = 10),
            internalStructure = anInternalStructureLayout(leftArm = 6, leftTorso = 5, centerTorso = 20),
        )
        // 20 damage to left arm:
        //  arm: 2 armor + 6 IS = 8 absorbed, destroyed, excess 12
        //  left torso: 1 armor + 5 IS = 6 absorbed, destroyed, excess 6
        //  center torso: 6 armor absorbed (10 -> 4), no IS damage
        val resolution = resolveDamage(unit, HitLocation.LEFT_ARM, 20)

        assertEquals(0, resolution.unit.armor.leftArm)
        assertEquals(0, resolution.unit.internalStructure.leftArm)
        assertEquals(0, resolution.unit.armor.leftTorso)
        assertEquals(0, resolution.unit.internalStructure.leftTorso)
        assertEquals(4, resolution.unit.armor.centerTorso) // 10 - 6
        assertEquals(20, resolution.unit.internalStructure.centerTorso) // untouched

        assertEquals(3, resolution.steps.size)
        assertEquals(HitLocation.LEFT_ARM, resolution.steps[0].location)
        assertTrue(resolution.steps[0].destroyed)
        assertEquals(HitLocation.LEFT_TORSO, resolution.steps[1].location)
        assertTrue(resolution.steps[1].destroyed)
        assertEquals(HitLocation.CENTER_TORSO, resolution.steps[2].location)
        assertEquals(6, resolution.steps[2].armorDamage)
        assertEquals(0, resolution.steps[2].structureDamage)
        assertFalse(resolution.steps[2].destroyed)
    }

    @Test
    fun `leg destroyed transfers overflow to same-side torso`() {
        val unit = aUnit(
            armor = anArmorLayout(leftLeg = 1, leftTorso = 10),
            internalStructure = anInternalStructureLayout(leftLeg = 5, leftTorso = 20),
        )
        // 10 damage to left leg: 1 armor + 5 IS = 6 absorbed, destroyed, excess 4 to left torso armor
        val resolution = resolveDamage(unit, HitLocation.LEFT_LEG, 10)

        assertEquals(0, resolution.unit.internalStructure.leftLeg)
        assertEquals(6, resolution.unit.armor.leftTorso) // 10 - 4

        assertEquals(2, resolution.steps.size)
        assertEquals(HitLocation.LEFT_LEG, resolution.steps[0].location)
        assertTrue(resolution.steps[0].destroyed)
        assertEquals(HitLocation.LEFT_TORSO, resolution.steps[1].location)
        assertEquals(4, resolution.steps[1].armorDamage)
    }

    @Test
    fun `right leg destroyed transfers overflow to right torso`() {
        val unit = aUnit(
            armor = anArmorLayout(rightLeg = 1, rightTorso = 10),
            internalStructure = anInternalStructureLayout(rightLeg = 5, rightTorso = 20),
        )
        val resolution = resolveDamage(unit, HitLocation.RIGHT_LEG, 10)

        assertEquals(0, resolution.unit.internalStructure.rightLeg)
        assertEquals(6, resolution.unit.armor.rightTorso)
        assertEquals(2, resolution.steps.size)
        assertEquals(HitLocation.RIGHT_TORSO, resolution.steps[1].location)
    }

    @Test
    fun `side torso destroyed transfers overflow to center torso front armor`() {
        val unit = aUnit(
            armor = anArmorLayout(leftTorso = 2, centerTorso = 15),
            internalStructure = anInternalStructureLayout(leftTorso = 6, centerTorso = 20),
        )
        // 12 damage front to left torso: 2 armor + 6 IS = 8, destroyed, excess 4 to CT front armor
        val resolution = resolveDamage(unit, HitLocation.LEFT_TORSO, 12, useRearArmor = false)

        assertTrue(resolution.steps[0].destroyed)
        assertEquals(HitLocation.CENTER_TORSO, resolution.steps[1].location)
        assertEquals(4, resolution.steps[1].armorDamage)
        assertEquals(11, resolution.unit.armor.centerTorso) // 15 - 4 (front)
        assertEquals(14, resolution.unit.armor.centerTorsoRear) // unchanged
    }

    @Test
    fun `side torso destroyed with rear hit transfers overflow to center torso rear armor`() {
        val unit = aUnit(
            armor = anArmorLayout(leftTorsoRear = 2, centerTorso = 15, centerTorsoRear = 8),
            internalStructure = anInternalStructureLayout(leftTorso = 6, centerTorso = 20),
        )
        // 12 rear damage to left torso: 2 rear armor + 6 IS = 8, destroyed, excess 4 to CT rear armor
        val resolution = resolveDamage(unit, HitLocation.LEFT_TORSO, 12, useRearArmor = true)

        assertTrue(resolution.steps[0].destroyed)
        assertEquals(HitLocation.CENTER_TORSO, resolution.steps[1].location)
        assertEquals(4, resolution.steps[1].armorDamage)
        assertEquals(4, resolution.unit.armor.centerTorsoRear) // 8 - 4
        assertEquals(15, resolution.unit.armor.centerTorso) // unchanged (front untouched)
    }

    @Test
    fun `head overflow destroys location with no transfer and drops excess`() {
        val unit = aUnit(
            armor = anArmorLayout(head = 2),
            internalStructure = anInternalStructureLayout(head = 3),
        )
        // 10 damage to head: 2 armor + 3 IS = 5 absorbed, destroyed, excess 5 dropped
        val resolution = resolveDamage(unit, HitLocation.HEAD, 10)

        assertEquals(0, resolution.unit.armor.head)
        assertEquals(0, resolution.unit.internalStructure.head)
        assertEquals(1, resolution.steps.size)
        val step = resolution.steps.single()
        assertEquals(HitLocation.HEAD, step.location)
        assertTrue(step.destroyed)
    }

    @Test
    fun `center torso overflow destroys location with no transfer`() {
        val unit = aUnit(
            armor = anArmorLayout(centerTorso = 2),
            internalStructure = anInternalStructureLayout(centerTorso = 5),
        )
        // 20 damage to CT: 2 armor + 5 IS = 7 absorbed, destroyed, excess 13 dropped
        val resolution = resolveDamage(unit, HitLocation.CENTER_TORSO, 20)

        assertEquals(0, resolution.unit.armor.centerTorso)
        assertEquals(0, resolution.unit.internalStructure.centerTorso)
        assertEquals(1, resolution.steps.size)
        assertTrue(resolution.steps.single().destroyed)
    }

    @Test
    fun `resolveAttacks integration - hit result carries damage steps`() {
        val unit = aUnit(
            id = "blowthrough-target",
            armor = anArmorLayout(centerTorso = 47, leftArm = 2, leftTorso = 8),
            internalStructure = anInternalStructureLayout(leftArm = 6, leftTorso = 20),
        )
        val state = gameState.copy(units = UnitRoster(listOf(attacker, unit)))

        // Find a seed that hits the left arm.
        for (seed in 0..2000) {
            val roller = DiceRoller.seeded(seed.toLong())
            val declaration = AttackDeclaration(attacker.id, unit.id, 0, true)
            val (newState, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)
            val result = results.single()
            if (result is AttackResult.Hit && result.locationHits.first().location == HitLocation.LEFT_ARM) {
                // Medium laser does 5 damage: 2 armor + 3 IS, no destruction, single step.
                assertTrue(result.damage.isNotEmpty())
                assertEquals(HitLocation.LEFT_ARM, result.damage.first().location)
                val updatedUnit = newState.units.byId(unit.id)
                assertEquals(0, updatedUnit.armor.leftArm)
                assertEquals(3, updatedUnit.internalStructure.leftArm) // 6 - 3
                return
            }
        }
        org.junit.jupiter.api.fail("Should have found a seed that hits LEFT_ARM")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Partial cover — leg hits deal no damage
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sets up a partial-cover scenario:
     *   Attacker at (0,0) elevation 1, target at (0,2) elevation 0,
     *   intervening hex (0,1) at elevation 1 (≤ attacker, > target) → partial cover.
     *
     * The target has thin leg armor (2) so any hit would be detectable.
     * Medium laser (damage 5): short range to (0,2) = distance 2.
     * TN = gunnery(4) + range(0) + terrain(3) = 7.
     */
    private fun partialCoverScenario(): Triple<GameState, HexCoordinates, HexCoordinates> {
        val attackerPos = HexCoordinates(0, 0)
        val targetPos = HexCoordinates(0, 2)
        val interveningPos = HexCoordinates(0, 1)

        val attackerUnit = aUnit(
            id = "attacker",
            position = attackerPos,
            gunnerySkill = 4,
        )
        val targetUnit = aUnit(
            id = "target",
            position = targetPos,
            armor = anArmorLayout(leftLeg = 2, rightLeg = 2),
            internalStructure = anInternalStructureLayout(leftLeg = 10, rightLeg = 10),
        )
        val hexes = mapOf(
            attackerPos to Hex(attackerPos, Terrain.CLEAR, elevation = 1),
            interveningPos to Hex(interveningPos, Terrain.CLEAR, elevation = 1),
        )
        val state = aGameState(units = listOf(attackerUnit, targetUnit), hexes = hexes)
        return Triple(state, attackerPos, targetPos)
    }

    @Test
    fun `partial cover adds 3 to terrain modifier and target number`() {
        val (state, _, _) = partialCoverScenario()
        val pcAttacker = state.units.first { it.id.value == "attacker" }
        val pcTarget = state.units.first { it.id.value == "target" }

        // TN = 4 (gunnery) + 0 (range short) + 3 (terrain / partial cover) = 7
        val declaration = AttackDeclaration(pcAttacker.id, pcTarget.id, 0, true)
        val roller = DiceRoller.deterministic(1, 1) // guaranteed miss; just check TN
        val (_, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)

        assertEquals(7, results.single().targetNumber)
        assertTrue(results.single().modifiers.any { it.label == "terrain" && it.amount == 3 })
    }

    @Test
    fun `partial cover leg hit deals no damage — armor unchanged`() {
        val (state, _, _) = partialCoverScenario()
        val pcAttacker = state.units.first { it.id.value == "attacker" }
        val pcTarget = state.units.first { it.id.value == "target" }

        // TN = 7.  Dice: to-hit = 4+4 = 8 ≥ 7 → hit; location = 2+3 = 5 → RIGHT_LEG.
        // Under partial cover, RIGHT_LEG hit should deal 0 damage.
        val declaration = AttackDeclaration(pcAttacker.id, pcTarget.id, 0, true)
        val roller = DiceRoller.deterministic(4, 4, 2, 3)
        val (newState, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)

        val result = results.single()
        assertTrue(result is AttackResult.Hit)
        assertEquals(HitLocation.RIGHT_LEG, (result as AttackResult.Hit).locationHits.first().location)
        assertTrue(result.partialCover)

        // Leg armor should be unchanged — partial cover suppressed the damage.
        val updatedTarget = newState.units.byId(pcTarget.id)
        assertEquals(2, updatedTarget.armor.rightLeg)  // unchanged from initial 2
        assertEquals(10, updatedTarget.internalStructure.rightLeg) // unchanged
    }

    @Test
    fun `partial cover non-leg hit still applies damage normally`() {
        val (state, _, _) = partialCoverScenario()
        val pcAttacker = state.units.first { it.id.value == "attacker" }
        val pcTarget = state.units.first { it.id.value == "target" }

        // TN = 7.  Dice: to-hit = 4+4 = 8 ≥ 7 → hit; location = 3+4 = 7 → CENTER_TORSO.
        // Center torso is not a leg, so damage should apply normally.
        val declaration = AttackDeclaration(pcAttacker.id, pcTarget.id, 0, true)
        val roller = DiceRoller.deterministic(4, 4, 3, 4)
        val (newState, results, _) = resolveAttacksWithCrits(listOf(declaration), state, roller)

        val result = results.single()
        assertTrue(result is AttackResult.Hit)
        assertEquals(HitLocation.CENTER_TORSO, (result as AttackResult.Hit).locationHits.first().location)
        assertTrue(result.partialCover)

        // Center torso armor should have been reduced by 5 (medium laser damage).
        val initialCT = pcTarget.armor.centerTorso
        val updatedTarget = newState.units.byId(pcTarget.id)
        assertEquals(initialCT - 5, updatedTarget.armor.centerTorso)
    }
}
