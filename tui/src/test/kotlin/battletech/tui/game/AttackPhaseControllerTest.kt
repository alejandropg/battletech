package battletech.tui.game

import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.input.InputAction
import battletech.tactical.action.ActionId
import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.PhaseActionReport
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnavailableAction
import battletech.tactical.action.UnavailabilityReason
import battletech.tactical.action.attack.WeaponAttackPreview
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Weapon
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AttackPhaseControllerTest {

    private fun createController(
        phase: TurnPhase = TurnPhase.WEAPON_ATTACK,
        actions: List<battletech.tactical.action.ActionOption> = emptyList(),
    ): AttackPhaseController {
        val actionQueryService = mockk<ActionQueryService>()
        every { actionQueryService.getAttackActions(any(), any(), any()) } returns PhaseActionReport(
            phase = phase,
            unitId = aUnit().id,
            actions = actions,
        )
        return AttackPhaseController(actionQueryService, phase)
    }

    @Test
    fun `enter produces phase state with available attacks`() {
        val attacks = listOf(
            AvailableAction(
                id = ActionId("ml-target"),
                name = "Medium Laser → Hunchback",
                successChance = 75,
                warnings = emptyList(),
                preview = WeaponAttackPreview(
                    expectedDamage = 5..5,
                    heatGenerated = 3,
                    ammoConsumed = null,
                ),
            ),
        )
        val controller = createController(actions = attacks)
        val unit = aUnit()
        val gameState = GameState(listOf(unit), aGameMap())

        val phaseState = controller.enter(unit, gameState)

        assertEquals(TurnPhase.WEAPON_ATTACK, phaseState.phase)
    }

    @Test
    fun `cancel returns cancelled`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = GameState(listOf(unit), aGameMap())
        val phaseState = controller.enter(unit, gameState)

        val result = controller.handleAction(InputAction.Cancel, phaseState, gameState)

        assertTrue(result is PhaseControllerResult.Cancelled)
    }

    @Test
    fun `confirm with no selection does nothing`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = GameState(listOf(unit), aGameMap())
        val phaseState = controller.enter(unit, gameState)

        val result = controller.handleAction(InputAction.Confirm, phaseState, gameState)

        assertTrue(result is PhaseControllerResult.UpdateState)
    }

    @Test
    fun `unavailable actions are tracked`() {
        val actions = listOf(
            UnavailableAction(
                id = ActionId("ac20-target"),
                name = "AC/20 → Target",
                reasons = listOf(UnavailabilityReason("OUT_OF_RANGE", "Target is out of range")),
            ),
        )
        val controller = createController(actions = actions)
        val unit = aUnit()
        val gameState = GameState(listOf(unit), aGameMap())

        val phaseState = controller.enter(unit, gameState)

        assertEquals(TurnPhase.WEAPON_ATTACK, phaseState.phase)
    }
}
