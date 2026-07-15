package battletech.tactical.attack

import battletech.tactical.attack.weapon.HasAmmoRule
import battletech.tactical.attack.weapon.WeaponNotDestroyedRule
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.movement.MovementPhaseHandler
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachabilityCalculator
import battletech.tactical.movement.ReachableHex
import battletech.tactical.query.RuleResult
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitFell
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.WeaponModels
import battletech.tactical.unit.availableAmmoBins
import battletech.tactical.unit.destroyedLegCount
import battletech.tactical.unit.mechLayout
import battletech.tactical.unit.withSlot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import battletech.tactical.query.OwnUnit

/**
 * Tests for Task 5 — Location destruction consequences.
 *
 * Uses direct state manipulation (crafting before/after [GameState]s) rather than
 * routing through [resolveAttacksWithCrits], so each test exercises exactly one
 * consequence path.
 */
internal class LocationDestructionConsequencesTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Arm destruction → weapons disabled
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `arm destroyed - all weapons in that arm are disabled`() {
        val build = mechLayout {
            place(MechLocation.LEFT_ARM, WeaponModels.mediumLaser)
        }
        val unit = aUnit(
            id = "unit-1",
            weapons = build.weapons,
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(leftArm = 17),
        )
        val before = GameState(listOf(unit), GameMap(emptyMap()))
        val afterUnit = unit.copy(internalStructure = anInternalStructureLayout(leftArm = 0))
        val after = GameState(listOf(afterUnit), GameMap(emptyMap()))

        val (finalState, events) = applyLocationDestructionConsequences(before, after, DiceRoller.deterministic())

        val finalUnit = finalState.unitById(unit.id)!!
        assertThat(finalUnit.weapons).allMatch { it.destroyed }
        assertThat(events).isEmpty()
    }

    @Test
    fun `arm weapon destroyed - WeaponNotDestroyedRule rejects it`() {
        val weapon = Weapon(model = WeaponModels.mediumLaser, destroyed = true)
        val unit = aUnit(id = "unit-1", weapons = listOf(weapon))
        val rule = WeaponNotDestroyedRule()
        val ctx = aWeaponAttackContext(actor = unit, weapon = weapon)

        assertThat(rule.evaluate(ctx)).isInstanceOf(RuleResult.Unsatisfied::class.java)
    }

    @Test
    fun `intact arm weapon - WeaponNotDestroyedRule passes`() {
        val weapon = Weapon(model = WeaponModels.mediumLaser, destroyed = false)
        val unit = aUnit(id = "unit-1", weapons = listOf(weapon))
        val rule = WeaponNotDestroyedRule()
        val ctx = aWeaponAttackContext(actor = unit, weapon = weapon)

        assertThat(rule.evaluate(ctx)).isEqualTo(RuleResult.Satisfied)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Side-torso destruction → same-side arm cascade
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `left torso destroyed - left arm IS zeroed and both locations weapons disabled`() {
        val build = mechLayout {
            place(MechLocation.LEFT_TORSO, WeaponModels.mediumLaser)
            place(MechLocation.LEFT_ARM, WeaponModels.mediumLaser)
        }
        val unit = aUnit(
            id = "unit-1",
            weapons = build.weapons,
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(leftTorso = 21, leftArm = 17),
        )
        val before = GameState(listOf(unit), GameMap(emptyMap()))
        val afterUnit = unit.copy(internalStructure = anInternalStructureLayout(leftTorso = 0, leftArm = 17))
        val after = GameState(listOf(afterUnit), GameMap(emptyMap()))

        val (finalState, events) = applyLocationDestructionConsequences(before, after, DiceRoller.deterministic())

        val finalUnit = finalState.unitById(unit.id)!!
        assertThat(finalUnit.internalStructure.leftTorso).isEqualTo(0)
        assertThat(finalUnit.internalStructure.leftArm).isEqualTo(0)
        assertThat(finalUnit.weapons).allMatch { it.destroyed }
        assertThat(events).isEmpty()
    }

    @Test
    fun `right torso destroyed - right arm cascades, left arm unaffected`() {
        val build = mechLayout {
            place(MechLocation.RIGHT_TORSO, WeaponModels.mediumLaser)
            place(MechLocation.LEFT_ARM, WeaponModels.mediumLaser)
        }
        val unit = aUnit(
            id = "unit-1",
            weapons = build.weapons,
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(rightTorso = 21, rightArm = 17, leftArm = 17),
        )
        val before = GameState(listOf(unit), GameMap(emptyMap()))
        val afterUnit = unit.copy(internalStructure = anInternalStructureLayout(rightTorso = 0, rightArm = 17, leftArm = 17))
        val after = GameState(listOf(afterUnit), GameMap(emptyMap()))

        val (finalState, events) = applyLocationDestructionConsequences(before, after, DiceRoller.deterministic())

        val finalUnit = finalState.unitById(unit.id)!!
        assertThat(finalUnit.internalStructure.rightArm).isEqualTo(0)
        assertThat(finalUnit.internalStructure.leftArm).isEqualTo(17)

        val rtWeapon = finalUnit.weapons.find { w ->
            finalUnit.criticalLayout.weaponIdsAt(MechLocation.RIGHT_TORSO).contains(w.mountId)
        }
        val laWeapon = finalUnit.weapons.find { w ->
            finalUnit.criticalLayout.weaponIdsAt(MechLocation.LEFT_ARM).contains(w.mountId)
        }
        assertThat(rtWeapon?.destroyed).isTrue()
        assertThat(laWeapon?.destroyed).isFalse()
        assertThat(events).isEmpty()
    }

    @Test
    fun `side torso cascade skipped when arm was already destroyed`() {
        val build = mechLayout {
            place(MechLocation.LEFT_TORSO, WeaponModels.mediumLaser)
        }
        val unit = aUnit(
            id = "unit-1",
            weapons = build.weapons,
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(leftTorso = 21, leftArm = 0),
        )
        val before = GameState(listOf(unit), GameMap(emptyMap()))
        val afterUnit = unit.copy(internalStructure = anInternalStructureLayout(leftTorso = 0, leftArm = 0))
        val after = GameState(listOf(afterUnit), GameMap(emptyMap()))

        val (finalState, events) = applyLocationDestructionConsequences(before, after, DiceRoller.deterministic())

        val finalUnit = finalState.unitById(unit.id)!!
        assertThat(finalUnit.internalStructure.leftArm).isEqualTo(0)
        assertThat(events).isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Leg destruction → forced fall
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `one leg destroyed - UnitFell emitted and unit goes prone`() {
        val unit = aUnit(
            id = "unit-1",
            tonnage = 50,
            internalStructure = anInternalStructureLayout(leftLeg = 21),
        )
        val before = GameState(listOf(unit), GameMap(emptyMap()))
        val afterUnit = unit.copy(internalStructure = anInternalStructureLayout(leftLeg = 0))
        val after = GameState(listOf(afterUnit), GameMap(emptyMap()))

        // Fall: location roll (3,4)=7 → CENTER_TORSO; facing roll 1 (no rotation);
        // pilot hit 1 consciousness 2d6 (3,3 → 6 ≥ target 3 → conscious).
        val fallRoller = DiceRoller.deterministic(3, 4, 1, 3, 3)
        val (finalState, events) = applyLocationDestructionConsequences(before, after, fallRoller)

        val finalUnit = finalState.unitById(unit.id)!!
        assertThat(finalUnit.isProne).isTrue()

        val fellEvents = events.filterIsInstance<UnitFell>()
        assertThat(fellEvents).hasSize(1)
        assertThat(fellEvents.single().unitId).isEqualTo(unit.id)
    }

    @Test
    fun `unit already prone when leg destroyed - no additional UnitFell`() {
        val unit = aUnit(
            id = "unit-1",
            tonnage = 50,
            internalStructure = anInternalStructureLayout(leftLeg = 21),
        ).copy(isProne = true)
        val before = GameState(listOf(unit), GameMap(emptyMap()))
        val afterUnit = unit.copy(internalStructure = anInternalStructureLayout(leftLeg = 0))
        val after = GameState(listOf(afterUnit), GameMap(emptyMap()))

        val (_, events) = applyLocationDestructionConsequences(before, after, DiceRoller.deterministic())

        assertThat(events.filterIsInstance<UnitFell>()).isEmpty()
    }

    @Test
    fun `both legs destroyed in same volley - no fall emitted`() {
        val unit = aUnit(
            id = "unit-1",
            tonnage = 50,
            internalStructure = anInternalStructureLayout(leftLeg = 21, rightLeg = 21),
        )
        val before = GameState(listOf(unit), GameMap(emptyMap()))
        val afterUnit = unit.copy(internalStructure = anInternalStructureLayout(leftLeg = 0, rightLeg = 0))
        val after = GameState(listOf(afterUnit), GameMap(emptyMap()))

        val (finalState, events) = applyLocationDestructionConsequences(before, after, DiceRoller.deterministic())

        assertThat(events.filterIsInstance<UnitFell>()).isEmpty()
        assertThat(finalState.unitById(unit.id)!!.destroyedLegCount()).isEqualTo(2)
    }

    @Test
    fun `second leg destroyed when first was already gone - both legs now destroyed, no fall`() {
        // Right leg already destroyed before this volley; left leg destroyed this volley.
        // After update destroyedLegCount() == 2 → both-legs path → no fall.
        val unit = aUnit(
            id = "unit-1",
            tonnage = 50,
            internalStructure = anInternalStructureLayout(leftLeg = 21, rightLeg = 0),
        )
        val before = GameState(listOf(unit), GameMap(emptyMap()))
        val afterUnit = unit.copy(internalStructure = anInternalStructureLayout(leftLeg = 0, rightLeg = 0))
        val after = GameState(listOf(afterUnit), GameMap(emptyMap()))

        val (_, events) = applyLocationDestructionConsequences(before, after, DiceRoller.deterministic())

        assertThat(events.filterIsInstance<UnitFell>()).isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Movement restrictions with a destroyed leg
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `destroyed leg halves walk maxMP in ReachabilityCalculator`() {
        val map = flatClearMap(6)
        val unit = aUnit(
            position = HexCoordinates(0, 0),
            walkingMP = 4,
            runningMP = 6,
            jumpMP = 3,
            internalStructure = anInternalStructureLayout(leftLeg = 0),
        )
        val calc = ReachabilityCalculator(map, listOf(OwnUnit(unit)))

        assertThat(calc.calculate(unit, MovementMode.WALK).maxMP).isEqualTo(2)
        assertThat(calc.calculate(unit, MovementMode.RUN).maxMP).isEqualTo(0)
        assertThat(calc.calculate(unit, MovementMode.JUMP).maxMP).isEqualTo(0)
    }

    @Test
    fun `no destroyed leg - full maxMP in ReachabilityCalculator`() {
        val map = flatClearMap(6)
        val unit = aUnit(
            position = HexCoordinates(0, 0),
            walkingMP = 4,
            runningMP = 6,
            jumpMP = 3,
            internalStructure = anInternalStructureLayout(leftLeg = 21, rightLeg = 21),
        )
        val calc = ReachabilityCalculator(map, listOf(OwnUnit(unit)))

        assertThat(calc.calculate(unit, MovementMode.WALK).maxMP).isEqualTo(4)
        assertThat(calc.calculate(unit, MovementMode.RUN).maxMP).isEqualTo(6)
        assertThat(calc.calculate(unit, MovementMode.JUMP).maxMP).isEqualTo(3)
    }

    @Test
    fun `MovementPhaseHandler rejects JUMP command when leg destroyed`() {
        val unit = aUnit(
            id = "unit-1",
            owner = PlayerId.PLAYER_1,
            jumpMP = 3,
            internalStructure = anInternalStructureLayout(leftLeg = 0),
        )
        val state = aGameState(units = listOf(unit))
        val handler = MovementPhaseHandler()
        val dest = ReachableHex(
            position = HexCoordinates(1, 0),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(MovementStep(HexCoordinates(1, 0), HexDirection.N)),
        )
        val cmd = MoveUnit(PlayerId.PLAYER_1, unit.id, dest, MovementMode.JUMP)

        val rejection = handler.validate(cmd, state, TurnState.NULL)

        assertThat(rejection).isInstanceOf(CommandRejection.LegDestroyed::class.java)
        assertThat((rejection as CommandRejection.LegDestroyed).unitId).isEqualTo(unit.id)
    }

    @Test
    fun `MovementPhaseHandler rejects RUN command when leg destroyed`() {
        val unit = aUnit(
            id = "unit-1",
            owner = PlayerId.PLAYER_1,
            runningMP = 6,
            internalStructure = anInternalStructureLayout(leftLeg = 0),
        )
        val state = aGameState(units = listOf(unit))
        val handler = MovementPhaseHandler()
        val dest = ReachableHex(
            position = HexCoordinates(1, 0),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(MovementStep(HexCoordinates(1, 0), HexDirection.N)),
        )
        val cmd = MoveUnit(PlayerId.PLAYER_1, unit.id, dest, MovementMode.RUN)

        val rejection = handler.validate(cmd, state, TurnState.NULL)

        assertThat(rejection).isInstanceOf(CommandRejection.LegDestroyed::class.java)
    }

    @Test
    fun `MovementPhaseHandler allows WALK when leg is destroyed`() {
        val unit = aUnit(
            id = "unit-1",
            owner = PlayerId.PLAYER_1,
            walkingMP = 4,
            internalStructure = anInternalStructureLayout(leftLeg = 0),
        )
        // A destroyed leg halves walking MP (4/2 = 2). (0,-1) is one hop north — within budget.
        val origin = HexCoordinates(0, 0)
        val north = HexCoordinates(0, -1)
        val state = aGameState(
            units = listOf(unit),
            hexes = mapOf(origin to Hex(origin), north to Hex(north)),
        )
        val handler = MovementPhaseHandler()
        val dest = ReachableHex(
            position = north,
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(MovementStep(north, HexDirection.N)),
        )
        val cmd = MoveUnit(PlayerId.PLAYER_1, unit.id, dest, MovementMode.WALK)

        val rejection = handler.validate(cmd, state, TurnState.NULL)

        assertThat(rejection).isNull()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ammo in destroyed locations excluded
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `availableAmmoBins excludes bin in destroyed location`() {
        val build = mechLayout {
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            id = "unit-1",
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(rightTorso = 0),
        )

        assertThat(unit.availableAmmoBins()).isEmpty()
    }

    @Test
    fun `availableAmmoBins includes bin in intact location`() {
        val build = mechLayout {
            ammo(MechLocation.LEFT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            id = "unit-1",
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(leftTorso = 21),
        )

        val available = unit.availableAmmoBins()
        assertThat(available).hasSize(1)
        assertThat(available.single().first).isEqualTo(MechLocation.LEFT_TORSO)
    }

    @Test
    fun `availableAmmoBins with two bins returns only the intact-location bin`() {
        val build = mechLayout {
            ammo(MechLocation.LEFT_TORSO, AmmoType.AC20, 1)
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            id = "unit-1",
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(leftTorso = 21, rightTorso = 0),
        )

        val available = unit.availableAmmoBins()
        assertThat(available).hasSize(1)
        assertThat(available.single().first).isEqualTo(MechLocation.LEFT_TORSO)
    }

    @Test
    fun `HasAmmoRule unsatisfied when all ammo bins are in destroyed locations`() {
        val build = mechLayout {
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            id = "unit-1",
            weapons = listOf(Weapon(WeaponModels.ac20)),
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(rightTorso = 0),
        )
        val ctx = aWeaponAttackContext(actor = unit, weapon = unit.weapons[0])

        assertThat(HasAmmoRule().evaluate(ctx)).isInstanceOf(RuleResult.Unsatisfied::class.java)
    }

    @Test
    fun `HasAmmoRule satisfied when ammo bin is in intact location`() {
        val build = mechLayout {
            ammo(MechLocation.LEFT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            id = "unit-1",
            weapons = listOf(Weapon(WeaponModels.ac20)),
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(leftTorso = 21),
        )
        val ctx = aWeaponAttackContext(actor = unit, weapon = unit.weapons[0])

        assertThat(HasAmmoRule().evaluate(ctx)).isEqualTo(RuleResult.Satisfied)
    }

    @Test
    fun `HasAmmoRule unsatisfied when intact-location bin is exhausted and other bin is in destroyed location`() {
        val build = mechLayout {
            ammo(MechLocation.LEFT_TORSO, AmmoType.AC20, 1)
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        // Exhaust the LT bin; RT location is destroyed
        val ltBinEntry = build.layout.ammoBins().first { (loc, _, _) -> loc == MechLocation.LEFT_TORSO }
        val exhaustedLayout = build.layout.withSlot(
            MechLocation.LEFT_TORSO,
            ltBinEntry.second,
            CriticalSlotContent.AmmoBin(AmmoType.AC20, shots = 0),
        )
        val unit = aUnit(
            id = "unit-1",
            weapons = listOf(Weapon(WeaponModels.ac20)),
            criticalLayout = exhaustedLayout,
            internalStructure = anInternalStructureLayout(leftTorso = 21, rightTorso = 0),
        )
        val ctx = aWeaponAttackContext(actor = unit, weapon = unit.weapons[0])

        assertThat(HasAmmoRule().evaluate(ctx)).isInstanceOf(RuleResult.Unsatisfied::class.java)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multiple units — consequences isolated to affected unit
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `consequence applies only to unit whose IS changed, bystander unit unchanged`() {
        val build = mechLayout {
            place(MechLocation.LEFT_ARM, WeaponModels.mediumLaser)
        }
        val affected = aUnit(
            id = "affected",
            weapons = build.weapons,
            criticalLayout = build.layout,
            internalStructure = anInternalStructureLayout(leftArm = 17),
        )
        // Bystander has a weapon but no mountId in any criticalLayout → disableWeaponsIn is a no-op
        val bystander = aUnit(
            id = "bystander",
            weapons = listOf(Weapon(WeaponModels.mediumLaser)),
            internalStructure = anInternalStructureLayout(leftArm = 17),
        )
        val before = GameState(listOf(affected, bystander), GameMap(emptyMap()))
        val afterAffected = affected.copy(internalStructure = anInternalStructureLayout(leftArm = 0))
        val after = GameState(listOf(afterAffected, bystander), GameMap(emptyMap()))

        val (finalState, _) = applyLocationDestructionConsequences(before, after, DiceRoller.deterministic())

        assertThat(finalState.unitById(affected.id)!!.weapons).allMatch { it.destroyed }
        assertThat(finalState.unitById(bystander.id)!!.weapons).noneMatch { it.destroyed }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun flatClearMap(radius: Int): GameMap {
    val hexes = buildMap<HexCoordinates, Hex> {
        for (col in -radius..radius) {
            for (row in -radius..radius) {
                val coords = HexCoordinates(col, row)
                put(coords, Hex(coords))
            }
        }
    }
    return GameMap(hexes)
}
