package battletech.tactical.attack

import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MechLocation
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.anArmorLayout
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.query.mediumLaser
import battletech.tactical.session.AmmoExploded
import battletech.tactical.session.PilotHit
import battletech.tactical.session.UnitFell
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.WeaponModels
import battletech.tactical.unit.mechLayout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Expanded forced-PSR rules added in Task 6:
 *  - 20-damage PSR: ≥20 total damage in one attack phase → PSR at +1 per full 20
 *  - Head IS penetration → 1 pilot hit
 *  - Ammo explosion (crit-triggered) → 2 pilot hits
 */
internal class ExpandedPsrTest {

    // ── 20-damage PSR ─────────────────────────────────────────────────────────

    @Test
    fun `applyTwentyDamagePsrs - below 20 damage does nothing and consumes no dice`() {
        val unit = aUnit(id = "unit-1", pilotingSkill = 5)
        val state = GameState(listOf(unit), GameMap(emptyMap()))

        val (newState, events) = applyTwentyDamagePsrs(
            state,
            mapOf(unit.id to 19),
            DiceRoller.deterministic(), // must not consume any dice
        )

        assertThat(events).isEmpty()
        assertThat(newState.unitById(unit.id)!!.isProne).isFalse()
    }

    @Test
    fun `applyTwentyDamagePsrs - exactly 20 damage triggers PSR at +1 and on pass leaves unit standing`() {
        val unit = aUnit(id = "unit-1", pilotingSkill = 5)
        val state = GameState(listOf(unit), GameMap(emptyMap()))
        // PSR TN = 5 + 1 = 6; roll (3,3)=6 ≥ 6 → pass.
        val roller = DiceRoller.deterministic(3, 3)

        val (newState, events) = applyTwentyDamagePsrs(state, mapOf(unit.id to 20), roller)

        assertThat(events).isEmpty()
        assertThat(newState.unitById(unit.id)!!.isProne).isFalse()
    }

    @Test
    fun `applyTwentyDamagePsrs - failing the PSR causes a fall and pilot hit`() {
        val unit = aUnit(id = "unit-1", pilotingSkill = 5)
        val state = GameState(listOf(unit), GameMap(emptyMap()))
        // PSR TN = 5 + 1 = 6; roll (1,1)=2 < 6 → fail.
        // Fall: location (3,4)=7 → CENTER_TORSO; facing 1; consciousness (3,3)=6 ≥ 3 → conscious.
        val roller = DiceRoller.deterministic(1, 1, 3, 4, 1, 3, 3)

        val (newState, events) = applyTwentyDamagePsrs(state, mapOf(unit.id to 20), roller)

        val fallen = newState.unitById(unit.id)!!
        assertThat(fallen.isProne).isTrue()
        assertThat(fallen.pilotHits).isEqualTo(1)
        assertThat(events.filterIsInstance<UnitFell>()).hasSize(1)
        assertThat(events.filterIsInstance<PilotHit>()).hasSize(1)
    }

    @Test
    fun `applyTwentyDamagePsrs - 40 damage yields modifier +2 and higher TN`() {
        val unit = aUnit(id = "unit-1", pilotingSkill = 5)
        val state = GameState(listOf(unit), GameMap(emptyMap()))
        // PSR TN = 5 + 2 = 7; roll (3,3)=6 < 7 → fail.
        // Fall: location (3,4)=7 → CENTER_TORSO; facing 1; consciousness (3,3).
        val roller = DiceRoller.deterministic(3, 3, 3, 4, 1, 3, 3)

        val (newState, events) = applyTwentyDamagePsrs(state, mapOf(unit.id to 40), roller)

        assertThat(newState.unitById(unit.id)!!.isProne).isTrue()
        assertThat(events.filterIsInstance<UnitFell>()).hasSize(1)
    }

    @Test
    fun `applyTwentyDamagePsrs - already prone unit is skipped even if damage is at or above 20`() {
        val unit = aUnit(id = "unit-1", pilotingSkill = 5).copy(isProne = true)
        val state = GameState(listOf(unit), GameMap(emptyMap()))

        val (newState, events) = applyTwentyDamagePsrs(
            state,
            mapOf(unit.id to 20),
            DiceRoller.deterministic(), // must not consume any dice
        )

        assertThat(events).isEmpty()
        assertThat(newState.unitById(unit.id)!!.isProne).isTrue()
    }

    // ── Head IS penetration → 1 pilot hit ────────────────────────────────────

