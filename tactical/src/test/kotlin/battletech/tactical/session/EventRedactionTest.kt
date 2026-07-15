package battletech.tactical.session

import battletech.tactical.attack.FallResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.physical.AttackDirection
import battletech.tactical.attack.physical.Knockdown
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.DestructionReason
import battletech.tactical.unit.PilotingSkillRoll
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [GameEvent.redactFor] is the single seam both the log ([BattleSession.logFor]) and the
 * wire (Stage 4) redact through. These tests pin its three outcomes (verbatim / redacted /
 * suppressed) directly against events, independent of any formatter or rendering.
 */
internal class EventRedactionTest {

    private val ownedUnit = aUnit(id = "own", owner = PlayerId.PLAYER_1)
    private val foreignUnit = aUnit(id = "foreign", owner = PlayerId.PLAYER_2)
    private val state = aGameState(units = listOf(ownedUnit, foreignUnit))

    private fun redact(event: GameEvent, viewer: PlayerId? = PlayerId.PLAYER_1, revealAll: Boolean = false): GameEvent? =
        event.redactFor(viewer, state, revealAll)

    private fun aPsr(targetNumber: Int = 7): PilotingSkillRoll =
        PilotingSkillRoll(targetNumber = targetNumber, roll = DiceRoll(4, 4), passed = true)

    // ---------- Cross-cutting: null viewer, revealAll ----------

    @Test
    fun `null viewer redacts even the viewer's own units - fails closed`() {
        val event = CriticalHit.Detailed(ownedUnit.id, MechLocation.CENTER_TORSO, 0, CriticalSlotContent.Engine)

        assertThat(redact(event, viewer = null)).isEqualTo(CriticalHit.Undisclosed(ownedUnit.id))
    }

    @Test
    fun `revealAll returns every event verbatim regardless of ownership`() {
        val event = CriticalHit.Detailed(foreignUnit.id, MechLocation.CENTER_TORSO, 0, CriticalSlotContent.Engine)

        assertThat(redact(event, viewer = PlayerId.PLAYER_1, revealAll = true)).isEqualTo(event)
    }

    // ---------- CriticalHit ----------

    @Test
    fun `CriticalHit stays Detailed for the owner`() {
        val event = CriticalHit.Detailed(ownedUnit.id, MechLocation.CENTER_TORSO, 0, CriticalSlotContent.Engine)

        assertThat(redact(event)).isEqualTo(event)
    }

    @Test
    fun `CriticalHit becomes Undisclosed for a foreign unit`() {
        val event = CriticalHit.Detailed(foreignUnit.id, MechLocation.RIGHT_ARM, 5, CriticalSlotContent.Gyro)

        assertThat(redact(event)).isEqualTo(CriticalHit.Undisclosed(foreignUnit.id))
    }

    // ---------- AmmoExploded ----------

    @Test
    fun `AmmoExploded stays Detailed for the owner`() {
        val event = AmmoExploded.Detailed(ownedUnit.id, AmmoType.AC20, 100)

        assertThat(redact(event)).isEqualTo(event)
    }

    @Test
    fun `AmmoExploded drops the ammo type but keeps damage for a foreign unit`() {
        val event = AmmoExploded.Detailed(foreignUnit.id, AmmoType.AC20, 100)

        assertThat(redact(event)).isEqualTo(AmmoExploded.Undisclosed(foreignUnit.id, damage = 100))
    }

    // ---------- UnitStoodUp ----------

    @Test
    fun `UnitStoodUp stays Detailed for the owner`() {
        val event = UnitStoodUp.Detailed(ownedUnit.id, aPsr(), stoodUp = true)

        assertThat(redact(event)).isEqualTo(event)
    }

    @Test
    fun `UnitStoodUp drops the PSR but keeps stoodUp for a foreign unit`() {
        val event = UnitStoodUp.Detailed(foreignUnit.id, aPsr(), stoodUp = false)

        assertThat(redact(event)).isEqualTo(UnitStoodUp.Undisclosed(foreignUnit.id, stoodUp = false))
    }

    // ---------- UnitShutdown / UnitRestarted ----------

