package battletech.tactical.action.definition

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionDefinition
import battletech.tactical.action.ActionPreview
import battletech.tactical.action.ActionRule
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.action.rule.AdjacentRule
import battletech.tactical.action.rule.HeatPenaltyRule
import battletech.tactical.model.GameState

public class PunchActionDefinition : ActionDefinition {

    override val phase: TurnPhase = TurnPhase.PHYSICAL_ATTACK

    override val name: String = "Punch"

    override val rules: List<ActionRule> = listOf(
        AdjacentRule(),
        HeatPenaltyRule(),
    )

    override fun expand(actor: Unit, gameState: GameState): List<ActionContext> {
        val enemies = gameState.units.filter { it.id != actor.id }
        return enemies.map { target ->
            ActionContext(
                actor = actor,
                target = target,
                gameState = gameState,
            )
        }
    }

    override fun preview(context: ActionContext): ActionPreview = ActionPreview(
        expectedDamage = PUNCH_DAMAGE..PUNCH_DAMAGE,
    )

    override fun successChance(context: ActionContext): Int =
        TWO_D6_PROBABILITY.getOrElse(context.actor.pilotingSkill) { 0 }

    override fun actionName(context: ActionContext): String {
        val targetName = context.target?.name ?: "Unknown Target"
        return "Punch $targetName"
    }

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
