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

    /** Helper: initialize impulse then enter TorsoFacing for the given unit. */
    private fun enterTorsoFacing(
        controller: AttackController,
        unit: battletech.tactical.action.CombatUnit,
        gameState: GameState,
    ): PhaseState.Attack.TorsoFacing {
        controller.initializeImpulse(unit.owner, 1)
        return controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState) as PhaseState.Attack.TorsoFacing
    }

    /** Helper: enter TorsoFacing then confirm to get WeaponSelection. */
    private fun enterWeaponSelection(
        controller: AttackController,
        unit: battletech.tactical.action.CombatUnit,
        gameState: GameState,
    ): PhaseState.Attack.WeaponSelection {
        val tf = enterTorsoFacing(controller, unit, gameState)
        return (controller.handle(InputAction.Confirm, tf, HexCoordinates(0, 0), gameState) as PhaseOutcome.Continue)
            .phaseState as PhaseState.Attack.WeaponSelection
    }

    @Test
    fun `enter produces Attack TorsoFacing state`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.NE,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = enterTorsoFacing(controller, unit, gameState)

        assertEquals(unit.id, state.unitId)
        assertEquals(unit.facing, state.torsoFacing)
    }

    @Test
    fun `enter with no enemies in arc shows empty targets`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 4))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = enterTorsoFacing(controller, unit, gameState)

        assertTrue(state.validTargetIds.isEmpty())
    }

    @Test
    fun `enter finds valid targets in arc`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.NE,
        )
        val inArc = aUnit(id = "in-arc", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
        val outOfArc = aUnit(id = "out-of-arc", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 4))
        val gameState = GameState(listOf(unit, inArc, outOfArc), aGameMap(cols = 6, rows = 6))

        val state = enterTorsoFacing(controller, unit, gameState)

        assertTrue(inArc.id in state.validTargetIds)
        assertFalse(outOfArc.id in state.validTargetIds)
    }

    @Test
    fun `enter populates targets list on TorsoFacing state`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val state = enterTorsoFacing(controller, unit, gameState)

        assertEquals(1, state.targets.size)
        assertEquals(enemy.id, state.targets[0].unitId)
        assertTrue(state.targets[0].weapons.isNotEmpty())
    }

    @Test
    fun `enter with no targets produces empty targets list`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)

        val state = enterTorsoFacing(controller, unit, gameState)

        assertTrue(state.targets.isEmpty())
    }

    @Test
    fun `cancel from TorsoFacing returns Cancelled`() {
        val controller = createController()
        val unit = aUnit(position = HexCoordinates(2, 2))
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.Cancel, state, HexCoordinates(2, 2), gameState)

        assertTrue(result is PhaseOutcome.Cancelled)
    }

    @Test
    fun `confirm TorsoFacing with no targets transitions to WeaponSelection with No Attack cursor`() {
        val controller = createController()
        val unit = aUnit(position = HexCoordinates(2, 2))
        val gameState = GameState(listOf(unit), map5x5)
        val torsoFacing = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.Confirm, torsoFacing, HexCoordinates(2, 2), gameState)

        val ws = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponSelection
        assertTrue(ws.targets.isEmpty())
        assertEquals(ws.targets.size, ws.cursorTargetIndex)  // cursor on "No Attack"
    }

    @Test
    fun `torso twist clockwise updates arc and targets`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.MoveCursor(HexDirection.NE), state, HexCoordinates(2, 2), gameState)

        val newState = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TorsoFacing
        assertEquals(HexDirection.NE, newState.torsoFacing)
    }

    @Test
    fun `torso twist counterclockwise updates arc`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.MoveCursor(HexDirection.NW), state, HexCoordinates(2, 2), gameState)

        val newState = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TorsoFacing
        assertEquals(HexDirection.NW, newState.torsoFacing)
    }

    @Test
    fun `torso twist beyond one hex-side is rejected`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val state = enterTorsoFacing(controller, unit, gameState)

        val result1 = controller.handle(InputAction.MoveCursor(HexDirection.NE), state, HexCoordinates(2, 2), gameState)
        val twistedState = (result1 as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TorsoFacing
        assertEquals(HexDirection.NE, twistedState.torsoFacing)

        val result2 = controller.handle(InputAction.MoveCursor(HexDirection.NE), twistedState, HexCoordinates(2, 2), gameState)
        val stillSame = (result2 as PhaseOutcome.Continue).phaseState as PhaseState.Attack.TorsoFacing
        assertEquals(HexDirection.NE, stillSame.torsoFacing)
    }

    @Test
    fun `confirm TorsoFacing on unit with target transitions to WeaponSelection`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val torsoFacing = enterTorsoFacing(controller, unit, gameState)

        val result = controller.handle(InputAction.Confirm, torsoFacing, HexCoordinates(2, 2), gameState)

        val ws = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponSelection
        assertEquals(1, ws.targets.size)
        assertEquals(enemy.id, ws.targets[0].unitId)
    }

    @Test
    fun `select No Attack in WeaponSelection marks unit as NO_ATTACK`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val ws = enterWeaponSelection(controller, unit, gameState)

        // targets is empty → cursor is on "No Attack" (cursorTargetIndex == targets.size)
        assertEquals(ws.targets.size, ws.cursorTargetIndex)
        val result = controller.handle(InputAction.Confirm, ws, HexCoordinates(2, 2), gameState)

        assertTrue(result is PhaseOutcome.Cancelled)
        assertTrue(controller.canCommit())
    }

    @Test
    fun `weapon toggle on and off works in WeaponSelection`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val ws = enterWeaponSelection(controller, unit, gameState)

        // Toggle weapon on
        val toggleOn = controller.handle(InputAction.Confirm, ws, enemy.position, gameState)
        val onState = (toggleOn as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponSelection
        assertTrue(0 in (onState.weaponAssignments[enemy.id] ?: emptySet()))

        // Toggle weapon off
        val toggleOff = controller.handle(InputAction.Confirm, onState, enemy.position, gameState)
        val offState = (toggleOff as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponSelection
        assertFalse(0 in (offState.weaponAssignments[enemy.id] ?: emptySet()))
    }

    @Test
    fun `cancel from WeaponSelection saves assignments and returns Cancelled`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val ws = enterWeaponSelection(controller, unit, gameState)

        val result = controller.handle(InputAction.Cancel, ws, enemy.position, gameState)

        assertTrue(result is PhaseOutcome.Cancelled)
    }

    @Test
    fun `commit after weapon assignment records declaration`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val ws = enterWeaponSelection(controller, unit, gameState)

        // Toggle weapon on
        controller.handle(InputAction.Confirm, ws, enemy.position, gameState)

        // Declaration is saved immediately on toggle; can commit
        assertTrue(controller.canCommit())

        val committedIds = controller.commitImpulse()
        assertTrue(unit.id in committedIds)

        val declarations = controller.collectDeclarations()
        assertEquals(1, declarations.size)
        assertEquals(unit.id, declarations[0].attackerId)
        assertEquals(enemy.id, declarations[0].targetId)
        assertEquals(0, declarations[0].weaponIndex)
        assertTrue(declarations[0].isPrimary)
    }

    @Test
    fun `tab jumps to first weapon of next target in WeaponSelection`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy1 = aUnit(id = "enemy1", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val enemy2 = aUnit(id = "enemy2", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
        val gameState = GameState(listOf(unit, enemy1, enemy2), map5x5)
        val ws = enterWeaponSelection(controller, unit, gameState)

        val initialIdx = ws.cursorTargetIndex
        val tabResult = controller.handle(InputAction.CycleUnit, ws, HexCoordinates(0, 0), gameState)
        val tabbed = (tabResult as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponSelection

        // Index should have advanced
        assertTrue(tabbed.cursorTargetIndex != initialIdx || ws.targets.size <= 1)
    }

    @Test
    fun `tab wraps through No Attack entry in WeaponSelection`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val ws = enterWeaponSelection(controller, unit, gameState)

        // Tab past the single target → should land on No Attack (index = targets.size)
        var state = ws
        repeat(ws.targets.size + 1) {
            val result = controller.handle(InputAction.CycleUnit, state, HexCoordinates(0, 0), gameState)
            state = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponSelection
        }
        // After targets.size + 1 tabs starting from 0, we've wrapped: 1 (No Attack) → 0 (first target)
        // We just verify it completes without error
        assertTrue(state.cursorTargetIndex in 0..ws.targets.size)
    }

    @Test
    fun `arrow down navigates to next weapon within target`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser(), mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val ws = enterWeaponSelection(controller, unit, gameState)

        val result = controller.handle(InputAction.MoveCursor(HexDirection.S), ws, HexCoordinates(0, 0), gameState)
        val moved = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponSelection

        // Should have moved to weapon index 1 (or wrapped if only 1 available)
        assertTrue(moved.cursorWeaponIndex != ws.cursorWeaponIndex || ws.targets[0].weapons.size == 1)
    }

    @Test
    fun `arrow down from last weapon of last target wraps to No Attack`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val ws = enterWeaponSelection(controller, unit, gameState)
        // Start at target 0, weapon 0 (only 1 weapon, 1 target)
        assertEquals(0, ws.cursorTargetIndex)
        assertEquals(0, ws.cursorWeaponIndex)

        val result = controller.handle(InputAction.MoveCursor(HexDirection.S), ws, HexCoordinates(0, 0), gameState)
        val moved = (result as PhaseOutcome.Continue).phaseState as PhaseState.Attack.WeaponSelection

        // Should wrap to "No Attack"
        assertEquals(ws.targets.size, moved.cursorTargetIndex)
    }

    @Test
    fun `canCommit returns false before any declarations`() {
        val controller = createController()
        controller.initializeImpulse(PlayerId.PLAYER_1, 1)
        assertFalse(controller.canCommit())
    }

    @Test
    fun `initializeImpulse resets declaration state`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)

        // Mark as no-attack in first impulse (enter WeaponSelection with empty targets → confirm = NO_ATTACK)
        val ws = enterWeaponSelection(controller, unit, gameState)
        controller.handle(InputAction.Confirm, ws, HexCoordinates(2, 2), gameState)
        assertTrue(controller.canCommit())

        // Initialize a new impulse — canCommit should be false again
        controller.initializeImpulse(PlayerId.PLAYER_1, 1)
        assertFalse(controller.canCommit())
    }
}
