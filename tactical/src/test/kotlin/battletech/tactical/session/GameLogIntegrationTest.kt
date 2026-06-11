package battletech.tactical.session

import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachableHex
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.InternalStructureLayout
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapons
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GameLogIntegrationTest {

    private val mech1 = aMech("m1", PlayerId.PLAYER_1, HexCoordinates(0, 0))
    private val mech2 = aMech("m2", PlayerId.PLAYER_2, HexCoordinates(3, 0))

    @Test
    fun `a freshly constructed session has an empty game log`() {
        val session = freshSession()

        assertThat(session.gameLog.snapshot()).isEmpty()
    }

    @Test
    fun `advance kickstart appends entries for emitted events`() {
        val session = freshSession()

        session.advance()

        val events = session.gameLog.snapshot().map { it.event }
        assertThat(events.any { it is InitiativeRolled }).isTrue()
    }

    @Test
    fun `all events including phase changes are stored in the log`() {
        val session = freshSession()

        session.advance()

        val events = session.gameLog.snapshot().map { it.event }
        assertThat(events.any { it is PhaseChanged }).isTrue()
    }

    @Test
    fun `submitCommand appends entries for emitted events`() {
        val session = sessionInMovement()
        val destination = ReachableHex(
            position = HexCoordinates(1, 0),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(
                MovementStep(HexCoordinates(0, 0), HexDirection.N),
                MovementStep(HexCoordinates(1, 0), HexDirection.N),
            ),
        )

        session.submitCommand(MoveUnit(PlayerId.PLAYER_1, mech1.id, destination, MovementMode.WALK))

        val events = session.gameLog.snapshot().map { it.event }
        assertThat(events.any { it is UnitMoved && it.unitId == mech1.id }).isTrue()
    }

    @Test
    fun `TurnEnded log entry is labeled with the turn that ended, not the next turn`() {
        // End-phase cascade: HEAT (last phase before END) → END → INITIATIVE → MOVEMENT.
        // We construct the session at HEAT with an empty turn state so the cascade
        // rolls through end-of-turn into a fresh turn 2.
        val turn = TurnState.NULL.copy(turnNumber = 1)
        val session = BattleSession(
            initialGameState = GameState(listOf(mech1, mech2), GameMap(hexesFor(listOf(mech1, mech2)))),
            initialTurnState = turn,
            roller = DiceRoller.seeded(42),
            initialPhase = TurnPhase.HEAT,
            initialNeedsOnEntry = true,
        )

        session.advance()

        val turnEnded = session.gameLog.snapshot().single { it.event is TurnEnded }
        assertThat((turnEnded.event as TurnEnded).turnNumber).isEqualTo(1)
        assertThat(turnEnded.turn).isEqualTo(1)
    }

    @Test
    fun `log entries carry the current turn number`() {
        val session = sessionInMovement(
            turn = aMovementTurn().copy(turnNumber = 3),
        )
        val destination = ReachableHex(
            position = HexCoordinates(1, 0),
            facing = HexDirection.N,
            mpSpent = 1,
            path = listOf(
                MovementStep(HexCoordinates(0, 0), HexDirection.N),
                MovementStep(HexCoordinates(1, 0), HexDirection.N),
            ),
        )

        session.submitCommand(MoveUnit(PlayerId.PLAYER_1, mech1.id, destination, MovementMode.WALK))

        val newEntries = session.gameLog.snapshot()
        assertThat(newEntries).isNotEmpty
        assertThat(newEntries.last().turn).isEqualTo(3)
    }

    private fun freshSession(): BattleSession = BattleSession(
        initialGameState = GameState(listOf(mech1, mech2), GameMap(hexesFor(listOf(mech1, mech2)))),
        initialTurnState = TurnState.NULL,
        roller = DiceRoller.seeded(42),
    )

    private fun aMovementTurn(): TurnState = TurnState(
        initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
            loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
        ),
        movement = MovementProgress(
            sequence = ImpulseSequence(
                listOf(Impulse(PlayerId.PLAYER_1, 1), Impulse(PlayerId.PLAYER_2, 1)),
            ),
        ),
    )

    private fun sessionInMovement(turn: TurnState = aMovementTurn()): BattleSession = BattleSession(
        initialGameState = GameState(listOf(mech1, mech2), GameMap(hexesFor(listOf(mech1, mech2)))),
        initialTurnState = turn,
        roller = DiceRoller.seeded(42),
        initialPhase = TurnPhase.MOVEMENT,
        initialNeedsOnEntry = false,
    )

    private fun aMech(id: String, owner: PlayerId, position: HexCoordinates): CombatUnit = CombatUnit(
        id = UnitId(id),
        owner = owner,
        name = id,
        tonnage = 50,
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
        heatSink = HeatSink(HeatSinkType.STS, 10),
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
        val coords = units.flatMap { u ->
            listOf(u.position) + HexDirection.entries.map { u.position.neighbor(it) }
        }
        return coords.distinct().associateWith { Hex(it) }
    }
}
