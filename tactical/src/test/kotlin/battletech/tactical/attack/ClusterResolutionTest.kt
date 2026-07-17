package battletech.tactical.attack

import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Hex
import battletech.tactical.model.Terrain
import battletech.tactical.query.aUnit
import battletech.tactical.query.anArmorLayout
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.WeaponModels
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for cluster-hit weapon resolution (SRM, LRM) and confirming that
 * non-cluster weapons (Medium Laser) preserve the exact dice order they had
 * before Task 3.
 *
 * Dice order (canonical, documented in [resolveAttacksWithCrits]):
 *   1. to-hit 2d6
 *   2a. (hit, non-cluster) location 2d6
 *   2b. (hit, cluster)     cluster-count 2d6, then one location 2d6 per group (in group order),
 *                          then per-group crit dice for any groups that dealt IS damage
 */
internal class ClusterResolutionTest {

    /**
     * Attacker at (0,0) with gunnery 2.  Default weapons are replaced per test.
     * Target at (1,0) is used for SRM tests (distance 1 ≤ SRM short range 3).
     * A separate [lrmTarget] at (7,0) is used for LRM tests so the LRM minimum-range
     * penalty (minimumRange=6) is 0 at distance 7, and range band is SHORT.
     */
    private val attacker = aUnit(id = "attacker", gunnerySkill = 2, position = HexCoordinates(0, 0))
    private val target = aUnit(
        id = "target",
        position = HexCoordinates(1, 0), // distance 1 → inside SRM short range, no penalty
        facing = FiringArc.bearingDirection(HexCoordinates(1, 0), HexCoordinates(0, 0)),
        armor = anArmorLayout(
            head = 9,
            centerTorso = 100, centerTorsoRear = 30,
            leftTorso = 100, leftTorsoRear = 30,
            rightTorso = 100, rightTorsoRear = 30,
            leftArm = 100, rightArm = 100,
            leftLeg = 100, rightLeg = 100,
        ),
        internalStructure = anInternalStructureLayout(
            head = 3, centerTorso = 50, leftTorso = 50, rightTorso = 50,
            leftArm = 50, rightArm = 50, leftLeg = 50, rightLeg = 50,
        ),
    )
    // LRM-20: minimumRange=6, shortRange=7. At distance 7: minRangeMod=0, rangeMod=0, TN=2.
    private val lrmTarget = target.copy(
        id = battletech.tactical.unit.UnitId("target"),
        position = HexCoordinates(7, 0),
        facing = FiringArc.bearingDirection(HexCoordinates(7, 0), HexCoordinates(0, 0)),
    )

    private val gameState = GameState(listOf(attacker, target), GameMap(emptyMap()))
    private fun lrmState(attUnit: battletech.tactical.unit.CombatUnit): GameState =
        GameState(listOf(attUnit, lrmTarget), GameMap(emptyMap()))

