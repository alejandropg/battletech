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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class FireWeaponActionDefinitionTest {

    private val definition = FireWeaponActionDefinition()

    @Test
    fun `phase is weapon attack`() {
        assertEquals(TurnPhase.WEAPON_ATTACK, definition.phase)
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

        assertEquals(5..5, preview.expectedDamage)
        assertEquals(3, preview.heatGenerated)
        assertNull(preview.ammoConsumed)
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

        assertEquals(20..20, preview.expectedDamage)
        assertEquals(7, preview.heatGenerated)
        assertEquals(1, preview.ammoConsumed)
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

        assertEquals(92, definition.successChance(context))
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

        assertEquals(72, definition.successChance(context))
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

        assertEquals(42, definition.successChance(context))
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

        assertEquals(0, definition.successChance(context))
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

        assertEquals("Fire Medium Laser at Hunchback", definition.actionName(context))
    }
}
