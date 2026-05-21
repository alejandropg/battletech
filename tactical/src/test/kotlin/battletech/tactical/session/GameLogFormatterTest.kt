package battletech.tactical.session

import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.movement.MovementMode
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.InternalStructureLayout
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapons
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GameLogFormatterTest {

    private val emptyState = GameState(
        units = emptyList(),
        map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)))),
    )
    private val emptyTurn = TurnState.NULL

    @Test
    fun `PhaseChanged formats as 'Phase X'`() {
        val text = GameLogFormatter.format(
            event = PhaseChanged(TurnPhase.INITIATIVE, TurnPhase.MOVEMENT),
            state = emptyState,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Phase: Movement")
    }

    @Test
    fun `UnitMoved shows the unit name, mode-specific verb, hex coords, and MP spent`() {
        val atlas = aMech(id = "atlas", name = "Atlas", position = HexCoordinates(2, 0))
        val stateWithAtlas = emptyState.copy(units = listOf(atlas))

        val text = GameLogFormatter.format(
            event = UnitMoved(
                unitId = atlas.id,
                from = HexCoordinates(2, 0),
                to = HexCoordinates(2, 1),
                finalFacing = HexDirection.N,
                mode = MovementMode.WALK,
                mpSpent = 3,
            ),
            state = stateWithAtlas,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Atlas walked 0301→0302 (3 MP)")
    }

    @Test
    fun `UnitMoved uses 'ran' for RUN and 'jumped' for JUMP`() {
        val atlas = aMech(id = "atlas", name = "Atlas", position = HexCoordinates(0, 0))
        val stateWithAtlas = emptyState.copy(units = listOf(atlas))

        val ran = GameLogFormatter.format(
            event = UnitMoved(atlas.id, HexCoordinates(0, 0), HexCoordinates(0, 1),
                HexDirection.N, MovementMode.RUN, 5),
            stateWithAtlas, emptyTurn,
        )
        val jumped = GameLogFormatter.format(
            event = UnitMoved(atlas.id, HexCoordinates(0, 0), HexCoordinates(0, 1),
                HexDirection.N, MovementMode.JUMP, 4),
            stateWithAtlas, emptyTurn,
        )

        assertThat(ran).isEqualTo("Atlas ran 0101→0102 (5 MP)")
        assertThat(jumped).isEqualTo("Atlas jumped 0101→0102 (4 MP)")
    }

    @Test
    fun `InitiativeRolled shows both rolls and the player who moves first`() {
        val initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to 6, PlayerId.PLAYER_2 to 3),
            loser = PlayerId.PLAYER_2,
            winner = PlayerId.PLAYER_1,
        )

        val text = GameLogFormatter.format(
            event = InitiativeRolled(initiative),
            state = emptyState,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Initiative: P1 rolled 6, P2 rolled 3 — P2 moves first")
    }

    @Test
    fun `AttacksResolved summarizes fired, hit, and damage`() {
        val results = listOf(
            anAttackResult(hit = true, damage = 5),
            anAttackResult(hit = true, damage = 7),
            anAttackResult(hit = false, damage = 0),
        )

        val text = GameLogFormatter.format(
            event = AttacksResolved(results),
            state = emptyState,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Attacks: 3 fired, 2 hit, 12 damage")
    }

    @Test
    fun `AttacksResolved with no attacks reads gracefully`() {
        val text = GameLogFormatter.format(
            event = AttacksResolved(emptyList()),
            state = emptyState,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Attacks: 0 fired, 0 hit, 0 damage")
    }

    @Test
    fun `AttackDeclarationsRecorded shows player and count`() {
        val text = GameLogFormatter.format(
            event = AttackDeclarationsRecorded(PlayerId.PLAYER_1, count = 2),
            state = emptyState,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("P1 declared 2 attacks")
    }

    @Test
    fun `AttackDeclarationsRecorded uses singular 'attack' for count of 1`() {
        val text = GameLogFormatter.format(
            event = AttackDeclarationsRecorded(PlayerId.PLAYER_2, count = 1),
            state = emptyState,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("P2 declared 1 attack")
    }

    @Test
    fun `TorsoFacingsApplied lists each unit and its torso facing`() {
        val atlas = aMech(id = "atlas", name = "Atlas")
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithUnits = emptyState.copy(units = listOf(atlas, locust))

        val text = GameLogFormatter.format(
            event = TorsoFacingsApplied(
                facings = mapOf(
                    atlas.id to HexDirection.NE,
                    locust.id to HexDirection.S,
                ),
            ),
            state = stateWithUnits,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Torso facings: Atlas→NE, Locust→S")
    }

    @Test
    fun `TorsoFacingsApplied with no facings says 'no changes'`() {
        val text = GameLogFormatter.format(
            event = TorsoFacingsApplied(facings = emptyMap()),
            state = emptyState,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Torso facings: no changes")
    }

    @Test
    fun `HeatDissipated shows before-arrow-after per unit that had heat`() {
        val atlas = aMech(id = "atlas", name = "Atlas")
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithUnits = emptyState.copy(units = listOf(atlas, locust))

        val text = GameLogFormatter.format(
            event = HeatDissipated(
                heatBefore = mapOf(atlas.id to 8, locust.id to 0),
                heatAfter = mapOf(atlas.id to 4, locust.id to 0),
            ),
            state = stateWithUnits,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Heat: Atlas 8→4")
    }

    @Test
    fun `HeatDissipated with no heat reports 'no heat'`() {
        val atlas = aMech(id = "atlas", name = "Atlas")
        val stateWithAtlas = emptyState.copy(units = listOf(atlas))

        val text = GameLogFormatter.format(
            event = HeatDissipated(
                heatBefore = mapOf(atlas.id to 0),
                heatAfter = mapOf(atlas.id to 0),
            ),
            state = stateWithAtlas,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Heat: no heat to dissipate")
    }

    @Test
    fun `TurnEnded shows the turn number that just ended`() {
        val text = GameLogFormatter.format(
            event = TurnEnded(turnNumber = 3),
            state = emptyState,
            turn = emptyTurn,
        )

        assertThat(text).isEqualTo("Turn 3 complete")
    }

    private fun anAttackResult(hit: Boolean, damage: Int): battletech.tactical.attack.AttackResult =
        battletech.tactical.attack.AttackResult(
            attackerId = UnitId("a"),
            targetId = UnitId("t"),
            weaponName = "ML",
            hit = hit,
            hitLocation = null,
            damageApplied = damage,
            targetNumber = 8,
            roll = if (hit) 10 else 5,
        )

    private fun aMech(
        id: String,
        name: String = id,
        owner: PlayerId = PlayerId.PLAYER_1,
        position: HexCoordinates = HexCoordinates(0, 0),
        currentHeat: Int = 0,
    ): CombatUnit = CombatUnit(
        id = UnitId(id),
        owner = owner,
        name = name,
        gunnerySkill = 4,
        pilotingSkill = 5,
        weapons = listOf(Weapons.mediumLaser()),
        position = position,
        facing = HexDirection.N,
        torsoFacing = HexDirection.N,
        walkingMP = 4,
        runningMP = 6,
        jumpMP = 4,
        currentHeat = currentHeat,
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
}
