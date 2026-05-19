package battletech.tactical.session

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.Impulse
import battletech.tactical.action.Initiative
import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackDeclaration
import battletech.tactical.command.CommandRejection
import battletech.tactical.command.CommandResult
import battletech.tactical.command.CommitAttackImpulse
import battletech.tactical.command.MoveUnit
import battletech.tactical.dice.DiceRoller
import battletech.tactical.event.AttackDeclarationsRecorded
import battletech.tactical.event.AttacksResolved
import battletech.tactical.event.TorsoFacingsApplied
import battletech.tactical.event.UnitMoved
import battletech.tactical.model.ArmorLayout
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.InternalStructureLayout
import battletech.tactical.model.MovementMode
import battletech.tactical.model.Weapons
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachableHex
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BattleSessionTest {

    private val mech1 = aMech(id = "m1", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
    private val mech2 = aMech(id = "m2", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 0))

    private fun newSession(
        units: List<CombatUnit> = listOf(mech1, mech2),
        turn: TurnState = TurnState(
            initiative = Initiative(
                rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
            ),
            movementSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1), Impulse(PlayerId.PLAYER_2, 1))),
        ),
        roller: DiceRoller = DiceRoller.seeded(42),
    ): BattleSession = BattleSession(
        initialGameState = GameState(units, GameMap(hexesFor(units))),
        initialTurnState = turn,
        roller = roller,
    )

    @Test
    fun `MoveUnit applies and advances turn state`() {
        val session = newSession()
        val destination = ReachableHex(
            position = HexCoordinates(1, 0),
            facing = HexDirection.NE,
            mpSpent = 1,
            path = listOf(MovementStep(HexCoordinates(0, 0), HexDirection.N), MovementStep(HexCoordinates(1, 0), HexDirection.NE)),
        )

        val result = session.submitCommand(
            MoveUnit(playerId = PlayerId.PLAYER_1, unitId = mech1.id, destination = destination, mode = MovementMode.WALK),
        )

        assertThat(result).isInstanceOf(CommandResult.Accepted::class.java)
        val accepted = result as CommandResult.Accepted
        assertThat(accepted.events).hasSize(1)
        val moved = accepted.events.single() as UnitMoved
        assertThat(moved.unitId).isEqualTo(mech1.id)
        assertThat(moved.from).isEqualTo(HexCoordinates(0, 0))
        assertThat(moved.to).isEqualTo(HexCoordinates(1, 0))
        assertThat(moved.mode).isEqualTo(MovementMode.WALK)
        assertThat(session.gameState.unitById(mech1.id)!!.position).isEqualTo(HexCoordinates(1, 0))
        assertThat(session.turnState.movedUnitIds).contains(mech1.id)
    }

    @Test
    fun `MoveUnit rejects UnknownUnit`() {
        val session = newSession()
        val result = session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, UnitId("ghost"), aReachableHex(), MovementMode.WALK),
        )
        val rejected = result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.UnknownUnit::class.java)
    }

    @Test
    fun `MoveUnit rejects when player does not own the unit`() {
        val session = newSession()
        val result = session.submitCommand(
            MoveUnit(PlayerId.PLAYER_2, mech1.id, aReachableHex(), MovementMode.WALK),
        )
        val rejected = result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.NotYourTurn::class.java)
    }

    @Test
    fun `MoveUnit rejects when the unit has already moved this turn`() {
        val session = newSession(
            turn = TurnState(
                initiative = Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
                movedUnitIds = setOf(mech1.id),
            ),
        )
        val result = session.submitCommand(
            MoveUnit(PlayerId.PLAYER_1, mech1.id, aReachableHex(), MovementMode.WALK),
        )
        val rejected = result as CommandResult.Rejected
        assertThat(rejected.reason).isInstanceOf(CommandRejection.UnitAlreadyActed::class.java)
    }

    @Test
    fun `CommitAttackImpulse records declarations and applies torso facings (non-final, weapon phase)`() {
        val session = newSession(
            turn = TurnState(
                initiative = Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(emptyList()),
                attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1), Impulse(PlayerId.PLAYER_2, 1))),
            ),
        )
        val decls = listOf(AttackDeclaration(mech1.id, mech2.id, 0, true))

        val result = session.submitCommand(
            CommitAttackImpulse(
                playerId = PlayerId.PLAYER_1,
                isWeaponPhase = true,
                declarations = decls,
                torsoFacings = mapOf(mech1.id to HexDirection.NE),
            ),
        )

        val accepted = result as CommandResult.Accepted
        assertThat(accepted.events.filterIsInstance<TorsoFacingsApplied>()).hasSize(1)
        assertThat(accepted.events.filterIsInstance<AttackDeclarationsRecorded>()).hasSize(1)
        assertThat(accepted.events.filterIsInstance<AttacksResolved>()).isEmpty()

        assertThat(session.turnState.attackDeclarations).containsExactlyElementsOf(decls)
        assertThat(session.turnState.attackImpulse).isNull()
        assertThat(session.gameState.unitById(mech1.id)!!.torsoFacing).isEqualTo(HexDirection.NE)
    }

    @Test
    fun `CommitAttackImpulse resolves attacks on the final weapon-phase impulse`() {
        val attacker = aMech(id = "a", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
        val target = aMech(id = "t", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 0))
        val session = newSession(
            units = listOf(attacker, target),
            turn = TurnState(
                initiative = Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(emptyList()),
                attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
            ),
        )

        val result = session.submitCommand(
            CommitAttackImpulse(
                playerId = PlayerId.PLAYER_1,
                isWeaponPhase = true,
                declarations = listOf(AttackDeclaration(attacker.id, target.id, 0, true)),
                torsoFacings = emptyMap(),
            ),
        )

        val accepted = result as CommandResult.Accepted
        assertThat(accepted.events.filterIsInstance<AttacksResolved>()).hasSize(1)
        assertThat(session.turnState.attackDeclarations).isEmpty()
        assertThat(session.turnState.attackSequence.isComplete).isTrue()
    }

    @Test
    fun `CommitAttackImpulse on physical-phase final impulse drops declarations without resolving`() {
        val session = newSession(
            turn = TurnState(
                initiative = Initiative(
                    rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
                    loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
                ),
                movementSequence = ImpulseSequence(emptyList()),
                attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
            ),
        )

        val result = session.submitCommand(
            CommitAttackImpulse(
                playerId = PlayerId.PLAYER_1,
                isWeaponPhase = false,
                declarations = listOf(AttackDeclaration(mech1.id, mech2.id, 0, true)),
                torsoFacings = emptyMap(),
            ),
        )

        val accepted = result as CommandResult.Accepted
        assertThat(accepted.events.filterIsInstance<AttacksResolved>()).isEmpty()
        assertThat(session.turnState.attackDeclarations).isEmpty()
    }

    @Test
    fun `viewFor returns a PlayerView scoped to the requested player`() {
        val session = newSession()
        val view = session.viewFor(PlayerId.PLAYER_1)
        assertThat(view.playerId).isEqualTo(PlayerId.PLAYER_1)
        assertThat(view.state.units).containsExactlyElementsOf(session.gameState.units)
    }

    // ---------- helpers ----------

    private fun aMech(
        id: String,
        owner: PlayerId,
        position: HexCoordinates,
    ): CombatUnit = CombatUnit(
        id = UnitId(id),
        owner = owner,
        name = id,
        gunnerySkill = 4,
        pilotingSkill = 5,
        weapons = listOf(Weapons.mediumLaser()),
        position = position,
        facing = HexDirection.N,
        torsoFacing = HexDirection.N,
        walkingMP = 4,
        runningMP = 6,
        jumpMP = 0,
        currentHeat = 0,
        heatSinkCapacity = 10,
        armor = ArmorLayout(
            head = 9,
            centerTorso = 30, centerTorsoRear = 10,
            leftTorso = 25, leftTorsoRear = 8,
            rightTorso = 25, rightTorsoRear = 8,
            leftArm = 20, rightArm = 20,
            leftLeg = 25, rightLeg = 25,
        ),
        internalStructure = InternalStructureLayout(
            head = 3,
            centerTorso = 31,
            leftTorso = 21,
            rightTorso = 21,
            leftArm = 17,
            rightArm = 17,
            leftLeg = 21,
            rightLeg = 21,
        ),
    )

    private fun hexesFor(units: List<CombatUnit>): Map<HexCoordinates, Hex> {
        // Include every unit position plus a small margin so adjacent moves resolve.
        val coords = units.flatMap { u ->
            listOf(u.position) + HexDirection.entries.map { u.position.neighbor(it) }
        }
        return coords.distinct().associateWith { Hex(it) }
    }

    private fun aReachableHex(): ReachableHex = ReachableHex(
        position = HexCoordinates(1, 0),
        facing = HexDirection.N,
        mpSpent = 1,
        path = emptyList(),
    )
}
