package battletech.tui.game

import battletech.tactical.action.ActionId
import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.AvailableAction
import battletech.tactical.action.PhaseActionReport
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnavailabilityReason
import battletech.tactical.action.UnavailableAction
import battletech.tactical.action.attack.WeaponAttackPreview
import battletech.tactical.model.GameState
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.input.InputAction
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AttackControllerTest {

    private fun createController(
        phase: TurnPhase = TurnPhase.WEAPON_ATTACK,
        actions: List<battletech.tactical.action.ActionOption> = emptyList(),
    ): AttackController {
        val actionQueryService = mockk<ActionQueryService>()
        every { actionQueryService.getAttackActions(any(), any(), any()) } returns PhaseActionReport(
            phase = phase,
            unitId = aUnit().id,
            actions = actions,
        )
        return AttackController(actionQueryService)
    }

    @Test
    fun `enter produces Attack state`() {
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

        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)

        assertEquals(TurnPhase.WEAPON_ATTACK, state.attackPhase)
        assertEquals(unit.id, state.unitId)
    }

    @Test
    fun `cancel returns Cancelled`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = GameState(listOf(unit), aGameMap())
        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)

        val result = controller.handle(InputAction.Cancel, state, gameState)

        assertTrue(result is PhaseOutcome.Cancelled)
    }

    @Test
    fun `confirm with no selection continues`() {
        val controller = createController()
        val unit = aUnit()
        val gameState = GameState(listOf(unit), aGameMap())
        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)

        val result = controller.handle(InputAction.Confirm, state, gameState)

        assertTrue(result is PhaseOutcome.Continue)
    }

    @Test
    fun `enter with no available attacks shows skip prompt`() {
        val controller = createController(actions = listOf(
            UnavailableAction(
                id = ActionId("ac20"),
                name = "AC/20",
                reasons = listOf(UnavailabilityReason("OUT_OF_RANGE", "Out of range")),
            ),
        ))
        val unit = aUnit()
        val gameState = GameState(listOf(unit), aGameMap())

        val state = controller.enter(unit, TurnPhase.WEAPON_ATTACK, gameState)

        assertTrue(state.prompt.contains("No attacks available"))
    }
}