    @Test
    fun `UnitShutdown Automatic and AvoidFailed stay verbatim for the owner`() {
        assertThat(redact(UnitShutdown.Automatic(ownedUnit.id))).isEqualTo(UnitShutdown.Automatic(ownedUnit.id))
        assertThat(redact(UnitShutdown.AvoidFailed(ownedUnit.id, DiceRoll(3, 3))))
            .isEqualTo(UnitShutdown.AvoidFailed(ownedUnit.id, DiceRoll(3, 3)))
    }

    @Test
    fun `UnitShutdown collapses to Undisclosed for a foreign unit, hiding which mechanism fired`() {
        assertThat(redact(UnitShutdown.Automatic(foreignUnit.id))).isEqualTo(UnitShutdown.Undisclosed(foreignUnit.id))
        assertThat(redact(UnitShutdown.AvoidFailed(foreignUnit.id, DiceRoll(3, 3))))
            .isEqualTo(UnitShutdown.Undisclosed(foreignUnit.id))
    }

    @Test
    fun `UnitRestarted collapses to Undisclosed for a foreign unit`() {
        assertThat(redact(UnitRestarted.Automatic(foreignUnit.id))).isEqualTo(UnitRestarted.Undisclosed(foreignUnit.id))
        assertThat(redact(UnitRestarted.RollPassed(foreignUnit.id, DiceRoll(5, 6))))
            .isEqualTo(UnitRestarted.Undisclosed(foreignUnit.id))
    }

    // ---------- PilotHit ----------

    @Test
    fun `PilotHit Fatal and Checked stay verbatim for the owner`() {
        assertThat(redact(PilotHit.Fatal(ownedUnit.id, pilotHits = 6))).isEqualTo(PilotHit.Fatal(ownedUnit.id, pilotHits = 6))
        val checked = PilotHit.Checked(ownedUnit.id, pilotHits = 2, consciousnessRoll = DiceRoll(3, 3), conscious = true)
        assertThat(redact(checked)).isEqualTo(checked)
    }

    @Test
    fun `PilotHit collapses to Undisclosed for a foreign unit, dropping the running total and roll`() {
        assertThat(redact(PilotHit.Fatal(foreignUnit.id, pilotHits = 6))).isEqualTo(PilotHit.Undisclosed(foreignUnit.id))
        val checked = PilotHit.Checked(foreignUnit.id, pilotHits = 2, consciousnessRoll = DiceRoll(3, 3), conscious = true)
        assertThat(redact(checked)).isEqualTo(PilotHit.Undisclosed(foreignUnit.id))
    }

    // ---------- HeatDissipated ----------

    @Test
    fun `HeatDissipated keeps only the viewer's own unit entries`() {
        val event = HeatDissipated(
            heatBefore = mapOf(ownedUnit.id to 12, foreignUnit.id to 8),
            heatAfter = mapOf(ownedUnit.id to 4, foreignUnit.id to 2),
        )

        assertThat(redact(event)).isEqualTo(
            HeatDissipated(heatBefore = mapOf(ownedUnit.id to 12), heatAfter = mapOf(ownedUnit.id to 4)),
        )
    }

    @Test
    fun `HeatDissipated is suppressed when the viewer owns none of the units that dissipated heat`() {
        val event = HeatDissipated(heatBefore = mapOf(foreignUnit.id to 8), heatAfter = mapOf(foreignUnit.id to 2))

        assertThat(redact(event)).isNull()
    }

    @Test
    fun `HeatDissipated with a genuinely empty map is NOT suppressed`() {
        val event = HeatDissipated(heatBefore = emptyMap(), heatAfter = emptyMap())

        assertThat(redact(event)).isEqualTo(event)
    }

    // ---------- Events with no private data: verbatim for everyone ----------

    @Test
    fun `UnitMoved, UnitFell, and UnitDestroyed are never redacted`() {
        val moved = UnitMoved(foreignUnit.id, foreignUnit.position, foreignUnit.position, HexDirection.N, MovementMode.WALK, 3)
        val fell = UnitFell(foreignUnit.id, aFallResult())
        val destroyed = UnitDestroyed(foreignUnit.id, DestructionReason.ENGINE_DESTROYED)

        assertThat(redact(moved)).isEqualTo(moved)
        assertThat(redact(fell)).isEqualTo(fell)
        assertThat(redact(destroyed)).isEqualTo(destroyed)
    }

