package battletech.tactical.attack

import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.attack.weapon.HasAmmoRule
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MechLocation
import battletech.tactical.query.RuleResult
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.anArmorLayout
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.WeaponModels
import battletech.tactical.unit.mechLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * Tests for Task 4 — ammunition consumption per shot.
 *
 * Rules verified:
 *  - Each ballistic/missile declaration decrements exactly 1 round from the attacker's
 *    first non-empty bin of the matching [AmmoType], regardless of hit or miss.
 *  - Cluster weapons (LRM/SRM) also consume exactly 1 round per declaration (not per missile).
 *  - Energy weapons (no ammoType) consume nothing.
 *  - When all bins of a weapon's ammoType are empty, [HasAmmoRule] blocks further declarations.
 *  - Remaining round count is correct after N shots, feeding heat-phase ammo explosion math.
 *  - Multi-weapon volleys from one unit decrement each weapon's bin correctly.
 *
 * No dice are consumed by ammo decrements (verified by ordering: decrements happen after all
 * to-hit / location / crit dice; seeded tests are unchanged by this task).
 */
internal class AmmoConsumptionTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Shared fixtures
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Target with thick armor so it survives many shots without dying (which would cause
     * the attacker lookup to return null and skip decrement — would be a false pass).
     */
    private val toughTarget = aUnit(
        id = "target",
        position = HexCoordinates(1, 0),
        facing = FiringArc.bearingDirection(HexCoordinates(1, 0), HexCoordinates(0, 0)),
        armor = anArmorLayout(
            centerTorso = 200, centerTorsoRear = 50,
            leftTorso = 200, leftTorsoRear = 50,
            rightTorso = 200, rightTorsoRear = 50,
            leftArm = 200, rightArm = 200,
            leftLeg = 200, rightLeg = 200,
        ),
        internalStructure = anInternalStructureLayout(
            centerTorso = 100, leftTorso = 100, rightTorso = 100,
            leftArm = 100, rightArm = 100,
            leftLeg = 100, rightLeg = 100,
        ),
    )

    /** Roller that always misses: 2d6 = (1,1) = 2, which is below any positive TN. */
    private fun missingRoller(rounds: Int = 1): DiceRoller =
        DiceRoller.deterministic(*IntArray(rounds * 2) { if (it % 2 == 0) 1 else 1 })

    // ─────────────────────────────────────────────────────────────────────────
    // AC/20 — fire until empty, then HasAmmoRule blocks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `AC20 bin decrements by 1 on each declaration until empty`() {
        // AC20 shotsPerTon = 5; place 1 ton = 1 bin, 5 shots.
        val layout = mechLayout { ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1) }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 4,
            weapons = listOf(Weapon(WeaponModels.ac20)),
            criticalLayout = layout,
        )
        var state = GameState(listOf(attacker, toughTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(attacker.id, toughTarget.id, 0, true)

        // AC20 shotsPerTon is 5 — fire 5 times to drain the bin.
        val shotsPerTon = AmmoType.AC20.shotsPerTon  // = 5
        repeat(shotsPerTon) { shotNumber ->
            val before = state.unitById(attacker.id)!!
                .criticalLayout.ammoBins().sumOf { it.third.shots }
            assertEquals(shotsPerTon - shotNumber, before,
                "Expected ${shotsPerTon - shotNumber} shots before shot ${shotNumber + 1}")

            // Roll 1,1 = miss; no location roll consumed
            val (newState, _) = resolveAttacks(listOf(decl), state, DiceRoller.deterministic(1, 1))
            state = newState

            val after = state.unitById(attacker.id)!!
                .criticalLayout.ammoBins().sumOf { it.third.shots }
            assertEquals(shotsPerTon - shotNumber - 1, after,
                "Expected ${shotsPerTon - shotNumber - 1} shots after shot ${shotNumber + 1}")
        }

        // After 5 shots the bin is empty.
        val finalAmmoBins = state.unitById(attacker.id)!!.criticalLayout.ammoBins()
        assertEquals(0, finalAmmoBins.sumOf { it.third.shots })

        // HasAmmoRule must now block further declarations.
        val rule = HasAmmoRule()
        val freshAttacker = state.unitById(attacker.id)!!
        val ctx = aWeaponAttackContext(
            actor = freshAttacker,
            weapon = freshAttacker.weapons[0],
            gameState = state,
            target = toughTarget,
        )
        assertInstanceOf(RuleResult.Unsatisfied::class.java, rule.evaluate(ctx),
            "HasAmmoRule should block after bin is empty")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Miss still consumes one round
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `miss still consumes one round from the ammo bin`() {
        val layout = mechLayout { ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1) }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 12,  // TN ≥ 12 → guaranteed miss
            weapons = listOf(Weapon(WeaponModels.ac20)),
            criticalLayout = layout,
        )
        val state = GameState(listOf(attacker, toughTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(attacker.id, toughTarget.id, 0, true)

        // Roll 1+1 = 2 < TN → miss. Ammo should still be decremented.
        val (newState, results) = resolveAttacks(listOf(decl), state, DiceRoller.deterministic(1, 1))

        assertInstanceOf(AttackResult.Miss::class.java, results.single(), "Should have missed")
        val shots = newState.unitById(attacker.id)!!
            .criticalLayout.ammoBins().sumOf { it.third.shots }
        assertEquals(AmmoType.AC20.shotsPerTon - 1, shots, "Miss should still consume a round")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hit also consumes one round
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `hit on AC20 consumes exactly one round`() {
        val layout = mechLayout { ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1) }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 0,  // TN = 0 → guaranteed hit on any roll
            weapons = listOf(Weapon(WeaponModels.ac20)),
            criticalLayout = layout,
            position = HexCoordinates(0, 0),
        )
        val state = GameState(listOf(attacker, toughTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(attacker.id, toughTarget.id, 0, true)

        // to-hit (6,6)=12 ≥ TN 0 → hit; location (3,4)=7 → CENTER_TORSO
        val (newState, results) = resolveAttacks(
            listOf(decl), state, DiceRoller.deterministic(6, 6, 3, 4),
        )

        assertInstanceOf(AttackResult.Hit::class.java, results.single(), "Should have hit")
        val shots = newState.unitById(attacker.id)!!
            .criticalLayout.ammoBins().sumOf { it.third.shots }
        assertEquals(AmmoType.AC20.shotsPerTon - 1, shots, "Hit should consume exactly one round")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cluster weapon (LRM-10) — exactly 1 round per declaration, not per missile
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `LRM-10 declaration consumes exactly 1 round regardless of missiles hitting`() {
        // LRM-10: clusterSize=10, shotsPerTon=12 (AmmoType.LRM10)
        val layout = mechLayout { ammo(MechLocation.LEFT_TORSO, AmmoType.LRM10, 1) }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 0,  // guaranteed hit
            weapons = listOf(Weapon(WeaponModels.lrm10)),
            criticalLayout = layout,
            position = HexCoordinates(0, 0),
        )
        val lrmTarget = toughTarget.copy(position = HexCoordinates(7, 0))
        val state = GameState(listOf(attacker, lrmTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(attacker.id, lrmTarget.id, 0, true)

        // Dice: to-hit(3,3)=6 ≥ 0 → hit; cluster(6,5)=11 → size 10 roll 11 → 10 missiles
        // Groups: 10/5=2 full groups; loc1(3,4)=7→CT, loc2(4,4)=8→LT
        // Target has thick armor so no IS → no crit dice.
        val (newState, results) = resolveAttacks(
            listOf(decl), state,
            DiceRoller.deterministic(3, 3, 6, 5, 3, 4, 4, 4),
        )

        assertInstanceOf(AttackResult.ClusterHit::class.java, results.single(), "LRM should have hit")
        assertEquals(10, (results.single() as AttackResult.ClusterHit).missilesHit, "Expect 10 missiles hit")
        val shots = newState.unitById(attacker.id)!!
            .criticalLayout.ammoBins().sumOf { it.third.shots }
        assertEquals(AmmoType.LRM10.shotsPerTon - 1, shots,
            "LRM-10 should consume exactly 1 round per declaration, not per missile")
    }

    @Test
    fun `LRM-10 miss still consumes exactly 1 round`() {
        val layout = mechLayout { ammo(MechLocation.LEFT_TORSO, AmmoType.LRM10, 1) }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 12,
            weapons = listOf(Weapon(WeaponModels.lrm10)),
            criticalLayout = layout,
        )
        val state = GameState(listOf(attacker, toughTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(attacker.id, toughTarget.id, 0, true)

        val (newState, results) = resolveAttacks(
            listOf(decl), state, DiceRoller.deterministic(1, 1),
        )

        assertInstanceOf(AttackResult.Miss::class.java, results.single())
        val shots = newState.unitById(attacker.id)!!
            .criticalLayout.ammoBins().sumOf { it.third.shots }
        assertEquals(AmmoType.LRM10.shotsPerTon - 1, shots,
            "LRM-10 miss should still consume exactly 1 round")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SRM-6 — 1 round per declaration (6 missiles, still 1 ammo)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `SRM-6 declaration consumes exactly 1 round regardless of missile count`() {
        val layout = mechLayout { ammo(MechLocation.RIGHT_TORSO, AmmoType.SRM6, 1) }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 2,
            weapons = listOf(Weapon(WeaponModels.srm6)),
            criticalLayout = layout,
        )
        val state = GameState(listOf(attacker, toughTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(attacker.id, toughTarget.id, 0, true)

        // to-hit(3,3)=6 ≥ TN 2 → hit; cluster(6,5)=11 → 6 missiles; 6 loc rolls, target thick armor
        val (newState, results) = resolveAttacks(
            listOf(decl), state,
            DiceRoller.deterministic(3, 3, 6, 5, 3, 4, 4, 4, 5, 5, 1, 2, 2, 2, 5, 6),
        )

        assertInstanceOf(AttackResult.ClusterHit::class.java, results.single())
        assertEquals(6, (results.single() as AttackResult.ClusterHit).missilesHit)
        val shots = newState.unitById(attacker.id)!!
            .criticalLayout.ammoBins().sumOf { it.third.shots }
        assertEquals(AmmoType.SRM6.shotsPerTon - 1, shots,
            "SRM-6 should consume exactly 1 round per declaration (not per missile)")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Energy weapon — no ammo consumed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `energy weapon (medium laser) consumes no ammo`() {
        val attacker = aUnit(
            id = "attacker",
            weapons = listOf(Weapon(WeaponModels.mediumLaser)),
            // No ammo bins in the layout
        )
        val state = GameState(listOf(attacker, toughTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(attacker.id, toughTarget.id, 0, true)

        val (newState, _) = resolveAttacks(listOf(decl), state, DiceRoller.deterministic(1, 1))

        // CriticalLayout should be unchanged — no bins to decrement
        val newAttacker = newState.unitById(attacker.id)!!
        assertEquals(0, newAttacker.criticalLayout.ammoBins().size,
            "Energy weapon unit should have no ammo bins")
        assertEquals(attacker.criticalLayout, newAttacker.criticalLayout,
            "CriticalLayout should be unchanged after energy weapon fire")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Remaining count after N shots feeds explosion damage
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `bin shot count after partial drain matches expected explosion damage`() {
        // 1 ton AC20 = 5 shots; fire 3 times → 2 shots remain.
        // Remaining damage if exploded: 2 shots × 20 dmg/shot = 40.
        val layout = mechLayout { ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1) }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 0,
            weapons = listOf(Weapon(WeaponModels.ac20)),
            criticalLayout = layout,
        )
        var state = GameState(listOf(attacker, toughTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(attacker.id, toughTarget.id, 0, true)

        // Fire 3 times (all misses to keep dice simple)
        repeat(3) {
            val (next, _) = resolveAttacks(listOf(decl), state, DiceRoller.deterministic(1, 1))
            state = next
        }

        val bins = state.unitById(attacker.id)!!.criticalLayout.ammoBins()
        assertEquals(1, bins.size, "Should still have one ammo bin")
        val (_, _, remainingBin) = bins.single()
        assertEquals(AmmoType.AC20, remainingBin.type)
        assertEquals(2, remainingBin.shots, "Expected 2 shots remaining after 3 fires")

        // The heat-phase cook-off / crit-triggered detonation reads bin.shots to compute
        // explosion damage: shots × damagePerShot.
        val expectedExplosionDamage = remainingBin.shots * remainingBin.type.damagePerShot
        assertEquals(40, expectedExplosionDamage,
            "2 remaining AC20 shots × 20 dmg/shot = 40 explosion damage")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-weapon volley — each weapon's bin decrements independently
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `multi-weapon volley decrements each weapon type's bin once`() {
        // Unit fires AC/20 (weapon 0) and SRM-6 (weapon 1) in the same volley.
        // Two separate ammo bins: AC20 (5 shots) and SRM6 (15 shots).
        val layout = mechLayout {
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)  // 5 shots
            ammo(MechLocation.LEFT_TORSO, AmmoType.SRM6, 1)   // 15 shots
        }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 0,
            weapons = listOf(Weapon(WeaponModels.ac20), Weapon(WeaponModels.srm6)),
            criticalLayout = layout,
        )
        val target2 = aUnit(
            id = "target2",
            position = HexCoordinates(1, 0),
            armor = anArmorLayout(
                centerTorso = 200, centerTorsoRear = 50,
                leftTorso = 200, leftTorsoRear = 50,
                rightTorso = 200, rightTorsoRear = 50,
                leftArm = 200, rightArm = 200,
                leftLeg = 200, rightLeg = 200,
            ),
            internalStructure = anInternalStructureLayout(
                centerTorso = 100, leftTorso = 100, rightTorso = 100,
                leftArm = 100, rightArm = 100, leftLeg = 100, rightLeg = 100,
            ),
        )
        val state = GameState(listOf(attacker, target2), GameMap(emptyMap()))

        val decls = listOf(
            AttackDeclaration(attacker.id, target2.id, 0, true),   // AC/20
            AttackDeclaration(attacker.id, target2.id, 1, false),  // SRM-6 (secondary)
        )

        // Dice:
        //  decl 0 (AC/20, single-location): to-hit(1,1)=2 → miss (TN=0 → hit actually…
        //    wait, gunnery 0, mods 0, TN=0, 2 >= 0 → hit; loc(3,4)=7→CT)
        //  decl 1 (SRM-6, cluster): to-hit(1,1)=2 ≥ 0 → hit; cluster(1,1)=2 → 2 missiles;
        //    loc1(3,4)=7→CT, loc2(4,4)=8→LT
        // All thick armor → no crit dice.
        val (newState, results) = resolveAttacks(
            decls, state,
            DiceRoller.deterministic(
                3, 4,   // AC/20 to-hit = 7 ≥ 0 → hit
                3, 4,   // AC/20 location = 7 → CENTER_TORSO
                3, 4,   // SRM-6 to-hit = 7 ≥ 0 → hit (secondary +1 → TN=1, 7≥1)
                1, 1,   // SRM-6 cluster = 2 → size 6 → 2 missiles
                3, 4,   // SRM-6 loc 1 = 7 → CENTER_TORSO
                4, 4,   // SRM-6 loc 2 = 8 → LEFT_TORSO
            ),
        )

        assertEquals(2, results.size)
        assertInstanceOf(AttackResult.Hit::class.java, results[0], "AC/20 should hit")
        assertInstanceOf(AttackResult.Hit::class.java, results[1], "SRM-6 should hit")

        val bins = newState.unitById(attacker.id)!!.criticalLayout.ammoBins()
        val ac20Bin = bins.first { it.third.type == AmmoType.AC20 }
        val srm6Bin = bins.first { it.third.type == AmmoType.SRM6 }

        assertEquals(AmmoType.AC20.shotsPerTon - 1, ac20Bin.third.shots,
            "AC/20 bin should have decremented by 1")
        assertEquals(AmmoType.SRM6.shotsPerTon - 1, srm6Bin.third.shots,
            "SRM-6 bin should have decremented by 1")
    }

    @Test
    fun `two weapons sharing same ammo type each decrement from the same pool`() {
        // Two SRM-2 weapons sharing a single SRM2 ammo bin pool (1 bin = 50 shots).
        val layout = mechLayout { ammo(MechLocation.RIGHT_TORSO, AmmoType.SRM2, 1) }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 4,  // TN=4 primary, TN=5 secondary; roll (1,1)=2 misses both
            weapons = listOf(Weapon(WeaponModels.srm2), Weapon(WeaponModels.srm2)),
            criticalLayout = layout,
        )
        val state = GameState(listOf(attacker, toughTarget), GameMap(emptyMap()))
        val decls = listOf(
            AttackDeclaration(attacker.id, toughTarget.id, 0, true),
            AttackDeclaration(attacker.id, toughTarget.id, 1, false),
        )

        // Both miss (TN = gunnery 2 + secondary 1 for decl 1 = 3; roll 1+1=2 → miss)
        val (newState, _) = resolveAttacks(
            decls, state,
            DiceRoller.deterministic(1, 1, 1, 1),  // two 2d6 miss rolls
        )

        val shots = newState.unitById(attacker.id)!!
            .criticalLayout.ammoBins().sumOf { it.third.shots }
        assertEquals(AmmoType.SRM2.shotsPerTon - 2, shots,
            "Two SRM-2 declarations should consume 2 rounds total from the shared bin")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dice order is unchanged — ammo decrement is pure state mutation, no dice
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `ammo decrement does not consume any additional dice from the roller`() {
        // Pin the dice stream for an AC/20 hit: to-hit(4,5)=9, location(3,4)=7→CT.
        // If ammo decrement consumed a die the location roll would be wrong.
        val layout = mechLayout { ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1) }.layout
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 0,
            weapons = listOf(Weapon(WeaponModels.ac20)),
            criticalLayout = layout,
        )
        val state = GameState(listOf(attacker, toughTarget), GameMap(emptyMap()))
        val decl = AttackDeclaration(attacker.id, toughTarget.id, 0, true)

        val roller = DiceRoller.deterministic(4, 5, 3, 4)
        val (newState, results) = resolveAttacks(listOf(decl), state, roller)
        val result = results.single()

        // Verify dice stream: to-hit = (4,5)=9, location = (3,4)=7→CENTER_TORSO
        assertEquals(battletech.tactical.dice.DiceRoll(4, 5), result.toHitRoll,
            "to-hit roll should be (4,5) — ammo decrement must not consume dice")
        assertInstanceOf(AttackResult.Hit::class.java, result)
        val hit = result as AttackResult.Hit
        assertEquals(battletech.tactical.dice.DiceRoll(3, 4), hit.locationHits.first().locationRoll,
            "location roll should be (3,4) — ammo decrement must not consume dice")
        assertEquals(HitLocation.CENTER_TORSO, hit.locationHits.first().location)

        // Also verify the ammo was decremented
        val shots = newState.unitById(attacker.id)!!
            .criticalLayout.ammoBins().sumOf { it.third.shots }
        assertEquals(AmmoType.AC20.shotsPerTon - 1, shots)
    }
}

private fun assertTrue(condition: Boolean, message: String? = null) {
    if (message != null) {
        org.junit.jupiter.api.Assertions.assertTrue(condition, message)
    } else {
        org.junit.jupiter.api.Assertions.assertTrue(condition)
    }
}
