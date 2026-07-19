package battletech.tactical.session

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.weapon.FiringArc
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachableHex
import battletech.tactical.query.aGameState
import battletech.tactical.query.aUnit
import battletech.tactical.query.anArmorLayout
import battletech.tactical.query.anInternalStructureLayout
import battletech.tactical.query.mediumLaser
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.DestructionReason
import battletech.tactical.unit.UnitRoster
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pins [BattleSession]'s centralized destruction sweep: it must catch kills
 * from any source (here, weapon damage applied via [CommitAttackImpulse]),
 * flip [battletech.tactical.unit.CombatUnit.isDestroyed], emit [UnitDestroyed],
 * end the match with [MatchEnded] when a side has no survivors, halt the
 * cascade, reject further commands, and stay idempotent when nothing new
 * was destroyed.
 */
internal class DestructionSweepTest {

    // Adjacent units (distance 1, short range for a medium laser) so the
    // attacker is guaranteed to be in range; we loop dice seeds to land a
    // CENTER_TORSO hit.
    private val attacker = aUnit(
        id = "attacker",
        owner = PlayerId.PLAYER_1,
        position = HexCoordinates(0, 0),
        weapons = listOf(mediumLaser()),
        gunnerySkill = 4,
    )

    // Thin CT: 0 armor, 1 IS point — a single medium laser hit (5 dmg) drops it to 0.
    private val target = aUnit(
        id = "target",
        owner = PlayerId.PLAYER_2,
        position = HexCoordinates(1, 0),
        facing = FiringArc.bearingDirection(HexCoordinates(1, 0), HexCoordinates(0, 0)),
        weapons = listOf(mediumLaser()),
        armor = anArmorLayout(centerTorso = 0, centerTorsoRear = 0),
        internalStructure = anInternalStructureLayout(centerTorso = 1),
    )

    private fun freshSession(roller: DiceRoller): BattleSession = BattleSession(
        initialGameState = GameState(
            UnitRoster(listOf(attacker, target)),
            GameMap(
                mapOf(
                    HexCoordinates(0, 0) to battletech.tactical.model.Hex(HexCoordinates(0, 0)),
                    HexCoordinates(1, 0) to battletech.tactical.model.Hex(HexCoordinates(1, 0)),
                ),
            ),
        ),
        initialTurnState = TurnState.NULL,
        roller = roller,
    )

    private fun stayPut(
        position: HexCoordinates,
        facing: battletech.tactical.model.HexDirection = battletech.tactical.model.HexDirection.N,
    ): ReachableHex = ReachableHex(
        position = position,
        facing = facing,
        mpSpent = 0,
        path = listOf(MovementStep(position, facing)),
    )

    /**
     * Drives a session from a fresh [TurnState.NULL] to a [CommandResult] for
     * the attacker's CommitAttackImpulse that should resolve the volley (the
     * final impulse, attacker firing on target). [extraDice] is the scripted
     * 2d6 pair (to-hit, location) appended after the two initiative rolls
     * (4 dice) consumed by [BattleSession.advance]. The hit always drops the
     * target's thin CT IS to 0 (>=1 structure damage), which now fires a crit
     * check (`docs/rules/armor-damage.md` §3) — a trailing (1,1) roll (2d6=2,
     * the table's "no crit" band) is appended so it consumes no further dice.
     */
    private fun driveToAttack(
        toHit: Pair<Int, Int>,
        location: Pair<Int, Int>,
    ): Pair<BattleSession, CommandResult> {
        // Initiative dice: P1 (2,3)=5, P2(4,4)=8 -> P1 loses, P2 wins. Matches
        // the convention used elsewhere in this test suite (SessionTestFixtures).
        val roller = DiceRoller.deterministic(
            2, 3,
            4, 4,
            toHit.first, toHit.second,
            location.first, location.second,
            1, 1,
        )
        val session = freshSession(roller)
        session.advance()

        val loser = session.turnState.initiative.loser
        val winner = session.turnState.initiative.winner
        val loserUnit = if (loser == PlayerId.PLAYER_1) attacker else target
        val winnerUnit = if (winner == PlayerId.PLAYER_1) attacker else target

        check(
            session.submitCommand(
                MoveUnit(loser, loserUnit.id, stayPut(loserUnit.position, loserUnit.facing), MovementMode.WALK),
            ) is CommandResult.Accepted,
        )
        check(
            session.submitCommand(
                MoveUnit(winner, winnerUnit.id, stayPut(winnerUnit.position, winnerUnit.facing), MovementMode.WALK),
            ) is CommandResult.Accepted,
        )

        assertThat(session.currentPhase).isEqualTo(TurnPhase.WEAPON_ATTACK)

        // Attacker (P1) declares against target (P2); the other player commits empty.
        val attackLoserDeclares = loser == PlayerId.PLAYER_1
        val firstPlayer = loser
        val secondPlayer = winner

        val firstDeclarations =
            if (attackLoserDeclares) listOf(AttackDeclaration(attacker.id, target.id, 0, true)) else emptyList()
        val r1 = session.submitCommand(CommitAttackImpulse(firstPlayer, firstDeclarations, emptyMap()))
        check(r1 is CommandResult.Accepted) { "impulse 1 failed: $r1" }

        val secondDeclarations =
            if (!attackLoserDeclares) listOf(AttackDeclaration(attacker.id, target.id, 0, true)) else emptyList()
        val r2 = session.submitCommand(CommitAttackImpulse(secondPlayer, secondDeclarations, emptyMap()))

        return session to r2
    }

