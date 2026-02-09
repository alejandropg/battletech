package battletech.tactical.action

import battletech.tactical.action.definition.FireWeaponActionDefinition
import battletech.tactical.action.definition.MoveActionDefinition
import battletech.tactical.action.definition.PunchActionDefinition
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Weapon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ActionQueryServiceTest {

    private val allDefinitions = listOf(
        MoveActionDefinition(),
        FireWeaponActionDefinition(),
        PunchActionDefinition(),
    )
    private val service = ActionQueryService(allDefinitions)

    @Test
    fun `report contains correct phase and unit id`() {
        val actor = aUnit()
        val gameState = aGameState(units = listOf(actor))

        val report = service.getActions(actor, TurnPhase.MOVEMENT, gameState)

        assertThat(report.phase).isEqualTo(TurnPhase.MOVEMENT)
        assertThat(report.unitId).isEqualTo(actor.id)
    }

    @Test
    fun `only includes actions for matching phase`() {
        val actor = aUnit(weapons = listOf(mediumLaser()))
        val enemy = aUnit(id = "enemy", position = HexCoordinates(3, 0))
        val gameState = aGameState(units = listOf(actor, enemy))

        val movementReport = service.getActions(actor, TurnPhase.MOVEMENT, gameState)
        val weaponReport = service.getActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

        assertThat(movementReport.actions).allSatisfy { action ->
            assertThat(action.name).contains("Move")
        }
        assertThat(weaponReport.actions).allSatisfy { action ->
            assertThat(action.name).contains("Fire")
        }
    }

    @Test
    fun `returns empty actions for phases without definitions`() {
        val actor = aUnit()
        val gameState = aGameState(units = listOf(actor))

        val report = service.getActions(actor, TurnPhase.INITIATIVE, gameState)

        assertThat(report.actions).isEmpty()
    }

    @Test
    fun `collects all reasons for unavailable actions`() {
        val destroyedWeaponNoAmmo = Weapon(
            name = "Broken AC/20",
            damage = 20,
            heat = 7,
            shortRange = 3,
            mediumRange = 6,
            longRange = 9,
            ammo = 0,
            destroyed = true,
        )
        val actor = aUnit(weapons = listOf(destroyedWeaponNoAmmo), position = HexCoordinates(0, 0))
        val enemy = aUnit(id = "enemy", position = HexCoordinates(3, 0))
        val gameState = aGameState(units = listOf(actor, enemy))

        val report = service.getActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

        assertThat(report.actions).hasSize(1)
        val action = report.actions[0]
        assertThat(action).isInstanceOf(UnavailableAction::class.java)
        val unavailable = action as UnavailableAction
        assertThat(unavailable.reasons.map { it.code }).containsExactlyInAnyOrder(
            "WEAPON_DESTROYED",
            "NO_AMMO",
        )
    }

    @Test
    fun `includes warnings for available actions with penalties`() {
        val actor = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(0, 0),
            currentHeat = 13,
            heatSinkCapacity = 10,
        )
        val enemy = aUnit(id = "enemy", position = HexCoordinates(2, 0))
        val gameState = aGameState(units = listOf(actor, enemy))

        val report = service.getActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

        assertThat(report.actions).hasSize(1)
        val action = report.actions[0]
        assertThat(action).isInstanceOf(AvailableAction::class.java)
        val available = action as AvailableAction
        assertThat(available.warnings).isNotEmpty
        assertThat(available.warnings[0].code).isEqualTo("HEAT_PENALTY")
    }

    @Test
    fun `one action per weapon and target combination`() {
        val actor = aUnit(
            weapons = listOf(mediumLaser(), srm4()),
            position = HexCoordinates(0, 0),
        )
        val enemy1 = aUnit(id = "enemy-1", name = "Enemy 1", position = HexCoordinates(2, 0))
        val enemy2 = aUnit(id = "enemy-2", name = "Enemy 2", position = HexCoordinates(3, 0))
        val gameState = aGameState(units = listOf(actor, enemy1, enemy2))

        val report = service.getActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

        assertThat(report.actions).hasSize(4)
    }

    @Test
    fun `atlas scenario - two available weapons and one unavailable`() {
        val mediumLaser = mediumLaser()
        val srm4 = srm4()
        val destroyedAc20 = Weapon(
            name = "AC/20",
            damage = 20,
            heat = 7,
            minimumRange = 3,
            shortRange = 3,
            mediumRange = 6,
            longRange = 9,
            ammo = 0,
            destroyed = true,
        )

        val atlas = aUnit(
            id = "atlas",
            name = "Atlas",
            gunnerySkill = 4,
            weapons = listOf(mediumLaser, srm4, destroyedAc20),
            position = HexCoordinates(5, 5),
        )
        val hunchback = aUnit(
            id = "hunchback",
            name = "Hunchback",
            gunnerySkill = 4,
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(7, 5),
        )
        val gameState = aGameState(units = listOf(atlas, hunchback))

        val report = service.getActions(atlas, TurnPhase.WEAPON_ATTACK, gameState)

        assertThat(report.phase).isEqualTo(TurnPhase.WEAPON_ATTACK)
        assertThat(report.unitId).isEqualTo(atlas.id)
        assertThat(report.actions).hasSize(3)

        val available = report.actions.filterIsInstance<AvailableAction>()
        val unavailable = report.actions.filterIsInstance<UnavailableAction>()

        assertThat(available).hasSize(2)
        assertThat(available.map { it.name }).containsExactlyInAnyOrder(
            "Fire Medium Laser at Hunchback",
            "Fire SRM-4 at Hunchback",
        )

        assertThat(unavailable).hasSize(1)
        val brokenAction = unavailable[0]
        assertThat(brokenAction.name).isEqualTo("Fire AC/20 at Hunchback")
        assertThat(brokenAction.reasons.map { it.code }).containsExactlyInAnyOrder(
            "WEAPON_DESTROYED",
            "NO_AMMO",
        )
    }
}
