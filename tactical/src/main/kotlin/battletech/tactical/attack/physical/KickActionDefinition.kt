package battletech.tactical.attack.physical

import battletech.tactical.attack.AttackDefinition
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.PhysicalAttackContext
import battletech.tactical.attack.weapon.HeatPenaltyRule
import battletech.tactical.model.GameState
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.ActionPreview
import battletech.tactical.unit.CombatUnit

public class KickActionDefinition : AttackDefinition<PhysicalAttackContext> {

    override val phase: TurnPhase = TurnPhase.PHYSICAL_ATTACK

    override val name: String = "Kick"

    override val rules: List<AttackRule<PhysicalAttackContext>> = listOf(
        AdjacentRule(),
        HeatPenaltyRule(),
    )

    override fun expand(actor: CombatUnit, gameState: GameState): List<PhysicalAttackContext> {
        val enemies = gameState.units.filter { it.id != actor.id }
        return enemies.map { target ->
            PhysicalAttackContext(actor = actor, target = target, gameState = gameState)
        }
    }

    override fun preview(context: PhysicalAttackContext): ActionPreview {
        val damage = kickDamage(context.actor)
        return PhysicalAttackPreview(expectedDamage = damage..damage)
    }

    override fun successChance(context: PhysicalAttackContext): Int =
        twoD6AtLeastProbability(context.actor.pilotingSkill + KICK_MODIFIER)

    override fun actionName(context: PhysicalAttackContext): String =
        "Kick ${context.target.name}"

    private companion object {
        /** Total Warfare: a kick is −2 to the to-hit target number. */
        const val KICK_MODIFIER = -2
    }
}