    // ---------- PhysicalAttacksResolved / Knockdown ----------

    @Test
    fun `PhysicalAttacksResolved keeps the Knockdown PSR when the faller is the viewer's own unit`() {
        val hit = PhysicalAttackResult.Hit(
            attackerId = foreignUnit.id,
            targetId = ownedUnit.id,
            attackName = "Kick",
            hitLocation = HitLocation.LEFT_LEG,
            damageApplied = 4,
            targetNumber = 6,
            toHitRoll = DiceRoll(4, 4),
            locationRoll = 5,
            attackDirection = AttackDirection.FRONT,
            knockdown = Knockdown.Fell.Detailed(unitId = ownedUnit.id, psr = aPsr(), fall = aFallResult()),
        )
        val event = PhysicalAttacksResolved(results = listOf(hit))

        assertThat(redact(event)).isEqualTo(event)
    }

    @Test
    fun `PhysicalAttacksResolved redacts the Knockdown PSR when the faller is foreign, keeping the fall`() {
        val fall = aFallResult()
        val hit = PhysicalAttackResult.Hit(
            attackerId = ownedUnit.id,
            targetId = foreignUnit.id,
            attackName = "Kick",
            hitLocation = HitLocation.LEFT_LEG,
            damageApplied = 4,
            targetNumber = 6,
            toHitRoll = DiceRoll(4, 4),
            locationRoll = 5,
            attackDirection = AttackDirection.FRONT,
            knockdown = Knockdown.Fell.Detailed(unitId = foreignUnit.id, psr = aPsr(), fall = fall),
        )
        val event = PhysicalAttacksResolved(results = listOf(hit))

        val redacted = redact(event) as PhysicalAttacksResolved
        val knockdown = redacted.results.single().knockdown
        assertThat(knockdown).isEqualTo(Knockdown.Fell.Undisclosed(unitId = foreignUnit.id, fall = fall))
    }

    @Test
    fun `PhysicalAttacksResolved redacts a Resisted Knockdown for the attacker's own miss when the faller is foreign`() {
        // A miss's knockdown check falls on the attacker, not the target.
        val miss = PhysicalAttackResult.Miss(
            attackerId = foreignUnit.id,
            targetId = ownedUnit.id,
            attackName = "Kick",
            targetNumber = 6,
            toHitRoll = DiceRoll(2, 3),
            attackDirection = AttackDirection.FRONT,
            knockdown = Knockdown.Resisted.Detailed(psr = aPsr()),
        )
        val event = PhysicalAttacksResolved(results = listOf(miss))

        val redacted = redact(event) as PhysicalAttacksResolved
        assertThat(redacted.results.single().knockdown).isEqualTo(Knockdown.Resisted.Undisclosed)
    }

    @Test
    fun `PhysicalAttacksResolved recursively redacts pilotEvents nested in a foreign Knockdown Fell`() {
        val pilotHit = PilotHit.Checked(foreignUnit.id, pilotHits = 1, consciousnessRoll = DiceRoll(3, 3), conscious = true)
        val hit = PhysicalAttackResult.Hit(
            attackerId = ownedUnit.id,
            targetId = foreignUnit.id,
            attackName = "Kick",
            hitLocation = HitLocation.LEFT_LEG,
            damageApplied = 4,
            targetNumber = 6,
            toHitRoll = DiceRoll(4, 4),
            locationRoll = 5,
            attackDirection = AttackDirection.FRONT,
            knockdown = Knockdown.Fell.Detailed(unitId = foreignUnit.id, psr = aPsr(), fall = aFallResult(), pilotEvents = listOf(pilotHit)),
        )
        val event = PhysicalAttacksResolved(results = listOf(hit))

        val redacted = redact(event) as PhysicalAttacksResolved
        val fell = redacted.results.single().knockdown as Knockdown.Fell.Undisclosed
        assertThat(fell.pilotEvents).containsExactly(PilotHit.Undisclosed(foreignUnit.id))
    }

    private fun aFallResult(): FallResult = FallResult(
        damage = 5,
        hitLocation = HitLocation.CENTER_TORSO,
        locationRoll = DiceRoll(3, 4),
        newFacing = HexDirection.SE,
        facingRoll = 3,
    )

}
