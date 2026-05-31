package battletech.tui.game

import battletech.tactical.attack.physical.PhysicalAttackKind
import battletech.tactical.attack.physical.Side
import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.DiceRoller
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.TurnState
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.game.phase.PhysicalAttackPhase
import battletech.tui.game.phase.enterPhysicalDeclaring
import com.github.ajalt.mordant.input.KeyboardEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PhysicalAttackPhaseTest {

    private val map = aGameMap(cols = 5, rows = 5)
    private val attacker = aUnit(id = "atk", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
    private val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 0))
    private val gameState = GameState(listOf(attacker, enemy), map)

    private fun turn() = TurnState(
        initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
            loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
        ),
        attackSequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
    )

    private fun scriptedRoller(vararg rolls: Int): DiceRoller = object : DiceRoller {
        private var index = 0
        // Scripted values first, then a varying sequence so cascaded initiative
        // re-rolls (which loop on ties) always terminate.
        override fun d6(): Int = (if (index < rolls.size) rolls[index] else index % 6 + 1).also { index++ }
    }

    private fun appWith(phase: battletech.tui.game.phase.Phase, roller: DiceRoller = scriptedRoller(3, 3, 1)) =
        AppState(gameState, turn(), phase, HexCoordinates(0, 0), roller)

    @Test
    fun `selecting an adjacent attacker enters the declaring phase`() {
        val app = appWith(PhysicalAttackPhase.SelectingAttacker())
        val transition = app.phase.handle(KeyboardEvent("Enter"), app)!!

        val phase = transition.app.phase
        assertThat(phase).isInstanceOf(PhysicalAttackPhase.Declaring::class.java)
        assertThat((phase as PhysicalAttackPhase.Declaring).unitId).isEqualTo(attacker.id)
    }

    @Test
    fun `toggling adds a physical attack to the draft`() {
        val declaring = enterPhysicalDeclaring(attacker.id, appWith(PhysicalAttackPhase.SelectingAttacker()), emptyMap())
        val app = appWith(declaring)
        val transition = declaring.handle(KeyboardEvent(" "), app)!!

        val phase = transition.app.phase as PhysicalAttackPhase.Declaring
        assertThat(phase.assignments.values.flatten()).isNotEmpty()
    }

    @Test
    fun `committing a punch resolves it against the session`() {
        val declaring = PhysicalAttackPhase.Declaring(
            unitId = attacker.id,
            cursorIndex = 0,
            assignments = mapOf(enemy.id to setOf(PhysicalAttackKind.Punch(Side.LEFT))),
        )
        val app = appWith(declaring)
        val armorBefore = totalArmor(app.session.gameState.unitById(enemy.id)!!.armor)

        declaring.handle(KeyboardEvent("c"), app)!!

        // The session resolved the punch (3+3 hit, ceil(50/10)=5 damage applied).
        val armorAfter = totalArmor(app.session.gameState.unitById(enemy.id)!!.armor)
        assertThat(armorAfter).isEqualTo(armorBefore - 5)
    }

    private fun totalArmor(a: battletech.tactical.unit.ArmorLayout): Int =
        a.head + a.centerTorso + a.centerTorsoRear + a.leftTorso + a.leftTorsoRear +
            a.rightTorso + a.rightTorsoRear + a.leftArm + a.rightArm + a.leftLeg + a.rightLeg
}