    @Test
    fun `weapon damage that zeroes CT destroys the unit and emits UnitDestroyed`() {
        // to-hit total 9 (hit vs TN 4), location total 7 -> CENTER_TORSO.
        val (session, result) = driveToAttack(toHit = 4 to 5, location = 3 to 4)

        check(result is CommandResult.Accepted) { "attack impulse rejected: $result" }
        val destroyed = result.events.filterIsInstance<UnitDestroyed>()
        assertThat(destroyed).hasSize(1)
        assertThat(destroyed.single().unitId).isEqualTo(target.id)
        assertThat(destroyed.single().reason).isEqualTo(DestructionReason.CENTER_TORSO_DESTROYED)

        val updatedTarget = session.gameState.units.byId(target.id)!!
        assertThat(updatedTarget.isDestroyed).isTrue()
    }

    @Test
    fun `destroying the last unit of a side ends the match with the other player as winner`() {
        val (session, result) = driveToAttack(toHit = 4 to 5, location = 3 to 4)
        check(result is CommandResult.Accepted) { "attack impulse rejected: $result" }

        val matchEnded = result.events.filterIsInstance<MatchEnded>()
        assertThat(matchEnded).hasSize(1)
        assertThat(matchEnded.single().outcome).isEqualTo(MatchOutcome.Victory(PlayerId.PLAYER_1))
        assertThat(session.isMatchOver).isTrue()

        // Further commands are rejected once the match is over.
        val rejected = session.submitCommand(
            CommitPhysicalAttackImpulse(PlayerId.PLAYER_1, emptyList(), emptyMap()),
        )
        assertThat(rejected).isInstanceOf(CommandResult.Rejected::class.java)
        assertThat((rejected as CommandResult.Rejected).reason).isEqualTo(CommandRejection.MatchOver)
    }

    @Test
    fun `cascade halts at match end and does not advance phases further`() {
        val (session, result) = driveToAttack(toHit = 4 to 5, location = 3 to 4)
        check(result is CommandResult.Accepted) { "attack impulse rejected: $result" }

        // The session must not have spun through HEAT/END/INITIATIVE forever;
        // it should be stuck wherever the match-over short-circuit caught it,
        // with no further PhaseChanged events beyond what's already recorded.
        val phaseBefore = session.currentPhase
        val turnBefore = session.turnState

        // Calling advance() again must be a no-op (matchOver guard).
        val moreEvents = session.advance()
        assertThat(moreEvents).isEmpty()
        assertThat(session.currentPhase).isEqualTo(phaseBefore)
        assertThat(session.turnState).isEqualTo(turnBefore)
    }

    @Test
    fun `a unit reaching 3 engine crits is flagged destroyed with ENGINE_DESTROYED via the sweep`() {
        // Construct criticalHits directly (Stage 5 is about consequences, not crit
        // dice) rather than scripting dice to land 3 crit picks on engine slots.
        // CENTER_TORSO framework: Engine at indices 0,1,2 and 7,8,9.
        val engineDestroyed = aUnit(
            id = "a",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(0, 0),
        ).copy(criticalHits = mapOf(battletech.tactical.model.MechLocation.CENTER_TORSO to setOf(0, 1, 2)))
        val other = aUnit(id = "b", owner = PlayerId.PLAYER_2, position = HexCoordinates(5, 0))
        val session = BattleSession(
            initialGameState = aGameState(units = listOf(engineDestroyed, other)),
            initialTurnState = TurnState.NULL,
            roller = DiceRoller.deterministic(2, 3, 4, 4),
        )

        val events = session.advance()

        val destroyed = events.filterIsInstance<UnitDestroyed>()
        assertThat(destroyed).hasSize(1)
        assertThat(destroyed.single().unitId).isEqualTo(engineDestroyed.id)
        assertThat(destroyed.single().reason).isEqualTo(DestructionReason.ENGINE_DESTROYED)
        assertThat(session.gameState.units.byId(engineDestroyed.id)!!.isDestroyed).isTrue()
    }

