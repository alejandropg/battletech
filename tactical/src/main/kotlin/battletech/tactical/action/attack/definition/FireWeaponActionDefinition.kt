package battletech.tactical.action.attack.definition

import battletech.tactical.action.ActionPreview
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.Unit
import battletech.tactical.action.attack.AttackDefinition
import battletech.tactical.action.attack.AttackRule
import battletech.tactical.action.attack.WeaponAttackContext
import battletech.tactical.action.attack.WeaponAttackPreview
import battletech.tactical.action.attack.rule.HasAmmoRule
import battletech.tactical.action.attack.rule.HeatPenaltyRule
import battletech.tactical.action.attack.rule.InRangeRule
import battletech.tactical.action.attack.rule.WeaponNotDestroyedRule
import battletech.tactical.model.GameState
import kotlin.math.ceil

public class FireWeaponActionDefinition : AttackDefinition<WeaponAttackContext> {

    override val phase: TurnPhase = TurnPhase.WEAPON_ATTACK

    override val name: String = "Fire Weapon"

    override val rules: List<AttackRule<WeaponAttackContext>> = listOf(
        WeaponNotDestroyedRule(),
        HasAmmoRule(),
        InRangeRule(),
        HeatPenaltyRule(),
    )

    override fun expand(actor: Unit, gameState: GameState): List<WeaponAttackContext> {
        val enemies = gameState.units.filter { it.id != actor.id }
        return actor.weapons.flatMap { weapon ->
            enemies.map { target ->
                WeaponAttackContext(
                    actor = actor,
                    target = target,
                    weapon = weapon,
                    gameState = gameState,
                )
            }
        }
    }

    override fun preview(context: WeaponAttackContext): ActionPreview {
        val weapon = context.weapon
        return WeaponAttackPreview(
            expectedDamage = weapon.damage..weapon.damage,
            heatGenerated = weapon.heat,
            ammoConsumed = if (weapon.ammo != null) 1 else null,
        )
    }

    override fun successChance(context: WeaponAttackContext): Int {
        val weapon = context.weapon
        val distance = context.actor.position.distanceTo(context.target.position)
        val rangeModifier = when {
            distance <= weapon.shortRange -> 0
            distance <= weapon.mediumRange -> 2
            distance <= weapon.longRange -> 4
            else -> return 0
        }

        var targetNumber = context.actor.gunnerySkill + rangeModifier

        val heatPenalty = heatPenaltyModifier(context.actor)
        targetNumber += heatPenalty

        return TWO_D6_PROBABILITY.getOrElse(targetNumber) { 0 }
    }

    override fun actionName(context: WeaponAttackContext): String =
        "Fire ${context.weapon.name} at ${context.target.name}"

    private fun heatPenaltyModifier(actor: Unit): Int {
        val excessHeat = actor.currentHeat - actor.heatSinkCapacity
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
