package battletech.tui.view

import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.Impulse
import battletech.tactical.unit.UnitRoster
import battletech.tui.aGameMap
import battletech.tui.anAppState
import battletech.tui.aTurnState
import battletech.tui.aUnit
import battletech.tui.game.phase.AttackPhase
import battletech.tui.game.phase.WeaponAllocation
import battletech.tui.mediumLaser
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end integration: AppState → declaredTargetsRender → DeclaredTargetsView.render.
 * Covers the full chain without starting the TUI binary; the individual stages are
 * covered by DeclaredTargetsRenderTest and DeclaredTargetsViewTest.
 */
internal class DeclaredTargetsIntegrationTest {

    private val map = aGameMap(cols = 7, rows = 7)
    private val turnState = aTurnState(attackOrder = listOf(Impulse(PlayerId.PLAYER_1, 1)))

    @Test
    fun `Declaring phase draft flows through render data to the view in gray`() {
        val attacker = aUnit(
            id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N,
            weapons = listOf(mediumLaser()),
        )
        val target = aUnit(
            id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(2, 1),
        )
        val gameState = GameState(UnitRoster(listOf(attacker, target)), map)
        val phase = AttackPhase.Declaring(
            attackTurnPhase = TurnPhase.WEAPON_ATTACK,
            unitId = attacker.id,
            allocation = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(target.id to setOf(0)),
                primaryTargetId = target.id,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
            ),
            drafts = emptyMap(),
        )

        val renderData = phase.declaredTargetsRender(anAppState(phase, gameState = gameState, turnState = turnState))
        val buffer = ScreenBuffer(28, 30)
        DeclaredTargetsView(renderData).render(buffer, 0, 0, 28, 30)

        val rowText = { row: Int ->
            buildString { (0 until 28).forEach { col -> append(buffer.get(col, row).char) } }
        }
        val wolfRow = (0 until 30).first { rowText(it).contains("wolf") }
        val colors = (2 until 28).map { col -> buffer.get(col, wolfRow).style.fg }.toSet()
        assertTrue(colors.contains(Color.GRAY)) {
            "Expected wolf (draft) row to use Color.GRAY, got: $colors"
        }
    }

    @Test
    fun `SelectingAttacker with no drafts renders empty panel`() {
        val gameState = GameState(UnitRoster(emptyList()), map)
        val phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)

        val renderData = phase.declaredTargetsRender(anAppState(phase, gameState = gameState, turnState = turnState))
        val buffer = ScreenBuffer(28, 20)
        DeclaredTargetsView(renderData).render(buffer, 0, 0, 28, 20)

        val output = buildString {
            for (row in 0 until 20) {
                for (col in 0 until 28) append(buffer.get(col, row).char)
                appendLine()
            }
        }
        assertTrue(output.contains("No declarations"))
    }
}
