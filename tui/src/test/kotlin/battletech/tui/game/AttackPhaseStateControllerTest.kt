package battletech.tui.game

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.InitiativeResult
import battletech.tactical.action.MovementImpulse
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.Weapon
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.input.AttackAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AttackPhaseStateControllerTest {

    private fun createController(): AttackController = AttackController()

    private fun mediumLaser(): Weapon = Weapon(
        name = "Medium Laser", damage = 5, heat = 3,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    private val map5x5 = aGameMap(cols = 5, rows = 5)

    private fun baseTurnState(): TurnState = TurnState(
        initiativeResult = InitiativeResult(
            rolls = mapOf(PlayerId.PLAYER_1 to 5, PlayerId.PLAYER_2 to 8),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        ),
        movementSequence = ImpulseSequence(listOf(MovementImpulse(PlayerId.PLAYER_1, 1))),
        attackSequence = ImpulseSequence(listOf(MovementImpulse(PlayerId.PLAYER_1, 1))),
    )

    /** Initialize a turn state's impulse for the given player. */
    private fun startImpulse(controller: AttackController, playerId: PlayerId = PlayerId.PLAYER_1): TurnState =
        controller.initializeImpulse(baseTurnState(), playerId)

    /** Helper: initialize impulse then enter Declaring for the given unit. */
    private fun enterDeclaring(
        controller: AttackController,
        unit: CombatUnit,
        gameState: GameState,
        turnState: TurnState = startImpulse(controller, unit.owner),
    ): Pair<AttackPhaseState, TurnState> {
        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState, turnState)
        return state to turnState
    }

    @Test
    fun `enter produces Attack Declaring state`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.NE,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val (state, _) = enterDeclaring(controller, unit, gameState)

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

        val (state, _) = enterDeclaring(controller, unit, gameState)

        assertTrue(validTargets(unit, state.torsoFacing, gameState).isEmpty())
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

        val (state, _) = enterDeclaring(controller, unit, gameState)
        val ids = validTargets(unit, state.torsoFacing, gameState)

        assertTrue(inArc.id in ids)
        assertFalse(outOfArc.id in ids)
    }

    @Test
    fun `enter populates targets list with weapons`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val (state, _) = enterDeclaring(controller, unit, gameState)
        val targets = targetInfos(unit, state.torsoFacing, gameState)

        assertEquals(1, targets.size)
        assertEquals(enemy.id, targets[0].unitId)
        assertTrue(targets[0].weapons.isNotEmpty())
    }

    @Test
    fun `enter with no targets produces empty targets list`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)

        val (state, _) = enterDeclaring(controller, unit, gameState)

        assertTrue(targetInfos(unit, state.torsoFacing, gameState).isEmpty())
    }

    @Test
    fun `cancel saves assignments and returns Cancelled`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        // Toggle a weapon on, then cancel
        val (toggled, ts1) = controller.handle(AttackAction.ToggleWeapon, state, HexCoordinates(2, 2), gameState, ts0)
        val toggledState = (toggled as PhaseOutcome.Continue).phaseState as AttackPhaseState
        val (result, ts2) = controller.handle(AttackAction.Cancel, toggledState, HexCoordinates(2, 2), gameState, ts1)

        assertTrue(result is PhaseOutcome.Cancelled)
        // After cancel, the toggled weapon should still be recorded for commit
        val (ts3, _) = controller.commitImpulse(ts2)
        assertEquals(1, controller.collectDeclarations(ts3).size)
    }

    @Test
    fun `weapon assignments preserved when cancelling and re-entering the same unit`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        // Toggle weapon on, then cancel
        val (toggled, ts1) = controller.handle(AttackAction.ToggleWeapon, state, HexCoordinates(2, 2), gameState, ts0)
        val toggledState = (toggled as PhaseOutcome.Continue).phaseState as AttackPhaseState
        val (_, ts2) = controller.handle(AttackAction.Cancel, toggledState, HexCoordinates(2, 2), gameState, ts1)

        // Re-enter the same unit — assignments should be restored
        val reEntered = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState, ts2)
        assertTrue(reEntered.weaponAssignments.values.any { it.isNotEmpty() },
            "Expected weapon assignments to survive cancel+re-enter, but got: ${reEntered.weaponAssignments}")
    }

    @Test
    fun `weapon assignments preserved when switching to another unit and back`() {
        val controller = createController()
        val unitA = aUnit(id = "A", weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val unitB = aUnit(id = "B", weapons = listOf(mediumLaser()), position = HexCoordinates(3, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unitA, unitB, enemy), map5x5)

        val ts0 = startImpulse(controller, PlayerId.PLAYER_1)

        // Enter A, toggle weapon, exit via Cancel (Esc)
        val stateA = controller.enter(unitA, TurnPhase.WEAPON_ATTACK, gameState, ts0)
        val (toggledA, ts1) = controller.handle(AttackAction.ToggleWeapon, stateA, enemy.position, gameState, ts0)
        val toggledAState = (toggledA as PhaseOutcome.Continue).phaseState as AttackPhaseState
        val (_, ts2) = controller.handle(AttackAction.Cancel, toggledAState, enemy.position, gameState, ts1)

        // Enter B, exit via Cancel
        val stateB = controller.enter(unitB, TurnPhase.WEAPON_ATTACK, gameState, ts2)
        val (_, ts3) = controller.handle(AttackAction.Cancel, stateB, enemy.position, gameState, ts2)

        // Re-enter A — weapon assignments must survive the trip through B
        val reEnteredA = controller.enter(unitA, TurnPhase.WEAPON_ATTACK, gameState, ts3)
        assertTrue(reEnteredA.weaponAssignments.values.any { it.isNotEmpty() },
            "Expected A's assignments to survive switching to B and back, but got: ${reEnteredA.weaponAssignments}")
    }

    @Test
    fun `torso twist persists after leaving and re-entering`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        // Twist torso, then exit without toggling any weapon
        val (twisted, ts1) = controller.handle(AttackAction.TwistTorso(clockwise = true), state, unit.position, gameState, ts0)
        val twistedState = (twisted as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, twistedState.torsoFacing)
        val (_, ts2) = controller.handle(AttackAction.Cancel, twistedState, unit.position, gameState, ts1)

        // Re-enter — torso facing should still be NE
        val reEntered = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState, ts2)
        assertEquals(HexDirection.NE, reEntered.torsoFacing)
    }

    @Test
    fun `confirm with no weapons assigned records nothing`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        val (result, ts1) = controller.handle(AttackAction.Confirm, state, HexCoordinates(2, 2), gameState, ts0)

        assertTrue(result is PhaseOutcome.Cancelled)
        // Confirm without toggling weapons leaves the declarations map empty
        val (ts2, _) = controller.commitImpulse(ts1)
        assertTrue(controller.collectDeclarations(ts2).isEmpty())
    }

    @Test
    fun `torso twist clockwise updates arc and targets`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        val (result, _) = controller.handle(AttackAction.TwistTorso(clockwise = true), state, HexCoordinates(2, 2), gameState, ts0)

        val newState = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, newState.torsoFacing)
    }

    @Test
    fun `torso twist counterclockwise updates arc`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        val (result, _) = controller.handle(AttackAction.TwistTorso(clockwise = false), state, HexCoordinates(2, 2), gameState, ts0)

        val newState = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NW, newState.torsoFacing)
    }

    @Test
    fun `torso twist beyond one hex-side is rejected`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val gameState = GameState(listOf(unit), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        val (result1, ts1) = controller.handle(AttackAction.TwistTorso(clockwise = true), state, HexCoordinates(2, 2), gameState, ts0)
        val twistedState = (result1 as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, twistedState.torsoFacing)

        val (result2, _) = controller.handle(AttackAction.TwistTorso(clockwise = true), twistedState, HexCoordinates(2, 2), gameState, ts1)
        val stillSame = (result2 as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, stillSame.torsoFacing)
    }

    @Test
    fun `weapon toggle on and off works with ToggleWeapon`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        // Toggle weapon on
        val (toggleOn, ts1) = controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState, ts0)
        val onState = (toggleOn as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertTrue(0 in (onState.weaponAssignments[enemy.id] ?: emptySet()))

        // Toggle weapon off
        val (toggleOff, _) = controller.handle(AttackAction.ToggleWeapon, onState, enemy.position, gameState, ts1)
        val offState = (toggleOff as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertFalse(0 in (offState.weaponAssignments[enemy.id] ?: emptySet()))
    }

    @Test
    fun `confirm after weapon assignment saves declaration`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        // Toggle weapon on
        val (toggleOn, ts1) = controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState, ts0)
        val onState = (toggleOn as PhaseOutcome.Continue).phaseState as AttackPhaseState

        // Confirm
        val (result, ts2) = controller.handle(AttackAction.Confirm, onState, enemy.position, gameState, ts1)
        assertTrue(result is PhaseOutcome.Cancelled)

        val (ts3, _) = controller.commitImpulse(ts2)

        val declarations = controller.collectDeclarations(ts3)
        assertEquals(1, declarations.size)
        assertEquals(unit.id, declarations[0].attackerId)
        assertEquals(enemy.id, declarations[0].targetId)
        assertEquals(0, declarations[0].weaponIndex)
        assertTrue(declarations[0].isPrimary)
    }

    @Test
    fun `NavigateWeapons down moves to next weapon within target`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser(), mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        val (result, _) = controller.handle(AttackAction.NavigateWeapons(delta = 1), state, HexCoordinates(0, 0), gameState, ts0)
        val moved = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState
        val targets = targetInfos(unit, state.torsoFacing, gameState)

        // Should have moved to weapon index 1 (or wrapped if only 1 available)
        assertTrue(moved.cursorWeaponIndex != state.cursorWeaponIndex || targets[0].weapons.size == 1)
    }

    @Test
    fun `NavigateWeapons from last weapon wraps to first weapon`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)
        assertEquals(0, state.cursorTargetIndex)
        assertEquals(0, state.cursorWeaponIndex)

        val (result, _) = controller.handle(AttackAction.NavigateWeapons(delta = 1), state, HexCoordinates(0, 0), gameState, ts0)
        val moved = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState

        // Only 1 weapon total, wraps back to same position
        assertEquals(0, moved.cursorTargetIndex)
        assertEquals(0, moved.cursorWeaponIndex)
    }

    @Test
    fun `initializeImpulse resets declaration state`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        // Toggle a weapon to record a declaration
        val (state, ts0) = enterDeclaring(controller, unit, gameState)
        val (_, ts1) = controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState, ts0)

        // Re-initializing wipes pending declarations for the new impulse
        val ts2 = controller.initializeImpulse(ts1, PlayerId.PLAYER_1)
        val (ts3, _) = controller.commitImpulse(ts2)
        assertTrue(controller.collectDeclarations(ts3).isEmpty())
    }

    @Test
    fun `torso twist clears assignments for targets that leave the arc`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), aGameMap(cols = 5, rows = 5))
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        // Toggle weapon on
        val (toggled, ts1) = controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState, ts0)
        val withWeapon = (toggled as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertTrue(withWeapon.weaponAssignments.values.any { it.isNotEmpty() })

        // Twist torso away — if enemy leaves the arc, assignments should be cleared
        val (twisted, _) = controller.handle(AttackAction.TwistTorso(clockwise = true), withWeapon, HexCoordinates(2, 2), gameState, ts1)
        val twistedState = (twisted as PhaseOutcome.Continue).phaseState as AttackPhaseState
        val twistedTargetIds = validTargets(unit, twistedState.torsoFacing, gameState)

        // If enemy is no longer in arc, assignments should be empty
        if (enemy.id !in twistedTargetIds) {
            assertTrue(twistedState.weaponAssignments.values.all { it.isEmpty() })
        }
    }

    @Test
    fun `TwistTorso and NavigateWeapons work independently`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser(), mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        // TwistTorso clockwise
        val (twisted, _) = controller.handle(AttackAction.TwistTorso(clockwise = true), state, HexCoordinates(2, 2), gameState, ts0)
        val twistedState = (twisted as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(HexDirection.NE, twistedState.torsoFacing)

        // NavigateWeapons down
        val (navigated, _) = controller.handle(AttackAction.NavigateWeapons(delta = 1), state, HexCoordinates(0, 0), gameState, ts0)
        val navigatedState = (navigated as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(state.torsoFacing, navigatedState.torsoFacing) // torso unchanged
        assertEquals(1, navigatedState.cursorWeaponIndex) // cursor moved
    }

    @Test
    fun `commitImpulse returns torso facings for committed units`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(3, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)

        val (state, ts0) = enterDeclaring(controller, unit, gameState)
        // Twist torso clockwise (N -> NE)
        val (twisted, ts1) = controller.handle(AttackAction.TwistTorso(clockwise = true), state, unit.position, gameState, ts0)
        val twistedState = (twisted as PhaseOutcome.Continue).phaseState as AttackPhaseState
        val (_, ts2) = controller.handle(AttackAction.Confirm, twistedState, unit.position, gameState, ts1)

        val (_, result) = controller.commitImpulse(ts2)

        assertThat(result.torsoFacings).containsEntry(unit.id, HexDirection.NE)
    }

    @Test
    fun `toggle off last weapon on primary with no secondary clears primaryTargetId`() {
        val controller = createController()
        val unit = aUnit(weapons = listOf(mediumLaser()), position = HexCoordinates(2, 2), facing = HexDirection.N)
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(unit, enemy), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)

        val (onResult, ts1) = controller.handle(AttackAction.ToggleWeapon, state, enemy.position, gameState, ts0)
        val onState = (onResult as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(enemy.id, onState.primaryTargetId)

        val (offResult, _) = controller.handle(AttackAction.ToggleWeapon, onState, enemy.position, gameState, ts1)
        val offState = (offResult as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertThat(offState.primaryTargetId).isNull()
    }

    @Test
    fun `toggle off last weapon on primary promotes secondary to primary`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser(), mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy1 = aUnit(id = "enemy1", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val enemy2 = aUnit(id = "enemy2", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
        val gameState = GameState(listOf(unit, enemy1, enemy2), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)
        val targets = targetInfos(unit, state.torsoFacing, gameState)

        val enemy1Idx = targets.indexOfFirst { it.unitId == enemy1.id }
        assertTrue(enemy1Idx >= 0, "enemy1 must be a valid target")

        // Directly set up: weapon 0 → enemy1 (primary), weapon 1 → enemy2 (secondary)
        val setup = state.copy(
            weaponAssignments = mapOf(enemy1.id to setOf(0), enemy2.id to setOf(1)),
            primaryTargetId = enemy1.id,
            cursorTargetIndex = enemy1Idx,
            cursorWeaponIndex = 0,
        )

        // Toggle off weapon 0 from enemy1 → enemy2 should become primary
        val (result, _) = controller.handle(AttackAction.ToggleWeapon, setup, enemy1.position, gameState, ts0)
        val resultState = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(enemy2.id, resultState.primaryTargetId)
    }

    @Test
    fun `toggle off last weapon on secondary leaves primary unchanged`() {
        val controller = createController()
        val unit = aUnit(
            weapons = listOf(mediumLaser(), mediumLaser()),
            position = HexCoordinates(2, 2),
            facing = HexDirection.N,
        )
        val enemy1 = aUnit(id = "enemy1", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 1))
        val enemy2 = aUnit(id = "enemy2", owner = PlayerId.PLAYER_2, position = HexCoordinates(2, 0))
        val gameState = GameState(listOf(unit, enemy1, enemy2), map5x5)
        val (state, ts0) = enterDeclaring(controller, unit, gameState)
        val targets = targetInfos(unit, state.torsoFacing, gameState)

        val enemy2Idx = targets.indexOfFirst { it.unitId == enemy2.id }
        assertTrue(enemy2Idx >= 0, "enemy2 must be a valid target")

        // Directly set up: weapon 0 → enemy1 (primary), weapon 1 → enemy2 (secondary)
        val setup = state.copy(
            weaponAssignments = mapOf(enemy1.id to setOf(0), enemy2.id to setOf(1)),
            primaryTargetId = enemy1.id,
            cursorTargetIndex = enemy2Idx,
            cursorWeaponIndex = 1,
        )

        // Toggle off weapon 1 from enemy2 → enemy1 remains primary
        val (result, _) = controller.handle(AttackAction.ToggleWeapon, setup, enemy2.position, gameState, ts0)
        val resultState = (result as PhaseOutcome.Continue).phaseState as AttackPhaseState
        assertEquals(enemy1.id, resultState.primaryTargetId)
    }
}
