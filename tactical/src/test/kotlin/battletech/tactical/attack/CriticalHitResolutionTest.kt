package battletech.tactical.attack

import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MechLocation
import battletech.tactical.query.aUnit
import battletech.tactical.query.anArmorLayout
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.query.mediumLaser
import battletech.tactical.session.AmmoExploded
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.PilotHit
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.DestructionReason
import battletech.tactical.unit.PILOT_DEATH_THRESHOLD
import battletech.tactical.unit.UnitRoster
import battletech.tactical.unit.WeaponModels
import battletech.tactical.unit.destructionReason
import battletech.tactical.unit.isSlotDestroyed
import battletech.tactical.unit.mechLayout
import battletech.tactical.unit.withSlot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CriticalHitResolutionTest {

    private val baseUnit = aUnit(
        id = "crit-target",
        armor = anArmorLayout(),
        internalStructure = anInternalStructureLayout(),
    )

    // --- Crit table mapping ---------------------------------------------------

    @Test
    fun `2 through 7 score no critical hit`() {
        for (total in 2..7) {
            val (d1, d2) = splitTotal(total)
            val roller = DiceRoller.deterministic(d1, d2)
            val (updated, events) = resolveCriticalHits(baseUnit, MechLocation.LEFT_ARM, roller)
            assertThat(events).describedAs("total=$total").isEmpty()
            assertThat(updated.criticalHits[MechLocation.LEFT_ARM] ?: emptySet<Int>())
                .describedAs("total=$total")
                .isEmpty()
        }
    }

    @Test
    fun `8 or 9 score one critical hit`() {
        for (total in listOf(8, 9)) {
            val (d1, d2) = splitTotal(total)
            // Block roll 1 (upper), slot roll 2 -> index 1 (UPPER_ARM actuator on LEFT_ARM)
            val roller = DiceRoller.deterministic(d1, d2, 1, 2)
            val (updated, events) = resolveCriticalHits(baseUnit, MechLocation.LEFT_ARM, roller)
            assertThat(events).describedAs("total=$total").hasSize(1)
            assertThat(updated.criticalHits[MechLocation.LEFT_ARM]).containsExactly(1)
        }
    }

    @Test
    fun `10 or 11 score two critical hits`() {
        for (total in listOf(10, 11)) {
            val (d1, d2) = splitTotal(total)
            // First pick: block 1, slot 2 -> index 1 (UPPER_ARM). Second: block 1, slot 1 -> index 0 (SHOULDER).
            val roller = DiceRoller.deterministic(d1, d2, 1, 2, 1, 1)
            val (updated, events) = resolveCriticalHits(baseUnit, MechLocation.LEFT_ARM, roller)
            assertThat(events).describedAs("total=$total").hasSize(2)
            assertThat(updated.criticalHits[MechLocation.LEFT_ARM]).containsExactlyInAnyOrder(0, 1)
        }
    }

    @Test
    fun `12 on torso scores three critical hits`() {
        // 2d6 = 12 (6,6). Three picks: (1,2)->idx1 Engine, (1,3)->idx2 Engine, (1,4)->idx3 Gyro
        val roller = DiceRoller.deterministic(6, 6, 1, 2, 1, 3, 1, 4)
        val (updated, events) = resolveCriticalHits(baseUnit, MechLocation.CENTER_TORSO, roller)
        assertThat(events).hasSize(3)
        assertThat(updated.criticalHits[MechLocation.CENTER_TORSO]).containsExactlyInAnyOrder(1, 2, 3)
    }

    @Test
    fun `12 on arm blows the limb off - internal structure zeroed and all slots destroyed`() {
        val roller = DiceRoller.deterministic(6, 6)
        val (updated, events) = resolveCriticalHits(baseUnit, MechLocation.LEFT_ARM, roller)
        assertThat(updated.internalStructure.leftArm).isEqualTo(0)
        val allSlots = updated.criticalLayout.slotsAt(MechLocation.LEFT_ARM)
        assertThat(updated.criticalHits[MechLocation.LEFT_ARM]).containsExactlyInAnyOrderElementsOf(allSlots.indices)
        // One CriticalHit event per slot in the location.
        assertThat(events).hasSize(allSlots.size)
    }

    @Test
    fun `12 on leg blows the limb off`() {
        val roller = DiceRoller.deterministic(6, 6)
        val (updated, _) = resolveCriticalHits(baseUnit, MechLocation.LEFT_LEG, roller)
        assertThat(updated.internalStructure.leftLeg).isEqualTo(0)
        val allSlots = updated.criticalLayout.slotsAt(MechLocation.LEFT_LEG)
        assertThat(updated.criticalHits[MechLocation.LEFT_LEG]).containsExactlyInAnyOrderElementsOf(allSlots.indices)
    }

    // --- Slot pick -------------------------------------------------------------

    @Test
    fun `slot pick lands on a known engine slot and records content in the event`() {
        // 2d6 = 9 -> 1 crit. Block 4 (lower half, start at 6), slot 2 -> index 7 (Engine).
        val roller = DiceRoller.deterministic(4, 5, 4, 2)
        val (updated, events) = resolveCriticalHits(baseUnit, MechLocation.CENTER_TORSO, roller)
        val event = events.single() as CriticalHit.Detailed
        assertThat(event.location).isEqualTo(MechLocation.CENTER_TORSO)
        assertThat(event.slotIndex).isEqualTo(7)
        assertThat(event.content).isEqualTo(CriticalSlotContent.Engine)
        assertThat(updated.isSlotDestroyed(MechLocation.CENTER_TORSO, 7)).isTrue()
    }

    // --- Roll-again -------------------------------------------------------------

    @Test
    fun `roll-again skips an empty slot then lands on a valid one`() {
        // HEAD slot index 3 is Empty. Block 1, slot 4 -> index 3 (Empty, skip).
        // Re-roll: block 1, slot 1 -> index 0 (LifeSupport).
        val roller = DiceRoller.deterministic(4, 5, 1, 4, 1, 1)
        val (updated, events) = resolveCriticalHits(baseUnit, MechLocation.HEAD, roller)
        val event = events.single() as CriticalHit.Detailed
        assertThat(event.slotIndex).isEqualTo(0)
        assertThat(event.content).isEqualTo(CriticalSlotContent.LifeSupport)
        assertThat(updated.isSlotDestroyed(MechLocation.HEAD, 3)).isFalse()
    }

    @Test
    fun `roll-again skips an already-destroyed slot then lands on a valid one`() {
        val alreadyHit = baseUnit.copy(
            criticalHits = mapOf(MechLocation.HEAD to setOf(0)),
        )
        // First attempt: block 1, slot 1 -> index 0 (already destroyed, skip).
        // Re-roll: block 1, slot 2 -> index 1 (Sensors).
        val roller = DiceRoller.deterministic(4, 5, 1, 1, 1, 2)
        val (updated, events) = resolveCriticalHits(alreadyHit, MechLocation.HEAD, roller)
        val event = events.single() as CriticalHit.Detailed
        assertThat(event.slotIndex).isEqualTo(1)
        assertThat(event.content).isEqualTo(CriticalSlotContent.Sensors)
        assertThat(updated.criticalHits[MechLocation.HEAD]).containsExactlyInAnyOrder(0, 1)
    }

    // --- Natural-2 through-armor -------------------------------------------------

    @Test
    fun `natural-2 location roll triggers a crit check even with intact armor`() {
        val attacker = aUnit(
            id = "attacker",
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(0, 0),
            gunnerySkill = 4,
        )
        val target = aUnit(
            id = "target",
            position = HexCoordinates(1, 0),
            facing = FiringArc.bearingDirection(HexCoordinates(1, 0), HexCoordinates(0, 0)),
            armor = anArmorLayout(centerTorso = 47),
            internalStructure = anInternalStructureLayout(centerTorso = 31),
        )
        val state = GameState(UnitRoster(listOf(attacker, target)), GameMap(emptyMap()))
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)

        // to-hit: 4+5=9 (hit, TN=4). location: 1+1=2 -> natural 2, CENTER_TORSO.
        // Damage (5) fully absorbed by armor (47), so no IS damage — only the
        // natural-2 bonus check fires. Crit roll: 4+5=9 -> 1 crit, block 4 slot 2 -> idx 7 (Engine).
        val roller = DiceRoller.deterministic(4, 5, 1, 1, 4, 5, 4, 2)
        val (newState, results, criticalHits) = resolveAttacksWithCrits(listOf(declaration), state, roller)

        val result = results.single()
        assertThat(result).isInstanceOf(AttackResult.Hit::class.java)
        result as AttackResult.Hit
        assertThat(result.locationHits.first().location).isEqualTo(HitLocation.CENTER_TORSO)
        assertThat(result.damage.first().structureDamage).isEqualTo(0)
        assertThat(criticalHits).hasSize(1)
        val updatedTarget = newState.units.byId(target.id)
        assertThat(updatedTarget.criticalHits[MechLocation.CENTER_TORSO]).isNotNull()
    }

    // --- Integration via resolveAttacks -----------------------------------------

    @Test
    fun `a hit that blows into IS triggers exactly one crit check on that location`() {
        val attacker = aUnit(
            id = "attacker",
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(0, 0),
            gunnerySkill = 4,
        )
        val target = aUnit(
            id = "target",
            position = HexCoordinates(1, 0),
            facing = FiringArc.bearingDirection(HexCoordinates(1, 0), HexCoordinates(0, 0)),
            armor = anArmorLayout(centerTorso = 2),
            internalStructure = anInternalStructureLayout(centerTorso = 31),
        )
        val state = GameState(UnitRoster(listOf(attacker, target)), GameMap(emptyMap()))
        val declaration = AttackDeclaration(attacker.id, target.id, 0, true)

        // to-hit 4+5=9 (hit). location 3+4=7 (CENTER_TORSO, not natural-2).
        // Medium laser 5 dmg: 2 armor absorbed, 3 IS damage -> exactly one crit check.
        // Crit roll: 1+1=2 -> no crit (table says 2-7 none), consumes no further dice.
        val roller = DiceRoller.deterministic(4, 5, 3, 4, 1, 1)
        val (newState, results, criticalHits) = resolveAttacksWithCrits(listOf(declaration), state, roller)

        val result = results.single()
        assertThat(result).isInstanceOf(AttackResult.Hit::class.java)
        assertThat((result as AttackResult.Hit).damage.first().structureDamage).isEqualTo(3)
        assertThat(criticalHits).isEmpty()
        assertThat(newState.units.byId(target.id).criticalHits).isEmpty()
    }

    @Test
    fun `volley ordering - second attack's roll-again sees first attack's destroyed slot`() {
        val attacker1 = aUnit(
            id = "attacker1",
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(0, 0),
            gunnerySkill = 4,
        )
        val attacker2 = aUnit(
            id = "attacker2",
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(0, -1),
            gunnerySkill = 4,
        )
        val target = aUnit(
            id = "target",
            position = HexCoordinates(1, 0),
            facing = FiringArc.bearingDirection(HexCoordinates(1, 0), HexCoordinates(0, 0)),
            armor = anArmorLayout(centerTorso = 2),
            internalStructure = anInternalStructureLayout(centerTorso = 31),
        )
        val state = GameState(UnitRoster(listOf(attacker1, attacker2, target)), GameMap(emptyMap()))
        val declarations = listOf(
            AttackDeclaration(attacker1.id, target.id, 0, true),
            AttackDeclaration(attacker2.id, target.id, 0, true),
        )

        val roller = DiceRoller.deterministic(
            // attack1: to-hit, location (CT, non-natural-2)
            4, 5, 3, 4,
            // attack2: to-hit, location (CT, non-natural-2)
            4, 5, 3, 4,
            // attack1 crit check: 4+5=9 -> 1 crit, block 4 slot 2 -> idx 7 (Engine)
            4, 5, 4, 2,
            // attack2 crit check: 4+5=9 -> 1 crit. First attempt lands on the same
            // idx 7 (now destroyed) -> roll-again -> block 4 slot 3 -> idx 8 (Engine)
            4, 5, 4, 2, 4, 3,
        )
        val (newState, _, criticalHits) = resolveAttacksWithCrits(declarations, state, roller)

        assertThat(criticalHits).hasSize(2)
        val updatedTarget = newState.units.byId(target.id)
        assertThat(updatedTarget.criticalHits[MechLocation.CENTER_TORSO]).containsExactlyInAnyOrder(7, 8)
    }

    // --- Weapon disable ---------------------------------------------------------

    @Test
    fun `crit on a weapon-mount slot disables that weapon`() {
        val build = mechLayout {
            place(MechLocation.RIGHT_TORSO, WeaponModels.mediumLaser)
        }
        val unit = aUnit(
            id = "crit-target",
            armor = anArmorLayout(),
            internalStructure = anInternalStructureLayout(),
            weapons = build.weapons,
            criticalLayout = build.layout,
        )
        // 2d6 = 9 -> 1 crit. Block 1 (upper), slot 1 -> index 0 (the laser's only slot).
        val roller = DiceRoller.deterministic(4, 5, 1, 1)
        val (updated, events) = resolveCriticalHits(unit, MechLocation.RIGHT_TORSO, roller)

        val criticalHit = events.filterIsInstance<CriticalHit.Detailed>().single()
        assertThat(criticalHit.content).isEqualTo(CriticalSlotContent.WeaponMount(build.weapons.single().mountId!!))
        assertThat(updated.weapons.single().destroyed).isTrue()
    }

    @Test
    fun `a weapon spanning multiple slots is disabled when any one slot is crit`() {
        val build = mechLayout {
            place(MechLocation.RIGHT_TORSO, WeaponModels.largeLaser)
        }
        val unit = aUnit(
            id = "crit-target",
            armor = anArmorLayout(),
            internalStructure = anInternalStructureLayout(),
            weapons = build.weapons,
            criticalLayout = build.layout,
        )
        // 2d6 = 9 -> 1 crit. Block 1 (upper), slot 2 -> index 1 (the laser's *second* slot).
        val roller = DiceRoller.deterministic(4, 5, 1, 2)
        val (updated, events) = resolveCriticalHits(unit, MechLocation.RIGHT_TORSO, roller)

        val criticalHit = events.filterIsInstance<CriticalHit.Detailed>().single()
        assertThat(criticalHit.slotIndex).isEqualTo(1)
        assertThat(updated.weapons.single().destroyed).isTrue()
    }

    // --- Ammo-bin detonation -----------------------------------------------------

    @Test
    fun `crit on an ammo bin detonates it into its own location, empties it, and emits AmmoExploded`() {
        val build = mechLayout {
            place(MechLocation.RIGHT_TORSO, WeaponModels.mediumLaser)
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            id = "crit-target",
            armor = anArmorLayout(rightTorso = 0),
            internalStructure = anInternalStructureLayout(rightTorso = 21),
            weapons = build.weapons,
            criticalLayout = build.layout,
        )
        // 2d6 = 9 -> 1 crit. Block 1 (upper), slot 2 -> index 1 (the ammo bin).
        // Ammo explosion inflicts 2 pilot hits: consciousness 2d6 (hit 1), 2d6 (hit 2).
        val roller = DiceRoller.deterministic(4, 5, 1, 2, 3, 3, 3, 3)
        val (updated, events) = resolveCriticalHits(unit, MechLocation.RIGHT_TORSO, roller)

        val exploded = events.filterIsInstance<AmmoExploded.Detailed>().single()
        assertThat(exploded.unitId).isEqualTo(unit.id)
        assertThat(exploded.ammoType).isEqualTo(AmmoType.AC20)
        assertThat(exploded.damage).isEqualTo(100) // 5 shots * 20 damage per shot

        // 100 damage into 0-armor, 21-IS right torso: IS destroyed, 79 excess
        // transfers into center torso (31 IS), leaving 31 - 79 -> destroyed, no further transfer.
        assertThat(updated.internalStructure.rightTorso).isEqualTo(0)
        assertThat(updated.internalStructure.centerTorso).isEqualTo(0)

        val remainingBin = updated.criticalLayout.ammoBins().single()
        assertThat(remainingBin.third.shots).isEqualTo(0)
    }

    @Test
    fun `crit on an already-empty ammo bin does nothing extra`() {
        val build = mechLayout {
            place(MechLocation.RIGHT_TORSO, WeaponModels.mediumLaser)
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        val emptiedLayout = build.layout.let { layout ->
            val (location, index, bin) = layout.ammoBins().single()
            layout.withSlot(location, index, bin.copy(shots = 0))
        }
        val unit = aUnit(
            id = "crit-target",
            armor = anArmorLayout(),
            internalStructure = anInternalStructureLayout(),
            weapons = build.weapons,
            criticalLayout = emptiedLayout,
        )
        val originalIS = unit.internalStructure

        // 2d6 = 9 -> 1 crit. Block 1 (upper), slot 2 -> index 1 (the now-empty ammo bin).
        val roller = DiceRoller.deterministic(4, 5, 1, 2)
        val (updated, events) = resolveCriticalHits(unit, MechLocation.RIGHT_TORSO, roller)

        assertThat(events.filterIsInstance<AmmoExploded>()).isEmpty()
        assertThat(events.filterIsInstance<CriticalHit>()).hasSize(1)
        assertThat(updated.internalStructure).isEqualTo(originalIS)
    }

    // --- Cockpit hit -------------------------------------------------------------

    @Test
    fun `crit on the cockpit slot kills the pilot and the unit is destroyed with PILOT_DEAD reason`() {
        // HEAD slot index 2 is Cockpit (standard framework).
        // 2d6 = 9 -> 1 crit. Block 1 (upper, blockStart=0), slot 3 -> index 2 (Cockpit).
        // No consciousness dice consumed: cockpit = instant death, no check needed.
        val roller = DiceRoller.deterministic(4, 5, 1, 3)
        val (updated, events) = resolveCriticalHits(baseUnit, MechLocation.HEAD, roller)

        // The destroyed slot is recorded.
        assertThat(updated.criticalHits[MechLocation.HEAD]).contains(2)

        // A CriticalHit event is emitted for the cockpit slot.
        val critEvent = events.filterIsInstance<CriticalHit.Detailed>().single()
        assertThat(critEvent.location).isEqualTo(MechLocation.HEAD)
        assertThat(critEvent.slotIndex).isEqualTo(2)
        assertThat(critEvent.content).isEqualTo(CriticalSlotContent.Cockpit)

        // A fatal PilotHit event follows: no consciousness roll, pilot hits = death threshold.
        val pilotEvent = events.filterIsInstance<PilotHit.Fatal>().single()
        assertThat(pilotEvent.unitId).isEqualTo(baseUnit.id)
        assertThat(pilotEvent.pilotHits).isEqualTo(PILOT_DEATH_THRESHOLD)

        // The unit's pilotHits reaches the death threshold -> PILOT_DEAD destruction reason.
        assertThat(updated.pilotHits).isEqualTo(PILOT_DEATH_THRESHOLD)
        assertThat(destructionReason(updated)).isEqualTo(DestructionReason.PILOT_DEAD)
    }

    @Test
    fun `cockpit crit on a pilot with existing hits still kills outright`() {
        // Pilot already has 3 hits; cockpit sets pilotHits directly to PILOT_DEATH_THRESHOLD.
        val woundedPilot = baseUnit.copy(pilotHits = 3)
        val roller = DiceRoller.deterministic(4, 5, 1, 3)
        val (updated, events) = resolveCriticalHits(woundedPilot, MechLocation.HEAD, roller)

        assertThat(updated.pilotHits).isEqualTo(PILOT_DEATH_THRESHOLD)
        assertThat(destructionReason(updated)).isEqualTo(DestructionReason.PILOT_DEAD)
        val pilotEvent = events.filterIsInstance<PilotHit.Fatal>().single()
        assertThat(pilotEvent.pilotHits).isEqualTo(PILOT_DEATH_THRESHOLD)
    }

    private fun splitTotal(total: Int): Pair<Int, Int> {
        val d1 = (total - 1).coerceIn(1, 6)
        val d2 = total - d1
        require(d2 in 1..6) { "cannot split $total into two d6 faces" }
        return d1 to d2
    }
}
