package battletech.tui.view

import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.TurnState
import battletech.tactical.unit.Weapon
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.game.phase.AttackPhase
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end integration: AppState → declaredTargetsRender → DeclaredTargetsView.render.
 * Covers the full chain without starting the TUI binary.
 */
internal class DeclaredTargetsIntegrationTest {

    private val map = aGameMap(cols = 7, rows = 7)

    private fun mediumLaser() = Weapon(
        name = "Medium Laser", damage = 5, heat = 3,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    @Test
    fun `Declaring phase produces non-null render struct with draft entry`() {
        val attacker = aUnit(
            id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N,
            weapons = listOf(mediumLaser()),
        )
        val target = aUnit(
            id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(2, 1),
        )
        val gameState = GameState(listOf(attacker, target), map)
        val turnState = TurnState(
            initiative = Initiative(
                rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
                loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
            ),
            attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
        )

        val phase = AttackPhase.Declaring(
            attackTurnPhase = TurnPhase.WEAPON_ATTACK,
            unitId = attacker.id,
            torsoFacing = HexDirection.N,
            cursorTargetIndex = 0,
            cursorWeaponIndex = 0,
            weaponAssignments = mapOf(target.id to setOf(0)),
            primaryTargetId = target.id,
            drafts = emptyMap(),
        )

        val renderData = phase.declaredTargetsRender(gameState, turnState, PlayerId.PLAYER_1)

        assertNotNull(renderData)
        assertEquals(1, renderData!!.entries.size)
        val entry = renderData.entries[0]
        assertTrue(entry.isDraft)
        assertEquals("Wolverine", entry.attackerName)
        assertEquals(PlayerId.PLAYER_1, entry.ownerPlayer)
        assertEquals(1, entry.targets.size)
        assertEquals("Atlas", entry.targets[0].targetName)
        assertTrue(entry.targets[0].isPrimary)
        assertEquals(1, entry.targets[0].weapons.size)
        assertEquals("Medium Laser", entry.targets[0].weapons[0].weaponName)
    }

    @Test
    fun `DeclaredTargetsView renders draft entry in gray`() {
        val attacker = aUnit(
            id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N,
            weapons = listOf(mediumLaser()),
        )
        val target = aUnit(
            id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(2, 1),
        )
        val gameState = GameState(listOf(attacker, target), map)
        val turnState = TurnState(
            initiative = Initiative(
                rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
                loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
            ),
            attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
        )

        val phase = AttackPhase.Declaring(
            attackTurnPhase = TurnPhase.WEAPON_ATTACK,
            unitId = attacker.id,
            torsoFacing = HexDirection.N,
            cursorTargetIndex = 0,
            cursorWeaponIndex = 0,
            weaponAssignments = mapOf(target.id to setOf(0)),
            primaryTargetId = target.id,
            drafts = emptyMap(),
        )

        val renderData = phase.declaredTargetsRender(gameState, turnState, PlayerId.PLAYER_1)!!
        val buffer = ScreenBuffer(28, 30)
        DeclaredTargetsView(renderData).render(buffer, 0, 0, 28, 30)

        val rowText = { row: Int ->
            buildString { (0 until 28).forEach { col -> append(buffer.get(col, row).char) } }
        }
        val wolverineRow = (0 until 30).first { rowText(it).contains("Wolverine") }
        val colors = (2 until 28).map { col -> buffer.get(col, wolverineRow).fg }.toSet()
        assertTrue(colors.contains(Color.GRAY)) {
            "Expected Wolverine (draft) row to use Color.GRAY, got: $colors"
        }
    }

    @Test
    fun `SelectingAttacker with no drafts renders empty panel`() {
        val gameState = GameState(emptyList(), map)
        val turnState = TurnState(
            initiative = Initiative(
                rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
                loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
            ),
            attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
        )

        val phase = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)

        val renderData = phase.declaredTargetsRender(gameState, turnState, PlayerId.PLAYER_1)!!
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