    @Test
    fun `a pilot driven to 6 hits is flagged destroyed with PILOT_DEAD and ends the match via the sweep`() {
        // pilotHits = 5 entering the Heat Phase, with 2 life-support crits so the
        // unit takes its 6th (fatal) hit unconditionally, regardless of heat.
        // At the death threshold, applyPilotHit rolls no consciousness check.
        // Units placed far apart (>9 hexes, beyond medium laser range) so empty
        // weapon/physical declarations are legal throughout, mirroring
        // PhaseCascadeIntegrationTest's drive-to-HEAT pattern.
        val dyingPilot = aUnit(
            id = "a",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(0, 0),
        ).copy(
            criticalHits = mapOf(battletech.tactical.model.MechLocation.HEAD to setOf(0, 5)),
            pilotHits = 5,
        )
        val other = aUnit(id = "b", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 12))
        val roller = DiceRoller.deterministic(
            2, 3, // P1 initiative roll: 5
            4, 4, // P2 initiative roll: 8 -> P1 loses
        )
        val session = BattleSession(
            initialGameState = GameState(
                UnitRoster(listOf(dyingPilot, other)),
                GameMap(hexesFor(listOf(dyingPilot, other))),
            ),
            initialTurnState = TurnState.NULL,
            roller = roller,
        )

        session.advance()
        val loser = session.turnState.initiative.loser
        val winner = session.turnState.initiative.winner
        val loserUnit = if (loser == PlayerId.PLAYER_1) dyingPilot else other
        val winnerUnit = if (winner == PlayerId.PLAYER_1) dyingPilot else other

        check(
            session.submitCommand(MoveUnit(loser, loserUnit.id, stayPut(loserUnit.position), MovementMode.WALK))
                is CommandResult.Accepted,
        )
        check(
            session.submitCommand(MoveUnit(winner, winnerUnit.id, stayPut(winnerUnit.position), MovementMode.WALK))
                is CommandResult.Accepted,
        )

        check(session.submitCommand(CommitAttackImpulse(loser, emptyList(), emptyMap())) is CommandResult.Accepted)
        check(session.submitCommand(CommitAttackImpulse(winner, emptyList(), emptyMap())) is CommandResult.Accepted)

        // Final physical impulse cascades through HEAT (where the fatal life-support
        // hit fires) -> sweep -> END -> MatchEnded.
        check(
            session.submitCommand(CommitPhysicalAttackImpulse(loser, emptyList(), emptyMap()))
                is CommandResult.Accepted,
        )
        val finalResult = session.submitCommand(CommitPhysicalAttackImpulse(winner, emptyList(), emptyMap()))
        check(finalResult is CommandResult.Accepted) { "final physical impulse rejected: $finalResult" }

        val destroyed = finalResult.events.filterIsInstance<UnitDestroyed>()
        assertThat(destroyed).hasSize(1)
        assertThat(destroyed.single().unitId).isEqualTo(dyingPilot.id)
        assertThat(destroyed.single().reason).isEqualTo(DestructionReason.PILOT_DEAD)
        assertThat(session.gameState.units.byId(dyingPilot.id)!!.isDestroyed).isTrue()

        val matchEnded = finalResult.events.filterIsInstance<MatchEnded>()
        assertThat(matchEnded).hasSize(1)
        assertThat(matchEnded.single().outcome).isEqualTo(MatchOutcome.Victory(other.owner))
        assertThat(session.isMatchOver).isTrue()
    }

    @Test
    fun `stateFor reveals every unit as the CombatUnit itself once the match is over, regardless of viewer`() {
        val (session, result) = driveToAttack(toHit = 4 to 5, location = 3 to 4)
        check(result is CommandResult.Accepted) { "attack impulse rejected: $result" }
        check(session.isMatchOver) { "expected the match to be over" }

        for (viewer in listOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2, null)) {
            val projected = session.stateFor(viewer)
            assertThat(projected.units).allSatisfy { assertThat(it).isInstanceOf(CombatUnit::class.java) }
        }
    }

    @Test
    fun `sweep is idempotent when nothing is newly destroyed`() {
        val intactAttacker = aUnit(id = "a", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
        val intactTarget = aUnit(id = "b", owner = PlayerId.PLAYER_2, position = HexCoordinates(5, 0))
        val session = BattleSession(
            initialGameState = aGameState(units = listOf(intactAttacker, intactTarget)),
            initialTurnState = TurnState.NULL,
            roller = DiceRoller.deterministic(2, 3, 4, 4),
        )

        val events = session.advance()

        assertThat(events.filterIsInstance<UnitDestroyed>()).isEmpty()
        assertThat(events.filterIsInstance<MatchEnded>()).isEmpty()
        assertThat(session.isMatchOver).isFalse()
    }
}
