package battletech.tactical.attack.weapon

import battletech.tactical.attack.AttackDefinition
import battletech.tactical.attack.AttackRule
import battletech.tactical.attack.WeaponAttackContext
import battletech.tactical.attack.total
import battletech.tactical.attack.weaponToHitModifiers
import battletech.tactical.model.GameState
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.ActionPreview
import battletech.tactical.unit.CombatUnit

public class FireWeaponActionDefinition : AttackDefinition<WeaponAttackContext> {

    override val phase: TurnPhase = TurnPhase.WEAPON_ATTACK

    override val name: String = "Fire Weapon"

    override val rules: List<AttackRule<WeaponAttackContext>> = listOf(
        WeaponNotDestroyedRule(),
        HasAmmoRule(),
        InRangeRule(),
        HeatPenaltyRule(),
    )

    override fun expand(actor: CombatUnit, gameState: GameState): List<WeaponAttackContext> {
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
            ammoConsumed = if (weapon.ammoType != null) 1 else null,
        )
    }

    override fun successChance(context: WeaponAttackContext): Int {
        val weapon = context.weapon
        val distance = context.actor.position.distanceTo(context.target.position)
        if (distance > weapon.longRange) return 0
        val modifiers = weaponToHitModifiers(context.actor, context.target, weapon, distance, isPrimaryTarget = true)
        val targetNumber = context.actor.gunnerySkill + modifiers.total()
        return TWO_D6_PROBABILITY.getOrElse(targetNumber.coerceAtLeast(2)) { 0 }
    }

    override fun actionName(context: WeaponAttackContext): String =
        "Fire ${context.weapon.name} at ${context.target.name}"

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
