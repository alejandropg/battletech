package battletech.tactical.query

import battletech.tactical.attack.total
import battletech.tactical.attack.weaponToHitModifiers
import battletech.tactical.model.GameMap
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.Terrain
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class DefaultPlayerViewTest {

    private fun viewFor(vararg units: battletech.tactical.unit.CombatUnit): PlayerView =
        DefaultPlayerView(PlayerId.PLAYER_1, aGameState(units = units.toList()))

    private fun mapWithRadius(center: HexCoordinates, radius: Int): GameMap {
        val hexes = mutableMapOf<HexCoordinates, Hex>()
        for (col in (center.col - radius)..(center.col + radius)) {
            for (row in (center.row - radius)..(center.row + radius)) {
                val coords = HexCoordinates(col, row)
                if (center.distanceTo(coords) <= radius) {
                    hexes[coords] = Hex(coords, Terrain.CLEAR)
                }
            }
        }
        return GameMap(hexes)
    }

    @Test
    fun `publicUnit returns null for unknown unit id`() {
        val view = viewFor(aUnit(id = "u1"))
        assertNull(view.publicUnit(UnitId("unknown")))
    }

    @Test
    fun `publicUnit returns correct scalar fields`() {
        val unit = aUnit(
            id = "u1",
            name = "Hunchback",
            walkingMP = 4,
            runningMP = 6,
            jumpMP = 0,
            armor = anArmorLayout(head = 9, centerTorso = 47),
        )
        val result = viewFor(unit).publicUnit(UnitId("u1"))!!

        assertEquals(UnitId("u1"), result.id)
        assertEquals("Hunchback", result.name)
        assertEquals(4, result.walkingMP)
        assertEquals(6, result.runningMP)
        assertEquals(0, result.jumpMP)
        assertEquals(9, result.armor.head)
        assertEquals(47, result.armor.centerTorso)
    }

    @Test
    fun `publicUnit maps weapon names only`() {
        val unit = aUnit(
            id = "u1",
            weapons = listOf(aWeapon(name = "AC/20"), aWeapon(name = "Medium Laser")),
        )
        val result = viewFor(unit).publicUnit(UnitId("u1"))!!

        assertEquals(listOf(PublicWeapon("AC/20"), PublicWeapon("Medium Laser")), result.weapons)
    }

    @Test
    fun `publicUnit does not expose private fields`() {
        // PublicUnit has no gunnerySkill, pilotingSkill, currentHeat, heatSink fields —
        // verified by the fact that this file compiles without referencing them on PublicUnit.
        val unit = aUnit(id = "u1", gunnerySkill = 3, pilotingSkill = 4, currentHeat = 15, heatSink = HeatSink(HeatSinkType.STS, 10))
        val result = viewFor(unit).publicUnit(UnitId("u1"))!!

        // Only the public projection fields are accessible
        assertEquals("Test Mech", result.name)
        assertEquals(listOf("gunnerySkill", "pilotingSkill", "currentHeat", "heatSink").none { field ->
            result.javaClass.declaredFields.any { it.name == field }
        }, true)
    }

    @Test
    fun `publicUnit is accessible through PlayerView interface`() {
        val unit = aUnit(id = "u1", name = "Atlas")
        val view: PlayerView = DefaultPlayerView(PlayerId.PLAYER_1, aGameState(units = listOf(unit)))
        assertEquals("Atlas", view.publicUnit(UnitId("u1"))?.name)
    }

    @Test
    fun `validTargets excludes a destroyed enemy unit`() {
        val attacker = aUnit(id = "attacker", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
        val destroyedEnemy = aUnit(
            id = "destroyed-enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(0, 1),
            isDestroyed = true,
        )
        val state = aGameState(
            units = listOf(attacker, destroyedEnemy),
            hexes = mapWithRadius(HexCoordinates(0, 0), radius = 3).hexes,
        )
        val view = DefaultPlayerView(PlayerId.PLAYER_1, state)

        assertThat(view.validTargets(UnitId("attacker"), HexDirection.N)).isEmpty()
    }

    @Test
    fun `legalMovementsFor returns empty list for a destroyed unit`() {
        val destroyed = aUnit(id = "destroyed", isDestroyed = true)
        val view = viewFor(destroyed)

        assertThat(view.legalMovementsFor(UnitId("destroyed"))).isEmpty()
    }

    @Test
    fun `legalMovementsFor returns empty list for a unit with an unconscious pilot`() {
        val unconscious = aUnit(id = "unconscious", isPilotConscious = false)
        val view = viewFor(unconscious)

        assertThat(view.legalMovementsFor(UnitId("unconscious"))).isEmpty()
    }

    @Test
    fun `validTargets excludes an attacker with an unconscious pilot but still allows it as a target`() {
        val unconsciousAttacker = aUnit(
            id = "unconscious-attacker",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(0, 0),
            isPilotConscious = false,
        )
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, 1))
        val state = aGameState(
            units = listOf(unconsciousAttacker, enemy),
            hexes = mapWithRadius(HexCoordinates(0, 0), radius = 3).hexes,
        )
        val view = DefaultPlayerView(PlayerId.PLAYER_1, state)

        // Cannot act as an attacker.
        assertThat(view.validTargets(UnitId("unconscious-attacker"), HexDirection.N)).isEmpty()

        // But remains a valid target for the enemy.
        val enemyView = DefaultPlayerView(PlayerId.PLAYER_2, state)
        assertThat(enemyView.validTargets(UnitId("enemy"), HexDirection.N))
            .contains(UnitId("unconscious-attacker"))
    }

    @Test
    fun `a unit whose only weapon was critically destroyed is no longer a valid attacker`() {
        // Stage 4: a crit on a weapon-mount slot sets Weapon.destroyed = true; the query
        // layer (WeaponTargeting.canEngageAt) already filters !destroyed, so the attacker
        // should have no eligible weapon left and the enemy should not be a valid target.
        val attacker = aUnit(
            id = "attacker",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(0, 0),
            weapons = listOf(aWeapon(name = "Medium Laser", destroyed = true)),
        )
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(0, 1),
        )
        val state = aGameState(
            units = listOf(attacker, enemy),
            hexes = mapWithRadius(HexCoordinates(0, 0), radius = 3).hexes,
        )
        val view = DefaultPlayerView(PlayerId.PLAYER_1, state)

        assertThat(view.validTargets(UnitId("attacker"), HexDirection.N)).isEmpty()
        assertThat(view.targetInfos(UnitId("attacker"), HexDirection.N)).isEmpty()
    }

    @Test
    fun `a unit with 2 sensor crits is blind and has no valid targets`() {
        // HEAD framework: Sensors at indices 1 and 4 (docs/rules/armor-damage.md §3).
        val attacker = aUnit(
            id = "attacker",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(0, 0),
        ).copy(criticalHits = mapOf(battletech.tactical.model.MechLocation.HEAD to setOf(1, 4)))
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(0, 1),
        )
        val state = aGameState(
            units = listOf(attacker, enemy),
            hexes = mapWithRadius(HexCoordinates(0, 0), radius = 3).hexes,
        )
        val view = DefaultPlayerView(PlayerId.PLAYER_1, state)

        assertThat(view.validTargets(UnitId("attacker"), HexDirection.N)).isEmpty()
        assertThat(view.targetInfos(UnitId("attacker"), HexDirection.N)).isEmpty()
    }

    @Test
    fun `a unit with only 1 sensor crit can still fire`() {
        val attacker = aUnit(
            id = "attacker",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(0, 0),
        ).copy(criticalHits = mapOf(battletech.tactical.model.MechLocation.HEAD to setOf(1)))
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(0, -1), // due north of the attacker, within the N firing arc
        )
        val state = aGameState(
            units = listOf(attacker, enemy),
            hexes = mapWithRadius(HexCoordinates(0, 0), radius = 3).hexes,
        )
        val view = DefaultPlayerView(PlayerId.PLAYER_1, state)

        assertThat(view.validTargets(UnitId("attacker"), HexDirection.N)).containsExactly(UnitId("enemy"))
        val info = view.targetInfos(UnitId("attacker"), HexDirection.N).single()
        assertThat(info.weapons.single().modifiers).contains("+2 sensors")
    }

    @Test
    fun `validTargets excludes enemy in arc and range when line of sight is blocked by woods`() {
        // Attacker at (0,0) facing N; enemy at (0,-3) — 3 hexes north, within weapon long range.
        // Intervening: (0,-1) HEAVY_WOODS (2 levels) + (0,-2) LIGHT_WOODS (1 level) = 3 levels >= threshold -> blocked.
        val attacker = aUnit(id = "attacker", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, -3))
        val hexes = mapOf(
            HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0), Terrain.CLEAR),
            HexCoordinates(0, -1) to Hex(HexCoordinates(0, -1), Terrain.HEAVY_WOODS),
            HexCoordinates(0, -2) to Hex(HexCoordinates(0, -2), Terrain.LIGHT_WOODS),
            HexCoordinates(0, -3) to Hex(HexCoordinates(0, -3), Terrain.CLEAR),
        )
        val state = aGameState(units = listOf(attacker, enemy), hexes = hexes)
        val view = DefaultPlayerView(PlayerId.PLAYER_1, state)

        assertThat(view.validTargets(UnitId("attacker"), HexDirection.N)).isEmpty()
        assertThat(view.targetInfos(UnitId("attacker"), HexDirection.N)).isEmpty()
    }

    @Test
    fun `validTargets includes enemy in arc and range when line of sight is clear`() {
        // Same layout as above but all clear terrain — LOS unobstructed, enemy is valid.
        val attacker = aUnit(id = "attacker", owner = PlayerId.PLAYER_1, position = HexCoordinates(0, 0))
        val enemy = aUnit(id = "enemy", owner = PlayerId.PLAYER_2, position = HexCoordinates(0, -3))
        val hexes = mapOf(
            HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0), Terrain.CLEAR),
            HexCoordinates(0, -1) to Hex(HexCoordinates(0, -1), Terrain.CLEAR),
            HexCoordinates(0, -2) to Hex(HexCoordinates(0, -2), Terrain.CLEAR),
            HexCoordinates(0, -3) to Hex(HexCoordinates(0, -3), Terrain.CLEAR),
        )
        val state = aGameState(units = listOf(attacker, enemy), hexes = hexes)
        val view = DefaultPlayerView(PlayerId.PLAYER_1, state)

        assertThat(view.validTargets(UnitId("attacker"), HexDirection.N)).containsExactly(UnitId("enemy"))
        assertThat(view.targetInfos(UnitId("attacker"), HexDirection.N)).hasSize(1)
    }

    @Test
    fun `targetInfos modifier list starts with gunnery base and sums to target number`() {
        // Normal short-range engagement: attacker gunnery=4 (default), no special conditions.
        // TN = 4 + Σ(modifiers) and will be well above 2, so coerceAtLeast does not apply.
        val attacker = aUnit(
            id = "attacker",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(0, 0),
        )
        val enemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(0, -1), // due north, distance 1
        )
        val state = aGameState(
            units = listOf(attacker, enemy),
            hexes = mapWithRadius(HexCoordinates(0, 0), radius = 3).hexes,
        )
        val view = DefaultPlayerView(PlayerId.PLAYER_1, state)

        val weaponInfo = view.targetInfos(UnitId("attacker"), HexDirection.N).single().weapons.single()
        val modifiers = weaponInfo.modifiers

        // First line must be the gunnery base.
        assertThat(modifiers.first()).isEqualTo("+${attacker.gunnerySkill} gunnery")

        // The column must sum exactly to the target number (no clamping in this scenario).
        val columnSum = modifiers.sumOf { it.substringBefore(' ').toInt() }
        assertThat(columnSum).isEqualTo(weaponInfo.targetDiceRoll)
    }

    @Test
    fun `targetInfos applies the prone modifier so the preview agrees with resolution`() {
        // Regression test: targetInfos previously omitted the prone modifier, so the
        // previewed target number disagreed with the resolver's (which already applies
        // proneTargetToHitModifier via weaponToHitModifiers). Both must now agree.
        val attacker = aUnit(
            id = "attacker",
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(0, 0),
        )
        val proneEnemy = aUnit(
            id = "enemy",
            owner = PlayerId.PLAYER_2,
            position = HexCoordinates(0, -1), // due north of the attacker, within the N firing arc
        ).copy(isProne = true)
        val state = aGameState(
            units = listOf(attacker, proneEnemy),
            hexes = mapWithRadius(HexCoordinates(0, 0), radius = 3).hexes,
        )
        val view = DefaultPlayerView(PlayerId.PLAYER_1, state)

        val info = view.targetInfos(UnitId("attacker"), HexDirection.N).single()
        val weaponInfo = info.weapons.single()
        val weapon = attacker.weapons.single()
        val distance = attacker.position.distanceTo(proneEnemy.position)

        val expectedTargetNumber = (
            attacker.gunnerySkill +
                weaponToHitModifiers(attacker, proneEnemy, weapon, distance, isPrimaryTarget = true, gameState = state).total()
            ).coerceAtLeast(2)

        assertThat(weaponInfo.targetDiceRoll).isEqualTo(expectedTargetNumber)
        assertThat(weaponInfo.modifiers).anyMatch { it.endsWith("prone") }
    }

    @Test
    fun `legalTorsoFacings returns leg facing and both adjacent hexsides`() {
        val unit = aUnit(id = "u1", facing = HexDirection.N)
        val view = viewFor(unit)

        assertThat(view.legalTorsoFacings(UnitId("u1")))
            .containsExactlyInAnyOrder(HexDirection.N, HexDirection.NE, HexDirection.NW)
    }

    @Test
    fun `legalTorsoFacings returns empty set for an unknown unit`() {
        val view = viewFor(aUnit(id = "u1"))

        assertThat(view.legalTorsoFacings(UnitId("unknown"))).isEmpty()
    }
}
