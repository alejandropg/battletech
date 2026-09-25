package io.archinaut.battletech.tui.game

import io.archinaut.battletech.tactical.attack.physical.PhysicalAttackKind
import io.archinaut.battletech.tactical.attack.physical.Side
import io.archinaut.battletech.tactical.dice.DiceRoll
import io.archinaut.battletech.tactical.dice.DiceRoller
import io.archinaut.battletech.tactical.model.GameState
import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.session.Impulse
import io.archinaut.battletech.tactical.session.ImpulseSequence
import io.archinaut.battletech.tactical.session.Initiative
import io.archinaut.battletech.tactical.session.TurnState
import io.archinaut.battletech.tactical.unit.UnitRoster
import io.archinaut.battletech.tui.aGameMap
import io.archinaut.battletech.tui.aUnit
import io.archinaut.battletech.tui.game.phase.PhysicalAttackPhase
import io.archinaut.battletech.tui.game.phase.enterPhysicalDeclaring
import io.archinaut.battletech.tui.input.AttackAction
import io.archinaut.battletech.tui.input.IdleAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PhysicalAttackPhaseTest {

    private val map = aGameMap(cols = 5, rows = 5)
    private val attacker = aUnit(id = "atk", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
    private val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(1, 0))
    private val gameState = GameState(UnitRoster(listOf(attacker, enemy)), map)

    private fun turn() = TurnState(
        initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
            loser = PlayerId.PLAYER_1, winner = PlayerId.PLAYER_2,
        ),
        attack = io.archinaut.battletech.tactical.session.AttackProgress(
            sequence = ImpulseSequence(listOf(Impulse(PlayerId.PLAYER_1, 1))),
        ),
    )

    private fun scriptedRoller(vararg rolls: Int): DiceRoller = object : DiceRoller {
        private var index = 0
        // Scripted values first, then a varying sequence so cascaded initiative
        // re-rolls (which loop on ties) always terminate.
        override fun d6(): Int = (if (index < rolls.size) rolls[index] else index % 6 + 1).also { index++ }
    }

    private fun appWith(phase: io.archinaut.battletech.tui.game.phase.Phase, roller: DiceRoller = scriptedRoller(3, 3, 1)) =
        AppState(gameState, turn(), phase, HexCoordinates(0, 0), roller)

    @Test
    fun `selecting an adjacent attacker enters the declaring phase`() {
        val app = appWith(PhysicalAttackPhase.SelectingAttacker())
        val transition = app.phase.handle(IdleAction.SelectUnit, app)!!

        val phase = transition.app.phase
        assertThat(phase).isInstanceOf(PhysicalAttackPhase.Declaring::class.java)
        assertThat((phase as PhysicalAttackPhase.Declaring).unitId).isEqualTo(attacker.id)
    }

    @Test
    fun `toggling adds a physical attack to the draft`() {
        val declaring = enterPhysicalDeclaring(attacker.id, emptyMap())
        val app = appWith(declaring)
        val transition = declaring.handle(AttackAction.ToggleWeapon, app)!!

        val phase = transition.app.phase as PhysicalAttackPhase.Declaring
        assertThat(phase.assignments.values.flatten()).isNotEmpty()
    }

    @Test
    fun `escape cancels back to SelectingAttacker`() {
        val declaring = enterPhysicalDeclaring(attacker.id, emptyMap())
        val app = appWith(declaring)
        val transition = declaring.handle(AttackAction.Cancel, app)!!

        assertThat(transition.app.phase).isInstanceOf(PhysicalAttackPhase.SelectingAttacker::class.java)
    }

    @Test
    fun `committing a punch resolves it against the session`() {
        val declaring = PhysicalAttackPhase.Declaring(
            unitId = attacker.id,
            cursorIndex = 0,
            assignments = mapOf(enemy.id to setOf(PhysicalAttackKind.Punch(Side.LEFT))),
        )
        val app = appWith(declaring)
        val armorBefore = totalArmor(app.state.units.byId(enemy.id).armor)

        declaring.handle(AttackAction.Commit, app)!!

        // The session resolved the punch (3+3 hit, ceil(50/10)=5 damage applied). app.session
        // is the same mutable BattleSession before and after, so re-reading app.state
        // (not a fresh AppState) observes the post-command state.
        val armorAfter = totalArmor(app.state.units.byId(enemy.id).armor)
        assertThat(armorAfter).isEqualTo(armorBefore - 5)
    }

    private fun totalArmor(a: io.archinaut.battletech.tactical.unit.ArmorLayout): Int =
        a.head + a.centerTorso + a.centerTorsoRear + a.leftTorso + a.leftTorsoRear +
            a.rightTorso + a.rightTorsoRear + a.leftArm + a.rightArm + a.leftLeg + a.rightLeg
}
