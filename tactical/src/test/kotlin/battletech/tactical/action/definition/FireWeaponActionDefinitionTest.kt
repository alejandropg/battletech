package battletech.tactical.action.definition

import battletech.tactical.action.TurnPhase
import battletech.tactical.action.ac20
import battletech.tactical.action.aGameState
import battletech.tactical.action.aUnit
import battletech.tactical.action.aWeapon
import battletech.tactical.action.mediumLaser
import battletech.tactical.action.rule.HasAmmoRule
import battletech.tactical.action.rule.HeatPenaltyRule
import battletech.tactical.action.rule.InRangeRule
import battletech.tactical.action.rule.WeaponNotDestroyedRule
import battletech.tactical.action.srm4
import battletech.tactical.model.HexCoordinates
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FireWeaponActionDefinitionTest {

    private val definition = FireWeaponActionDefinition()

    @Test
    fun `phase is weapon attack`() {
        assertThat(definition.phase).isEqualTo(TurnPhase.WEAPON_ATTACK)
    }

    @Test
    fun `rules include all four weapon rules`() {
        assertThat(definition.rules).hasSize(4)
        assertThat(definition.rules.map { it::class }).containsExactlyInAnyOrder(
            WeaponNotDestroyedRule::class,
            HasAmmoRule::class,
            InRangeRule::class,
            HeatPenaltyRule::class,
        )
    }

    @Test
    fun `expand creates one context per weapon and target combination`() {
        val actor = aUnit(
            weapons = listOf(mediumLaser(), srm4()),
            position = HexCoordinates(0, 0),
        )
        val enemy1 = aUnit(id = "enemy-1", name = "Enemy 1", position = HexCoordinates(3, 0))
        val enemy2 = aUnit(id = "enemy-2", name = "Enemy 2", position = HexCoordinates(5, 0))
        val gameState = aGameState(units = listOf(actor, enemy1, enemy2))

        val contexts = definition.expand(actor, gameState)

        assertThat(contexts).hasSize(4)
    }

    @Test
    fun `expand returns empty when unit has no weapons`() {
        val actor = aUnit(weapons = emptyList())
        val enemy = aUnit(id = "enemy", position = HexCoordinates(3, 0))
        val gameState = aGameState(units = listOf(actor, enemy))

        val contexts = definition.expand(actor, gameState)

        assertThat(contexts).isEmpty()
    }

    @Test
    fun `expand returns empty when no enemies exist`() {
        val actor = aUnit(weapons = listOf(mediumLaser()))
        val gameState = aGameState(units = listOf(actor))

        val contexts = definition.expand(actor, gameState)

        assertThat(contexts).isEmpty()
    }

    @Test
    fun `preview for energy weapon has damage and heat but no ammo consumed`() {
        val actor = aUnit(position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(2, 0))
        val weapon = mediumLaser()
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            target = target,
            weapon = weapon,
            gameState = aGameState(units = listOf(actor, target)),
        )

        val preview = definition.preview(context)

        assertThat(preview.expectedDamage).isEqualTo(5..5)
        assertThat(preview.heatGenerated).isEqualTo(3)
        assertThat(preview.ammoConsumed).isNull()
    }

    @Test
    fun `preview for ballistic weapon includes ammo consumed`() {
        val actor = aUnit(position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(2, 0))
        val weapon = ac20()
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            target = target,
            weapon = weapon,
            gameState = aGameState(units = listOf(actor, target)),
        )

        val preview = definition.preview(context)

        assertThat(preview.expectedDamage).isEqualTo(20..20)
        assertThat(preview.heatGenerated).isEqualTo(7)
        assertThat(preview.ammoConsumed).isEqualTo(1)
    }

    @Test
    fun `success chance for gunnery 4 at short range is 92 percent`() {
        val actor = aUnit(gunnerySkill = 4, position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(2, 0))
        val weapon = aWeapon(shortRange = 3, mediumRange = 6, longRange = 9)
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            target = target,
            weapon = weapon,
            gameState = aGameState(units = listOf(actor, target)),
        )

        assertThat(definition.successChance(context)).isEqualTo(92)
    }

    @Test
    fun `success chance for gunnery 4 at medium range is 72 percent`() {
        val actor = aUnit(gunnerySkill = 4, position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(5, 0))
        val weapon = aWeapon(shortRange = 3, mediumRange = 6, longRange = 9)
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            target = target,
            weapon = weapon,
            gameState = aGameState(units = listOf(actor, target)),
        )

        assertThat(definition.successChance(context)).isEqualTo(72)
    }

    @Test
    fun `success chance for gunnery 4 at long range is 42 percent`() {
        val actor = aUnit(gunnerySkill = 4, position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(8, 0))
        val weapon = aWeapon(shortRange = 3, mediumRange = 6, longRange = 9)
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            target = target,
            weapon = weapon,
            gameState = aGameState(units = listOf(actor, target)),
        )

        assertThat(definition.successChance(context)).isEqualTo(42)
    }

    @Test
    fun `success chance is zero when target number exceeds 12`() {
        val actor = aUnit(gunnerySkill = 4, position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", position = HexCoordinates(8, 0))
        val weapon = aWeapon(shortRange = 3, mediumRange = 6, longRange = 9)
        val overheatedActor = actor.copy(currentHeat = 30, heatSinkCapacity = 10)
        val context = battletech.tactical.action.ActionContext(
            actor = overheatedActor,
            target = target,
            weapon = weapon,
            gameState = aGameState(units = listOf(overheatedActor, target)),
        )

        assertThat(definition.successChance(context)).isEqualTo(0)
    }

    @Test
    fun `action name includes weapon and target names`() {
        val actor = aUnit(position = HexCoordinates(0, 0))
        val target = aUnit(id = "enemy", name = "Hunchback", position = HexCoordinates(3, 0))
        val weapon = mediumLaser()
        val context = battletech.tactical.action.ActionContext(
            actor = actor,
            target = target,
            weapon = weapon,
            gameState = aGameState(units = listOf(actor, target)),
        )

        assertThat(definition.actionName(context)).isEqualTo("Fire Medium Laser at Hunchback")
    }
}
