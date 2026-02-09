package battletech.tactical.action.definition

import battletech.tactical.action.ActionContext
import battletech.tactical.action.ActionDefinition
import battletech.tactical.action.ActionPreview
import battletech.tactical.action.ActionRule
import battletech.tactical.action.RuleResult
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.action.rule.HasAmmoRule
import battletech.tactical.action.rule.HeatPenaltyRule
import battletech.tactical.action.rule.InRangeRule
import battletech.tactical.action.rule.WeaponNotDestroyedRule
import battletech.tactical.model.GameState
import kotlin.math.ceil

public class FireWeaponActionDefinition : ActionDefinition {

    override val phase: TurnPhase = TurnPhase.WEAPON_ATTACK

    override val name: String = "Fire Weapon"

    override val rules: List<ActionRule> = listOf(
        WeaponNotDestroyedRule(),
        HasAmmoRule(),
        InRangeRule(),
        HeatPenaltyRule(),
    )

    override fun expand(actor: Unit, gameState: GameState): List<ActionContext> {
        val enemies = gameState.units.filter { it.id != actor.id }
        return actor.weapons.flatMap { weapon ->
            enemies.map { target ->
                ActionContext(
                    actor = actor,
                    target = target,
                    weapon = weapon,
                    gameState = gameState,
                )
            }
        }
    }

    override fun preview(context: ActionContext): ActionPreview {
        val weapon = context.weapon ?: return ActionPreview()
        return ActionPreview(
            expectedDamage = weapon.damage..weapon.damage,
            heatGenerated = weapon.heat,
            ammoConsumed = if (weapon.ammo != null) 1 else null,
        )
    }

    override fun successChance(context: ActionContext): Int {
        val weapon = context.weapon ?: return 0
        val target = context.target ?: return 0
        val distance = context.actor.position.distanceTo(target.position)
        val rangeModifier = when {
            distance <= weapon.shortRange -> 0
            distance <= weapon.mediumRange -> 2
            distance <= weapon.longRange -> 4
            else -> return 0
        }

        var targetNumber = context.actor.gunnerySkill + rangeModifier

        val heatPenalty = heatPenaltyModifier(context)
        targetNumber += heatPenalty

        return TWO_D6_PROBABILITY.getOrElse(targetNumber) { 0 }
    }

    override fun actionName(context: ActionContext): String {
        val weaponName = context.weapon?.name ?: "Unknown Weapon"
        val targetName = context.target?.name ?: "Unknown Target"
        return "Fire $weaponName at $targetName"
    }

    private fun heatPenaltyModifier(context: ActionContext): Int {
        val excessHeat = context.actor.currentHeat - context.actor.heatSinkCapacity
        return if (excessHeat <= 0) 0 else ceil(excessHeat / 3.0).toInt()
    }

    private companion object {
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
