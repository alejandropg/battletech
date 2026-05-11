package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.MovementImpulse
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Weapon
import battletech.tui.aGameMap
import battletech.tui.aUnit
import com.github.ajalt.mordant.input.KeyboardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AttackPhaseStatePhaseTest {

    private val actionQueryService = ActionQueryService(
        MoveActionDefinition(),
        listOf(FireWeaponActionDefinition()),
    )

    private fun newManager(): PhaseManager = PhaseManager(
        movementController = MovementController(actionQueryService),
        attackController = AttackController(),
    )

    private fun mediumLaser(): Weapon = Weapon(
        name = "Medium Laser", damage = 5, heat = 3,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    private val map7x7 = aGameMap(cols = 7, rows = 7)

    private fun attackTurnState(attackedUnitIds: Set<UnitId> = emptySet()): TurnState = TurnState(
        initiativeResult = InitiativeResult(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        ),
        movementOrder = listOf(MovementImpulse(PlayerId.PLAYER_1, 1)),
        attackOrder = listOf(MovementImpulse(PlayerId.PLAYER_1, 3)),
        attackedUnitIds = attackedUnitIds,
    )

    private fun tabKey(): KeyboardEvent = KeyboardEvent("Tab")

    private fun enterAttackOn(
        manager: PhaseManager,
        unit: battletech.tactical.action.CombatUnit,
        gameState: GameState,
    ): AttackPhaseState {
        manager.attackController.initializeImpulse(PlayerId.PLAYER_1)
        return manager.attackController.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)
    }

    @Test
    fun `Tab with multiple selectable attackers advances to next attacker and moves cursor`() {
        val manager = newManager()
        val unitA = aUnit(
            id = "a", weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 3), facing = HexDirection.N,
        )
        val unitB = aUnit(
            id = "b", weapons = listOf(mediumLaser()),
            position = HexCoordinates(4, 3), facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(3, 1),
        )
        val gameState = GameState(listOf(unitA, unitB, enemy), map7x7)
        val phaseA = enterAttackOn(manager, unitA, gameState)
        val appState = AppState(
            gameState = gameState,
            currentPhase = TurnPhase.WEAPON_ATTACK,
            cursor = unitA.position,
            phase = phaseA,
            turnState = attackTurnState(),
        )

        val result = phaseA.processEvent(tabKey(), appState, manager)

        assertNotNull(result)
        val newPhase = result!!.appState.phase
        assertInstanceOf(AttackPhaseState::class.java, newPhase)
        assertEquals(unitB.id, (newPhase as AttackPhaseState).unitId)
        assertEquals(unitB.position, result.appState.cursor)
    }

    @Test
    fun `Tab preserves previous attacker's weapon assignments when cycling back`() {
        val manager = newManager()
        val unitA = aUnit(
            id = "a", weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 3), facing = HexDirection.N,
        )
        val unitB = aUnit(
            id = "b", weapons = listOf(mediumLaser()),
            position = HexCoordinates(4, 3), facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(3, 1),
        )
        val gameState = GameState(listOf(unitA, unitB, enemy), map7x7)
        val phaseA = enterAttackOn(manager, unitA, gameState)
        val appStateA = AppState(
            gameState = gameState,
            currentPhase = TurnPhase.WEAPON_ATTACK,
            cursor = unitA.position,
            phase = phaseA,
            turnState = attackTurnState(),
        )

        // Toggle a weapon on A (Space), so the declaration is persisted in currentImpulse.
        val toggleResult = phaseA.processEvent(KeyboardEvent(" "), appStateA, manager)
        assertNotNull(toggleResult)
        val phaseAAfterToggle = toggleResult!!.appState.phase as AttackPhaseState
        assertTrue(phaseAAfterToggle.weaponAssignments[enemy.id]?.contains(0) == true)

        // Tab to B
        val appStateAfterToggle = toggleResult.appState
        val tabToB = phaseAAfterToggle.processEvent(tabKey(), appStateAfterToggle, manager)
        assertNotNull(tabToB)
        val phaseB = tabToB!!.appState.phase as AttackPhaseState
        assertEquals(unitB.id, phaseB.unitId)

        // Tab back to A
        val tabBackToA = phaseB.processEvent(tabKey(), tabToB.appState, manager)
        assertNotNull(tabBackToA)
        val phaseAAgain = tabBackToA!!.appState.phase as AttackPhaseState
        assertEquals(unitA.id, phaseAAgain.unitId)

        // A's weapon assignment should be restored from currentImpulse.
        assertTrue(phaseAAgain.weaponAssignments[enemy.id]?.contains(0) == true)
    }

    @Test
    fun `Tab wraps to self when only one selectable attacker remains`() {
        val manager = newManager()
        val unitA = aUnit(
            id = "a", weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 3), facing = HexDirection.N,
        )
        val unitB = aUnit(
            id = "b", weapons = listOf(mediumLaser()),
            position = HexCoordinates(4, 3), facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(3, 1),
        )
        val gameState = GameState(listOf(unitA, unitB, enemy), map7x7)
        val phaseA = enterAttackOn(manager, unitA, gameState)
        // Pretend B already attacked, so A is the only selectable attacker.
        val appState = AppState(
            gameState = gameState,
            currentPhase = TurnPhase.WEAPON_ATTACK,
            cursor = unitA.position,
            phase = phaseA,
            turnState = attackTurnState(attackedUnitIds = setOf(unitB.id)),
        )

        val result = phaseA.processEvent(tabKey(), appState, manager)

        assertNotNull(result)
        val newPhase = result!!.appState.phase as AttackPhaseState
        assertEquals(unitA.id, newPhase.unitId)
        assertEquals(unitA.position, result.appState.cursor)
    }

    @Test
    fun `Tab cycles through selectable attackers in order`() {
        val manager = newManager()
        val unitA = aUnit(
            id = "a", weapons = listOf(mediumLaser()),
            position = HexCoordinates(1, 3), facing = HexDirection.N,
        )
        val unitB = aUnit(
            id = "b", weapons = listOf(mediumLaser()),
            position = HexCoordinates(3, 3), facing = HexDirection.N,
        )
        val unitC = aUnit(
            id = "c", weapons = listOf(mediumLaser()),
            position = HexCoordinates(5, 3), facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(3, 1),
        )
        val gameState = GameState(listOf(unitA, unitB, unitC, enemy), map7x7)
        val phaseA = enterAttackOn(manager, unitA, gameState)
        val appStateA = AppState(
            gameState = gameState,
            currentPhase = TurnPhase.WEAPON_ATTACK,
            cursor = unitA.position,
            phase = phaseA,
            turnState = attackTurnState(),
        )

        val toB = phaseA.processEvent(tabKey(), appStateA, manager)!!
        assertEquals(unitB.id, (toB.appState.phase as AttackPhaseState).unitId)

        val toC = toB.appState.phase.processEvent(tabKey(), toB.appState, manager)!!
        assertEquals(unitC.id, (toC.appState.phase as AttackPhaseState).unitId)

        val backToA = toC.appState.phase.processEvent(tabKey(), toC.appState, manager)!!
        assertEquals(unitA.id, (backToA.appState.phase as AttackPhaseState).unitId)
    }

    @Test
    fun `Tab does not commit the impulse`() {
        val manager = newManager()
        val unitA = aUnit(
            id = "a", weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 3), facing = HexDirection.N,
        )
        val unitB = aUnit(
            id = "b", weapons = listOf(mediumLaser()),
            position = HexCoordinates(4, 3), facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(3, 1),
        )
        val gameState = GameState(listOf(unitA, unitB, enemy), map7x7)
        val phaseA = enterAttackOn(manager, unitA, gameState)
        val appState = AppState(
            gameState = gameState,
            currentPhase = TurnPhase.WEAPON_ATTACK,
            cursor = unitA.position,
            phase = phaseA,
            turnState = attackTurnState(),
        )

        val result = phaseA.processEvent(tabKey(), appState, manager)
        assertNotNull(result)

        // attackedUnitIds is only mutated by `c` (CommitDeclarations); Tab must not touch it.
        assertEquals(emptySet<UnitId>(), result!!.appState.turnState!!.attackedUnitIds)
        // No declarations have been committed to the persistent list either.
        assertTrue(manager.attackController.collectDeclarations().isEmpty())
    }
}