    // ────────────────────────────────────────────────────────────────────────
    // Energy weapon (Medium Laser) — single location, dice order unchanged
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `medium laser produces exactly one location hit`() {
        val att = attacker.copy(weapons = listOf(Weapon(WeaponModels.mediumLaser)))
        val state = gameState.copy(units = listOf(att, target))
        val decl = AttackDeclaration(att.id, target.id, 0, true)
        // Dice: to-hit (4,4)=8 ≥ TN 2, location (3,4)=7 → CENTER_TORSO
        val roller = DiceRoller.deterministic(4, 4, 3, 4)
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.SingleHit)
        result as AttackResult.SingleHit
        assertEquals(HitLocation.CENTER_TORSO, result.locationHits.first().location)
        assertEquals(DiceRoll(3, 4), result.locationHits.first().locationRoll)
        assertEquals(5, result.damageApplied)
        assertEquals(1, result.locationHits.size)
        val lh = result.locationHits.single()
        assertEquals(HitLocation.CENTER_TORSO, lh.location)
        assertEquals(5, lh.damage)
        assertEquals(DiceRoll(3, 4), lh.locationRoll)
    }

    @Test
    fun `medium laser miss produces empty locationHits`() {
        val highGunneryAtt = attacker.copy(gunnerySkill = 8, weapons = listOf(Weapon(WeaponModels.mediumLaser)))
        val state = gameState.copy(units = listOf(highGunneryAtt, target))
        val decl = AttackDeclaration(highGunneryAtt.id, target.id, 0, true)
        // TN = 8, roll (1,1)=2 → miss; no location roll consumed
        val roller = DiceRoller.deterministic(1, 1)
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.Miss)
    }

    @Test
    fun `medium laser dice order unchanged — deterministic roller produces identical result`() {
        // Pins the dice order: to-hit(4,5)=9, location(3,4)=7 → CENTER_TORSO.
        // Stream must be exactly [4, 5, 3, 4] — no extra rolls consumed.
        val att = attacker.copy(weapons = listOf(Weapon(WeaponModels.mediumLaser)))
        val state = gameState.copy(units = listOf(att, target))
        val decl = AttackDeclaration(att.id, target.id, 0, true)
        val roller = DiceRoller.deterministic(4, 5, 3, 4)
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertEquals(DiceRoll(4, 5), result.toHitRoll)
        assertTrue(result is AttackResult.SingleHit)
        result as AttackResult.SingleHit
        assertEquals(DiceRoll(3, 4), result.locationHits.first().locationRoll)
        assertEquals(HitLocation.CENTER_TORSO, result.locationHits.first().location)
        assertEquals(5, result.damageApplied)
    }

    // ────────────────────────────────────────────────────────────────────────
    // SRM-6 cluster resolution
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `SRM-6 miss produces no locationHits`() {
        val att = attacker.copy(gunnerySkill = 8, weapons = listOf(Weapon(WeaponModels.srm6)))
        val state = gameState.copy(units = listOf(att, target))
        val decl = AttackDeclaration(att.id, target.id, 0, true)
        // TN = 8, roll (1,1)=2 → miss; no cluster or location rolls consumed
        val roller = DiceRoller.deterministic(1, 1)
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.Miss)
    }

    @Test
    fun `SRM-6 hit produces one LocationHit per missile`() {
        // Dice order:
        //   to-hit:   (3,3)=6  ≥ TN 2 → HIT
        //   cluster:  (6,5)=11 → size 6 roll 11 → 6 missiles (full salvo)
        //   loc 1:    (3,4)=7 → CENTER_TORSO   (2 dmg)
        //   loc 2:    (4,4)=8 → LEFT_TORSO     (2 dmg)
        //   loc 3:    (5,5)=10 → LEFT_ARM       (2 dmg)
        //   loc 4:    (1,2)=3 → RIGHT_ARM       (2 dmg)
        //   loc 5:    (2,2)=4 → RIGHT_ARM       (2 dmg)
        //   loc 6:    (5,6)=11 → LEFT_ARM       (2 dmg)
        // Total = 12 damage; target has heavy armor so no IS damage → no crit dice needed.
        val att = attacker.copy(gunnerySkill = 2, weapons = listOf(Weapon(WeaponModels.srm6)))
        val state = gameState.copy(units = listOf(att, target))
        val decl = AttackDeclaration(att.id, target.id, 0, true)
        val roller = DiceRoller.deterministic(
            3, 3,    // to-hit = 6 ≥ 2 → HIT
            6, 5,    // cluster roll = 11 → 6 missiles
            3, 4,    // loc 1 = 7 → CENTER_TORSO
            4, 4,    // loc 2 = 8 → LEFT_TORSO
            5, 5,    // loc 3 = 10 → LEFT_ARM
            1, 2,    // loc 4 = 3 → RIGHT_ARM
            2, 2,    // loc 5 = 4 → RIGHT_ARM
            5, 6,    // loc 6 = 11 → LEFT_ARM
        )
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.ClusterHit)
        result as AttackResult.ClusterHit
        assertEquals(6, result.missilesHit)
        assertEquals(6, result.locationHits.size)
        assertEquals(12, result.damageApplied)

        // Each group = 1 missile × 2 dmg/missile = 2 dmg
        result.locationHits.forEach { assertEquals(2, it.damage) }

        assertEquals(HitLocation.CENTER_TORSO, result.locationHits[0].location)
        assertEquals(HitLocation.LEFT_TORSO,   result.locationHits[1].location)
        assertEquals(HitLocation.LEFT_ARM,     result.locationHits[2].location)
        assertEquals(HitLocation.RIGHT_ARM,    result.locationHits[3].location)
        assertEquals(HitLocation.RIGHT_ARM,    result.locationHits[4].location)
        assertEquals(HitLocation.LEFT_ARM,     result.locationHits[5].location)

        // First group mirrors the legacy scalar fields
        assertEquals(HitLocation.CENTER_TORSO, result.locationHits.first().location)
        assertEquals(DiceRoll(3, 4), result.locationHits.first().locationRoll)
    }

    @Test
    fun `SRM-6 with partial cluster roll produces fewer locationHits`() {
        // cluster (1,1)=2 → size 6 roll 2 → 2 missiles
        val att = attacker.copy(gunnerySkill = 2, weapons = listOf(Weapon(WeaponModels.srm6)))
        val state = gameState.copy(units = listOf(att, target))
        val decl = AttackDeclaration(att.id, target.id, 0, true)
        val roller = DiceRoller.deterministic(
            3, 3,   // to-hit = 6 ≥ 2 → HIT
            1, 1,   // cluster roll = 2 → size 6 → 2 missiles
            3, 4,   // loc 1 = 7 → CENTER_TORSO
            4, 4,   // loc 2 = 8 → LEFT_TORSO
        )
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.ClusterHit)
        result as AttackResult.ClusterHit
        assertEquals(2, result.missilesHit)
        assertEquals(2, result.locationHits.size)
        assertEquals(4, result.damageApplied)
    }

    @Test
    fun `SRM-6 applies damage to all group locations on the evolving unit`() {
        // 4 missiles (cluster roll = 6 → size 6 → 4 missiles), each 2 dmg.
        // Target has thin armor (CT=3, LT=3) so loc 3 and loc 4 will breach armor → IS damage → crit checks.
        //
        // Dice order after loc rolls:
        //   loc 1 (CT, 2 dmg): armor 3→1; no IS → no crit
        //   loc 2 (LT, 2 dmg): armor 3→1; no IS → no crit
        //   loc 3 (CT, 2 dmg): armor 1→0 + 1 IS → crit check (2d6) → (3,3)=6 → no crit
        //   loc 4 (LT, 2 dmg): armor 1→0 + 1 IS → crit check (2d6) → (3,3)=6 → no crit
        val thinTarget = target.copy(
            armor = anArmorLayout(centerTorso = 3, leftTorso = 3),
            internalStructure = anInternalStructureLayout(centerTorso = 20, leftTorso = 20),
        )
        val att = attacker.copy(gunnerySkill = 2, weapons = listOf(Weapon(WeaponModels.srm6)))
        val state = gameState.copy(units = listOf(att, thinTarget))
        val decl = AttackDeclaration(att.id, thinTarget.id, 0, true)
        val roller = DiceRoller.deterministic(
            3, 3,   // to-hit = 6 ≥ 2 → HIT
            3, 3,   // cluster roll = 6 → size 6 → 4 missiles
            3, 4,   // loc 1 = 7 → CENTER_TORSO
            4, 4,   // loc 2 = 8 → LEFT_TORSO
            3, 4,   // loc 3 = 7 → CENTER_TORSO
            4, 4,   // loc 4 = 8 → LEFT_TORSO
            3, 3,   // crit check for loc 3 IS damage → 6 → no crit
            3, 3,   // crit check for loc 4 IS damage → 6 → no crit
        )
        val (newState, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.ClusterHit)
        result as AttackResult.ClusterHit
        assertEquals(4, result.missilesHit)
        assertEquals(8, result.damageApplied)
        assertTrue(result.damage.isNotEmpty())

        val updatedTarget = newState.unitById(thinTarget.id)!!
        // CT: 3→1 (loc 1) → 1-2 = 0 armor + 1 IS (loc 3)
        assertEquals(0, updatedTarget.armor.centerTorso)
        assertEquals(19, updatedTarget.internalStructure.centerTorso)
        // LT: 3→1 (loc 2) → 1-2 = 0 armor + 1 IS (loc 4)
        assertEquals(0, updatedTarget.armor.leftTorso)
        assertEquals(19, updatedTarget.internalStructure.leftTorso)
    }

    // ────────────────────────────────────────────────────────────────────────
    // LRM-20 cluster resolution
    //
    // LRM-20: minimumRange=6, shortRange=7.  Tests use lrmTarget at distance 7:
    //   minRangeMod = 0, rangeMod = 0, TN = gunnery(2) + 0 + 0 = 2.
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `LRM-20 hit with 16 missiles produces 4 groups (3 full + 1 partial)`() {
        // cluster (4,5)=9 → size 20 roll 9 → 16 missiles
        // Groups: 16 / 5 = 3 full + 1 remainder → [5, 5, 5, 1]
        // Target has heavy armor → no IS damage → no crit dice needed.
        val att = attacker.copy(gunnerySkill = 2, weapons = listOf(Weapon(WeaponModels.lrm20)))
        val state = lrmState(att)
        val decl = AttackDeclaration(att.id, lrmTarget.id, 0, true)
        val roller = DiceRoller.deterministic(
            3, 3,   // to-hit = 6 ≥ 2 → HIT
            4, 5,   // cluster roll = 9 → 16 missiles
            3, 4,   // loc 1 = 7 → CENTER_TORSO  (5 dmg)
            4, 4,   // loc 2 = 8 → LEFT_TORSO    (5 dmg)
            5, 5,   // loc 3 = 10 → LEFT_ARM      (5 dmg)
            1, 2,   // loc 4 = 3 → RIGHT_ARM      (1 dmg)
        )
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.ClusterHit)
        result as AttackResult.ClusterHit
        assertEquals(16, result.missilesHit)
        assertEquals(4, result.locationHits.size)
        assertEquals(16, result.damageApplied) // 5+5+5+1

        assertEquals(5, result.locationHits[0].damage)
        assertEquals(5, result.locationHits[1].damage)
        assertEquals(5, result.locationHits[2].damage)
        assertEquals(1, result.locationHits[3].damage)

        assertEquals(HitLocation.CENTER_TORSO, result.locationHits[0].location)
        assertEquals(HitLocation.LEFT_TORSO,   result.locationHits[1].location)
        assertEquals(HitLocation.LEFT_ARM,     result.locationHits[2].location)
        assertEquals(HitLocation.RIGHT_ARM,    result.locationHits[3].location)

        assertEquals(DiceRoll(3, 4), result.locationHits[0].locationRoll)
        assertEquals(DiceRoll(4, 4), result.locationHits[1].locationRoll)
        assertEquals(DiceRoll(5, 5), result.locationHits[2].locationRoll)
        assertEquals(DiceRoll(1, 2), result.locationHits[3].locationRoll)

        // First group mirrors the legacy scalar fields
        assertEquals(HitLocation.CENTER_TORSO, result.locationHits.first().location)
        assertEquals(DiceRoll(3, 4), result.locationHits.first().locationRoll)
    }

    @Test
    fun `LRM-20 full salvo (20 missiles) produces 4 groups of 5`() {
        // cluster (6,5)=11 → size 20 roll 11 → 20 missiles
        // Groups: 20 / 5 = 4 full groups → [5, 5, 5, 5]
        val att = attacker.copy(gunnerySkill = 2, weapons = listOf(Weapon(WeaponModels.lrm20)))
        val state = lrmState(att)
        val decl = AttackDeclaration(att.id, lrmTarget.id, 0, true)
        val roller = DiceRoller.deterministic(
            3, 3,   // to-hit = 6 ≥ 2 → HIT
            6, 5,   // cluster roll = 11 → 20 missiles
            3, 4,   // loc 1 = 7 → CENTER_TORSO
            4, 4,   // loc 2 = 8 → LEFT_TORSO
            5, 5,   // loc 3 = 10 → LEFT_ARM
            1, 3,   // loc 4 = 4 → RIGHT_ARM
        )
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.ClusterHit)
        result as AttackResult.ClusterHit
        assertEquals(20, result.missilesHit)
        assertEquals(4, result.locationHits.size)
        assertEquals(20, result.damageApplied)
        result.locationHits.forEach { assertEquals(5, it.damage) }
    }

    @Test
    fun `LRM-20 minimum roll (6 missiles) produces one full group and one partial`() {
        // cluster (1,1)=2 → size 20 roll 2 → 6 missiles
        // Groups: 6 / 5 = 1 full + 1 remainder → [5, 1]
        val att = attacker.copy(gunnerySkill = 2, weapons = listOf(Weapon(WeaponModels.lrm20)))
        val state = lrmState(att)
        val decl = AttackDeclaration(att.id, lrmTarget.id, 0, true)
        val roller = DiceRoller.deterministic(
            3, 3,   // to-hit = 6 ≥ 2 → HIT
            1, 1,   // cluster roll = 2 → 6 missiles
            3, 4,   // loc 1 = 7 → CENTER_TORSO  (5 dmg)
            4, 4,   // loc 2 = 8 → LEFT_TORSO    (1 dmg)
        )
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.ClusterHit)
        result as AttackResult.ClusterHit
        assertEquals(6, result.missilesHit)
        assertEquals(2, result.locationHits.size)
        assertEquals(5, result.locationHits[0].damage)
        assertEquals(1, result.locationHits[1].damage)
        assertEquals(6, result.damageApplied)
    }

    @Test
    fun `LRM-20 applies damage across all groups on the evolving unit`() {
        // 16 missiles (cluster roll 9): groups [5, 5, 5, 1], all hitting CENTER_TORSO.
        // CT armor=8, IS=20.
        //   loc 1 (CT, 5): armor 8→3; no IS → no crit
        //   loc 2 (CT, 5): armor 3→0 + 2 IS → crit check (3,3)=6 → no crit
        //   loc 3 (CT, 5): armor 0→0 + 5 IS → crit check (3,3)=6 → no crit
        //   loc 4 (CT, 1): armor 0→0 + 1 IS → crit check (3,3)=6 → no crit
        // Final CT IS = 20 - (2+5+1) = 12.
        val thinTarget = lrmTarget.copy(
            armor = anArmorLayout(centerTorso = 8),
            internalStructure = anInternalStructureLayout(centerTorso = 20),
        )
        val att = attacker.copy(gunnerySkill = 2, weapons = listOf(Weapon(WeaponModels.lrm20)))
        val state = GameState(listOf(att, thinTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(att.id, thinTarget.id, 0, true)
        val roller = DiceRoller.deterministic(
            3, 3,   // to-hit = 6 ≥ 2 → HIT
            4, 5,   // cluster roll = 9 → 16 missiles
            3, 4,   // loc 1 = 7 → CT (5 dmg: armor 8→3)
            3, 4,   // loc 2 = 7 → CT (5 dmg: armor 3→0 + 2 IS)
            3, 4,   // loc 3 = 7 → CT (5 dmg: armor 0 + 5 IS)
            3, 4,   // loc 4 = 7 → CT (1 dmg: armor 0 + 1 IS)
            3, 3,   // crit check for loc 2 IS damage → 6 → no crit
            3, 3,   // crit check for loc 3 IS damage → 6 → no crit
            3, 3,   // crit check for loc 4 IS damage → 6 → no crit
        )
        val (newState, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.Hit)
        assertEquals(16, (result as AttackResult.Hit).damageApplied)

        val updatedTarget = newState.unitById(thinTarget.id)!!
        assertEquals(0, updatedTarget.armor.centerTorso)
        assertEquals(12, updatedTarget.internalStructure.centerTorso) // 20 - 8 = 12
    }

    // ────────────────────────────────────────────────────────────────────────
    // Partial cover with cluster weapons
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `cluster weapon partial cover suppresses leg-location groups but not others`() {
        // Partial-cover scenario: attacker elev 1, target elev 0, intervening hex elev 1.
        // → partialCover=true, terrain modifier +3 → TN = gunnery(2) + 0 + 3 = 5.
        val attackerPos = HexCoordinates(0, 0)
        val targetPos = HexCoordinates(0, 2)
        val interveningPos = HexCoordinates(0, 1)
        val hexes = mapOf(
            attackerPos to Hex(attackerPos, Terrain.CLEAR, elevation = 1),
            interveningPos to Hex(interveningPos, Terrain.CLEAR, elevation = 1),
        )
        val pcAttacker = aUnit(id = "attacker", position = attackerPos, gunnerySkill = 2,
            weapons = listOf(Weapon(WeaponModels.srm6)))
        val pcTarget = aUnit(
            id = "target",
            position = targetPos,
            armor = anArmorLayout(leftLeg = 10, rightLeg = 10, centerTorso = 20, leftTorso = 32),
            internalStructure = anInternalStructureLayout(
                leftLeg = 10, rightLeg = 10, centerTorso = 20, leftTorso = 20),
        )
        val pcState = GameState(listOf(pcAttacker, pcTarget), battletech.tactical.model.GameMap(hexes))
        val decl = AttackDeclaration(pcAttacker.id, pcTarget.id, 0, true)

        // cluster (3,3)=6 → size 6 roll 6 → 4 missiles
        //   loc 1 = 9 → LEFT_LEG     → suppressed (partial cover)
        //   loc 2 = 7 → CENTER_TORSO → 2 dmg; armor 20→18 (no IS) → no crit
        //   loc 3 = 5 → RIGHT_LEG    → suppressed (partial cover)
        //   loc 4 = 8 → LEFT_TORSO   → 2 dmg; armor 32→30 (no IS) → no crit
        val roller = DiceRoller.deterministic(
            3, 3,   // to-hit = 6 ≥ 5 → HIT
            3, 3,   // cluster roll = 6 → 4 missiles
            4, 5,   // loc 1 = 9 → LEFT_LEG     (suppressed)
            3, 4,   // loc 2 = 7 → CENTER_TORSO (2 dmg)
            2, 3,   // loc 3 = 5 → RIGHT_LEG    (suppressed)
            4, 4,   // loc 4 = 8 → LEFT_TORSO   (2 dmg)
        )
        val (newState, results, _) = resolveAttacksWithCrits(listOf(decl), pcState, roller)
        val result = results.single()

        assertTrue(result is AttackResult.ClusterHit)
        result as AttackResult.ClusterHit
        assertTrue(result.partialCover)
        assertEquals(4, result.missilesHit)
        assertEquals(4, result.locationHits.size)

        val updatedTarget = newState.unitById(pcTarget.id)!!
        // Leg hits suppressed — leg armor unchanged
        assertEquals(10, updatedTarget.armor.leftLeg)
        assertEquals(10, updatedTarget.armor.rightLeg)
        // Non-leg hits applied normally
        assertEquals(18, updatedTarget.armor.centerTorso)  // 20 - 2
        assertEquals(30, updatedTarget.armor.leftTorso)    // 32 - 2
    }

    // ────────────────────────────────────────────────────────────────────────
    // SRM-2
    // ────────────────────────────────────────────────────────────────────────

    @Test
    fun `SRM-2 full roll (2 missiles) produces 2 groups of 2 dmg`() {
        // cluster (6,5)=11 → size 2 roll 11 → 2 missiles
        val att = attacker.copy(gunnerySkill = 2, weapons = listOf(Weapon(WeaponModels.srm2)))
        val state = gameState.copy(units = listOf(att, target))
        val decl = AttackDeclaration(att.id, target.id, 0, true)
        val roller = DiceRoller.deterministic(
            3, 3,   // to-hit = 6 ≥ 2 → HIT
            6, 5,   // cluster roll = 11 → size 2 → 2 missiles
            3, 4,   // loc 1 = 7 → CENTER_TORSO
            4, 4,   // loc 2 = 8 → LEFT_TORSO
        )
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.ClusterHit)
        result as AttackResult.ClusterHit
        assertEquals(2, result.missilesHit)
        assertEquals(2, result.locationHits.size)
        result.locationHits.forEach { assertEquals(2, it.damage) }
        assertEquals(4, result.damageApplied)
    }

    @Test
    fun `SRM-2 minimum roll (1 missile) produces 1 group of 2 dmg`() {
        // cluster (1,1)=2 → size 2 roll 2 → 1 missile
        val att = attacker.copy(gunnerySkill = 2, weapons = listOf(Weapon(WeaponModels.srm2)))
        val state = gameState.copy(units = listOf(att, target))
        val decl = AttackDeclaration(att.id, target.id, 0, true)
        val roller = DiceRoller.deterministic(
            3, 3,   // to-hit = 6 ≥ 2 → HIT
            1, 1,   // cluster roll = 2 → size 2 → 1 missile
            3, 4,   // loc 1 = 7 → CENTER_TORSO
        )
        val (_, results, _) = resolveAttacksWithCrits(listOf(decl), state, roller)
        val result = results.single()

        assertTrue(result is AttackResult.ClusterHit)
        result as AttackResult.ClusterHit
        assertEquals(1, result.missilesHit)
        assertEquals(1, result.locationHits.size)
        assertEquals(2, result.locationHits.single().damage)
        assertEquals(2, result.damageApplied)
    }
}
