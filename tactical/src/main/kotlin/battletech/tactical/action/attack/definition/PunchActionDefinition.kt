package battletech.tactical.action.attack.definition

import battletech.tactical.action.ActionPreview
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.action.attack.AttackDefinition
import battletech.tactical.action.attack.AttackRule
import battletech.tactical.action.attack.PhysicalAttackContext
import battletech.tactical.action.attack.PhysicalAttackPreview
import battletech.tactical.action.attack.rule.AdjacentRule
import battletech.tactical.action.attack.rule.HeatPenaltyRule
import battletech.tactical.model.GameState

public class PunchActionDefinition : AttackDefinition<PhysicalAttackContext> {

    override val phase: TurnPhase = TurnPhase.PHYSICAL_ATTACK

    override val name: String = "Punch"

    override val rules: List<AttackRule<PhysicalAttackContext>> = listOf(
        AdjacentRule(),
        HeatPenaltyRule(),
    )

    override fun expand(actor: Unit, gameState: GameState): List<PhysicalAttackContext> {
        val enemies = gameState.units.filter { it.id != actor.id }
        return enemies.map { target ->
            PhysicalAttackContext(
                actor = actor,
                target = target,
                gameState = gameState,
            )
        }
    }

    override fun preview(context: PhysicalAttackContext): ActionPreview = PhysicalAttackPreview(
        expectedDamage = PUNCH_DAMAGE..PUNCH_DAMAGE,
    )

    override fun successChance(context: PhysicalAttackContext): Int =
        TWO_D6_PROBABILITY.getOrElse(context.actor.pilotingSkill) { 0 }

    override fun actionName(context: PhysicalAttackContext): String =
        "Punch ${context.target.name}"

    private companion object {
        const val PUNCH_DAMAGE = 5

        val TWO_D6_PROBABILITY: Map<Int, Int> = mapOf(
            2 to 100,
            3 to 97,
            4 to 92,
            5 to 83,
            6 to 72,
            7 to 58,
            8 to 42,
            9 to 28,
            10 to 17,
            11 to 8,
            12 to 3,
        )
    }
}
