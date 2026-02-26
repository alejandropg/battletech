package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Weapon
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.input.InputAction
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AttackControllerTest {

    private fun createController(): AttackController {
        val actionQueryService = mockk<ActionQueryService>()
        return AttackController(actionQueryService)
    }

    private fun mediumLaser(): Weapon = Weapon(
        name = "Medium Laser", damage = 5, heat = 3,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    private val map5x5 = aGameMap(cols = 5, rows = 5)

    @Test
    fun `enter produces Attack Browsing state`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.NE,
        )
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(3, 1),
        )
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)

        assertTrue(state is PhaseState.Attack.Browsing)
        assertEquals(unit.id, state.unitId)
        assertEquals(unit.facing, state.torsoFacing)
    }

    @Test
    fun `enter with no enemies in arc shows skip prompt`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 4), // behind when facing N
        )
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState) as PhaseState.Attack.Browsing

        assertTrue(state.validTargetIds.isEmpty())
        assertTrue(state.prompt.contains("No attacks"))
    }

    @Test
    fun `enter finds valid targets in arc`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.NE,
        )
        val inArc = aUnit(
            id = "in-arc",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(3, 1),
        )
        val outOfArc = aUnit(
            id = "out-of-arc",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(0, 4),
        )
        val gameState = GameState(listOf(unit, inArc, outOfArc), aGameMap(cols = 6, rows = 6))

        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState) as PhaseState.Attack.Browsing

        assertTrue(inArc.id in state.validTargetIds)
        assertFalse(outOfArc.id in state.validTargetIds)
    }

    @Test
    fun `enter populates targets list on Browsing state`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1),
        )
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState) as PhaseState.Attack.Browsing

        assertEquals(1, state.targets.size)
        assertEquals(enemy.id, state.targets[0].unitId)
        assertTrue(state.targets[0].eligibleWeapons.isNotEmpty())
    }

    @Test
    fun `enter with no targets produces empty targets list`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val gameState = GameState(listOf(unit), map5x5)

        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState) as PhaseState.Attack.Browsing

        assertTrue(state.targets.isEmpty())
    }

    @Test
    fun `cancel from Browsing returns Cancelled`() {
        val controller = createController()
        val unit = aUnit(position = HexCoordinates(2, 2))
        val gameState = GameState(listOf(unit), map5x5)
        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)

        val result = controller.handle(InputAction.Cancel, state, HexCoordinates(2, 2), gameState)

        assertTrue(result is PhaseOutcome.Cancelled)
    }

    @Test
    fun `confirm with no valid targets completes phase`() {
        val controller = createController()
        val unit = aUnit(position = HexCoordinates(2, 2))
        val gameState = GameState(listOf(unit), map5x5)
        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)

        val result = controller.handle(InputAction.Confirm, state, HexCoordinates(2, 2), gameState)

        assertTrue(result is PhaseOutcome.Complete)
    }

    @Test
    fun `torso twist clockwise updates arc and targets`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val gameState = GameState(listOf(unit), map5x5)
        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState) as PhaseState.Attack.Browsing

        // Twist clockwise (NE direction = right arrow)
        val result = controller.handle(
            InputAction.MoveCursor(HexDirection.NE),
            state,
            HexCoordinates(2, 2),
            gameState,
        )

        val newState = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.Browsing
        assertEquals(HexDirection.NE, newState.torsoFacing)
    }

    @Test
    fun `torso twist counterclockwise updates arc`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val gameState = GameState(listOf(unit), map5x5)
        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState) as PhaseState.Attack.Browsing

        val result = controller.handle(
            InputAction.MoveCursor(HexDirection.NW),
            state,
            HexCoordinates(2, 2),
            gameState,
        )

        val newState = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.Browsing
        assertEquals(HexDirection.NW, newState.torsoFacing)
    }

    @Test
    fun `torso twist beyond one hex-side is rejected`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val gameState = GameState(listOf(unit), map5x5)
        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState) as PhaseState.Attack.Browsing

        // Twist to NE first
        val result1 = controller.handle(
            InputAction.MoveCursor(HexDirection.NE),
            state,
            HexCoordinates(2, 2),
            gameState,
        )
        val twistedState = (result1 as PhaseOutcome.Continue).phaseState as PhaseState.Attack.Browsing
        assertEquals(HexDirection.NE, twistedState.torsoFacing)

        // Try to twist further CW (to SE) — should be rejected (2 turns from N)
        val result2 = controller.handle(
            InputAction.MoveCursor(HexDirection.NE),
            twistedState,
            HexCoordinates(2, 2),
            gameState,
        )
        val stillSame = (result2 as PhaseOutcome.Continue).phaseState as PhaseState.Attack.Browsing
        assertEquals(HexDirection.NE, stillSame.torsoFacing)
    }

    @Test
    fun `confirm on valid target transitions to AssigningWeapons`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1), // directly north
        )
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)

        val result = controller.handle(InputAction.Confirm, state, enemy.position, gameState)

        val newState = (result as PhaseOutcome.Continue).phaseState
        assertTrue(newState is PhaseState.Attack.AssigningWeapons)
        val assigning = newState as PhaseState.Attack.AssigningWeapons
        assertEquals(enemy.id, assigning.primaryTargetId)
        assertEquals(1, assigning.targets.size)
    }

    @Test
    fun `weapon toggle on and off works`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1),
        )
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val browsing = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)

        // Confirm on enemy to enter AssigningWeapons
        val enterResult = controller.handle(InputAction.Confirm, browsing, enemy.position, gameState)
        val assigning = (enterResult as PhaseOutcome.Continue).phaseState as PhaseState.Attack.AssigningWeapons

        // Toggle weapon 1 on
        val toggleOn = controller.handle(InputAction.SelectAction(1), assigning, enemy.position, gameState)
        val onState = (toggleOn as PhaseOutcome.Continue).phaseState as PhaseState.Attack.AssigningWeapons
        assertTrue(0 in (onState.weaponAssignments[enemy.id] ?: emptySet()))

        // Toggle weapon 1 off
        val toggleOff = controller.handle(InputAction.SelectAction(1), onState, enemy.position, gameState)
        val offState = (toggleOff as PhaseOutcome.Continue).phaseState as PhaseState.Attack.AssigningWeapons
        assertFalse(0 in (offState.weaponAssignments[enemy.id] ?: emptySet()))
    }

    @Test
    fun `cancel from AssigningWeapons returns to Browsing`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1),
        )
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val browsing = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)
        val enterResult = controller.handle(InputAction.Confirm, browsing, enemy.position, gameState)
        val assigning = (enterResult as PhaseOutcome.Continue).phaseState as PhaseState.Attack.AssigningWeapons

        val result = controller.handle(InputAction.Cancel, assigning, enemy.position, gameState)

        assertTrue((result as PhaseOutcome.Continue).phaseState is PhaseState.Attack.Browsing)
    }

    @Test
    fun `confirm with weapon assigned records declaration and completes`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1),
        )
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val browsing = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)
        val enterResult = controller.handle(InputAction.Confirm, browsing, enemy.position, gameState)
        val assigning = (enterResult as PhaseOutcome.Continue).phaseState as PhaseState.Attack.AssigningWeapons

        // Toggle weapon on
        val toggleResult = controller.handle(InputAction.SelectAction(1), assigning, enemy.position, gameState)
        val withWeapon = (toggleResult as PhaseOutcome.Continue).phaseState as PhaseState.Attack.AssigningWeapons

        // Confirm
        val result = controller.handle(InputAction.Confirm, withWeapon, enemy.position, gameState)

        assertTrue(result is PhaseOutcome.Complete)
        val declarations = controller.collectDeclarations()
        assertEquals(1, declarations.size)
        assertEquals(unit.id, declarations[0].attackerId)
        assertEquals(enemy.id, declarations[0].targetId)
        assertEquals(0, declarations[0].weaponIndex)
        assertTrue(declarations[0].isPrimary)
    }

    @Test
    fun `tab cycles between targets`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy1 = aUnit(
            id = "enemy1",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1),
        )
        val enemy2 = aUnit(
            id = "enemy2",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 0),
        )
        val gameState = GameState(listOf(unit, enemy1, enemy2), map5x5)
        val browsing = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)
        val enterResult = controller.handle(InputAction.Confirm, browsing, enemy1.position, gameState)
        val assigning = (enterResult as PhaseOutcome.Continue).phaseState as PhaseState.Attack.AssigningWeapons

        val initialIdx = assigning.selectedTargetIndex
        val tabResult = controller.handle(InputAction.CycleUnit, assigning, enemy1.position, gameState)
        val tabbed = (tabResult as PhaseOutcome.Continue).phaseState as PhaseState.Attack.AssigningWeapons

        // Should have moved to the other target
        assertTrue(tabbed.selectedTargetIndex != initialIdx || assigning.targets.size == 1)
    }
}