    @Test
    fun `resolveAttacks - head IS penetration inflicts one pilot hit`() {
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 4,
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(0, 0),
        )
        // Head armor = 0 so 5-damage laser punches straight through to IS.
        val target = aUnit(
            id = "target",
            armor = anArmorLayout(head = 0),
            internalStructure = anInternalStructureLayout(head = 3),
            position = HexCoordinates(1, 0),
        )
        val state = aGameState(units = listOf(attacker, target))

        // to-hit (4+4=8 ≥ TN 4 → hit); location (6+6=12 → HEAD).
        // HEAD IS penetration: consciousness 2d6 (3,3)=6 ≥ target 3 → conscious.
        // Crit check 2d6 (3,3)=6 → 0 crits (empty layout, no slots to pick).
        val roller = DiceRoller.deterministic(4, 4, 6, 6, 3, 3, 3, 3)

        val (newState, _, _) = resolveAttacksWithCrits(listOf(AttackDeclaration(attacker.id, target.id, 0, true)), state, roller)

        val updatedTarget = newState.unitById(target.id)!!
        assertThat(updatedTarget.pilotHits).isEqualTo(1)
    }

    @Test
    fun `resolveAttacks - head hit with armor intact does NOT inflict pilot hit`() {
        val attacker = aUnit(
            id = "attacker",
            gunnerySkill = 4,
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(0, 0),
        )
        // Head armor = 9 (default): laser hits armor only, no IS damage, no pilot hit.
        val target = aUnit(
            id = "target",
            armor = anArmorLayout(head = 9),
            position = HexCoordinates(1, 0),
        )
        val state = aGameState(units = listOf(attacker, target))

        // to-hit (4+4=8 → hit); location (6+6=12 → HEAD); no IS damage → no pilot hit.
        val roller = DiceRoller.deterministic(4, 4, 6, 6)

        val (newState, _, _) = resolveAttacksWithCrits(listOf(AttackDeclaration(attacker.id, target.id, 0, true)), state, roller)

        assertThat(newState.unitById(target.id)!!.pilotHits).isEqualTo(0)
    }

    // ── Ammo explosion (crit-triggered) → 2 pilot hits ────────────────────────

    @Test
    fun `resolveCriticalHits - ammo bin detonation inflicts exactly 2 pilot hits`() {
        val build = mechLayout {
            place(MechLocation.RIGHT_TORSO, WeaponModels.mediumLaser)
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            id = "unit-1",
            pilotHits = 0,
            armor = anArmorLayout(rightTorso = 0),
            internalStructure = anInternalStructureLayout(rightTorso = 21),
            weapons = build.weapons,
            criticalLayout = build.layout,
        )
        // 2d6 = 9 → 1 crit. Block 1 (upper), slot 2 → index 1 (the ammo bin).
        // Ammo explosion → pilot hit 1 (target 3; 3+3=6 ≥ 3 → conscious),
        //                 pilot hit 2 (target 5; 3+3=6 ≥ 5 → conscious).
        val roller = DiceRoller.deterministic(4, 5, 1, 2, 3, 3, 3, 3)

        val (updated, events) = resolveCriticalHits(unit, MechLocation.RIGHT_TORSO, roller)

        assertThat(events.filterIsInstance<AmmoExploded>()).hasSize(1)
        assertThat(events.filterIsInstance<PilotHit>()).hasSize(2)
        assertThat(updated.pilotHits).isEqualTo(2)
        assertThat(updated.isPilotConscious).isTrue()
    }

    @Test
    fun `resolveCriticalHits - ammo explosion knocks pilot unconscious if consciousness check fails`() {
        val build = mechLayout {
            place(MechLocation.RIGHT_TORSO, WeaponModels.mediumLaser)
            ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1)
        }
        val unit = aUnit(
            id = "unit-1",
            pilotHits = 0,
            armor = anArmorLayout(rightTorso = 0),
            internalStructure = anInternalStructureLayout(rightTorso = 21),
            weapons = build.weapons,
            criticalLayout = build.layout,
        )
        // 2d6 = 9 → 1 crit → ammo bin.
        // Pilot hit 1 (target 3; 1+1=2 < 3 → unconscious).
        // Pilot hit 2 (target 5; 1+1=2 < 5 → already unconscious, no new KO event).
        val roller = DiceRoller.deterministic(4, 5, 1, 2, 1, 1, 1, 1)

        val (updated, _) = resolveCriticalHits(unit, MechLocation.RIGHT_TORSO, roller)

        assertThat(updated.isPilotConscious).isFalse()
        assertThat(updated.pilotHits).isEqualTo(2)
    }
}
