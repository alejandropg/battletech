package battletech.tactical.action

import battletech.tactical.action.attack.WeaponAttackPreview
import battletech.tactical.action.attack.definition.FireWeaponActionDefinition
import battletech.tactical.action.attack.definition.PunchActionDefinition
import battletech.tactical.action.movement.MoveActionDefinition
import battletech.tactical.action.movement.MovementPreview
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.Weapon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ActionQueryServiceTest {

    private val service = ActionQueryService(
        movementDefinitions = listOf(MoveActionDefinition()),
        attackDefinitions = listOf(FireWeaponActionDefinition(), PunchActionDefinition()),
    )

    @Test
    fun `movement report contains correct phase and unit id`() {
        val actor = aUnit()
        val gameState = aGameState(units = listOf(actor))

        val report = service.getMovementActions(actor, gameState)

        assertEquals(TurnPhase.MOVEMENT, report.phase)
        assertEquals(actor.id, report.unitId)
    }

    @Test
    fun `attack report contains correct phase and unit id`() {
        val actor = aUnit()
        val gameState = aGameState(units = listOf(actor))

        val report = service.getAttackActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

        assertEquals(TurnPhase.WEAPON_ATTACK, report.phase)
        assertEquals(actor.id, report.unitId)
    }

    @Test
    fun `movement actions only contain movement names`() {
        val actor = aUnit(weapons = listOf(mediumLaser()))
        val enemy = aUnit(id = "enemy", position = HexCoordinates(3, 0))
        val gameState = aGameState(units = listOf(actor, enemy))

        val movementReport = service.getMovementActions(actor, gameState)

        assertThat(movementReport.actions).allSatisfy { action ->
            assertThat(action.name).containsAnyOf("Walk", "Run", "Jump")
        }
    }

    @Test
    fun `attack actions only contain attack names`() {
        val actor = aUnit(weapons = listOf(mediumLaser()))
        val enemy = aUnit(id = "enemy", position = HexCoordinates(3, 0))
        val gameState = aGameState(units = listOf(actor, enemy))

        val weaponReport = service.getAttackActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

        assertThat(weaponReport.actions).allSatisfy { action ->
            assertThat(action.name).contains("Fire")
        }
    }

    @Test
    fun `returns empty actions for phases without definitions`() {
        val actor = aUnit()
        val gameState = aGameState(units = listOf(actor))

        val report = service.getAttackActions(actor, TurnPhase.INITIATIVE, gameState)

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

        val report = service.getAttackActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

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

        val report = service.getAttackActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

        assertThat(report.actions).hasSize(1)
        val action = report.actions[0]
        assertThat(action).isInstanceOf(AvailableAction::class.java)
        val available = action as AvailableAction
        assertThat(available.warnings).isNotEmpty
        assertEquals("HEAT_PENALTY", available.warnings[0].code)
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

        val report = service.getAttackActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

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

        val report = service.getAttackActions(atlas, TurnPhase.WEAPON_ATTACK, gameState)

        assertEquals(TurnPhase.WEAPON_ATTACK, report.phase)
        assertEquals(atlas.id, report.unitId)
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
        assertEquals("Fire AC/20 at Hunchback", brokenAction.name)
        assertThat(brokenAction.reasons.map { it.code }).containsExactlyInAnyOrder(
            "WEAPON_DESTROYED",
            "NO_AMMO",
        )
    }

    @Test
    fun `movement phase returns walk and run actions`() {
        val origin = HexCoordinates(0, 0)
        val hexes = mapOf(
            origin to Hex(origin),
            HexCoordinates(0, -1) to Hex(HexCoordinates(0, -1)),
        )
        val actor = aUnit(position = origin, walkingMP = 4, runningMP = 6, jumpMP = 0)
        val gameState = aGameState(units = listOf(actor), hexes = hexes)

        val report = service.getMovementActions(actor, gameState)

        assertThat(report.actions).hasSize(2)
        assertThat(report.actions.map { it.name }).containsExactlyInAnyOrder(
            "Walk Test Mech",
            "Run Test Mech",
        )
    }

    @Test
    fun `movement preview contains reachability map`() {
        val origin = HexCoordinates(0, 0)
        val hexes = mapOf(
            origin to Hex(origin),
            HexCoordinates(0, -1) to Hex(HexCoordinates(0, -1)),
        )
        val actor = aUnit(position = origin, walkingMP = 4, runningMP = 6)
        val gameState = aGameState(units = listOf(actor), hexes = hexes)

        val report = service.getMovementActions(actor, gameState)

        val available = report.actions.filterIsInstance<AvailableAction>()
        assertThat(available).isNotEmpty
        available.forEach { action ->
            val preview = action.preview as MovementPreview
            assertThat(preview.reachability.destinations).isNotEmpty()
        }
    }

    @Test
    fun `weapon attack preview contains damage and heat`() {
        val actor = aUnit(
            weapons = listOf(mediumLaser()),
            position = HexCoordinates(0, 0),
        )
        val enemy = aUnit(id = "enemy", position = HexCoordinates(2, 0))
        val gameState = aGameState(units = listOf(actor, enemy))

        val report = service.getAttackActions(actor, TurnPhase.WEAPON_ATTACK, gameState)

        val available = report.actions.filterIsInstance<AvailableAction>()
        assertThat(available).hasSize(1)
        val preview = available[0].preview as WeaponAttackPreview
        assertEquals(5..5, preview.expectedDamage)
        assertEquals(3, preview.heatGenerated)
    }
}
