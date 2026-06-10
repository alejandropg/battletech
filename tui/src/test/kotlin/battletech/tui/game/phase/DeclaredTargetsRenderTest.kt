package battletech.tui.game.phase

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.Impulse
import battletech.tactical.session.ImpulseSequence
import battletech.tactical.session.Initiative
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitDeclaration
import battletech.tactical.unit.Weapon
import battletech.tui.aGameMap
import battletech.tui.aUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DeclaredTargetsRenderTest {

    private val map = aGameMap(cols = 7, rows = 7)

    private fun mediumLaser() = Weapon(
        name = "Medium Laser", damage = 5, heat = 3,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    private fun srm6() = Weapon(
        name = "SRM 6", damage = 12, heat = 4,
        shortRange = 3, mediumRange = 6, longRange = 9,
    )

    private fun initiative() = Initiative(
        rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
        loser = PlayerId.PLAYER_1,
        winner = PlayerId.PLAYER_2,
    )

    private fun attackSequenceP1First() = ImpulseSequence(
        listOf(Impulse(PlayerId.PLAYER_1, 2), Impulse(PlayerId.PLAYER_2, 2)),
    )

    private fun attackSequenceP2First() = ImpulseSequence(
        listOf(Impulse(PlayerId.PLAYER_2, 2), Impulse(PlayerId.PLAYER_1, 2)),
    )

    private fun turnState(
        attackDeclarations: List<AttackDeclaration> = emptyList(),
        attackSequence: ImpulseSequence = attackSequenceP1First(),
    ) = TurnState(
        initiative = initiative(),
        attack = battletech.tactical.session.AttackProgress(
            sequence = attackSequence,
            weaponDeclarations = attackDeclarations,
        ),
    )

    @Test
    fun `committed P1 declaration produces one non-draft entry`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(attacker, target), map)

        val decl = AttackDeclaration(
            attackerId = attacker.id, targetId = target.id,
            weaponIndex = 0, isPrimary = true,
        )
        val turnState = turnState(attackDeclarations = listOf(decl))

        val result = buildDeclaredTargetsRender(
            gameState, turnState, PlayerId.PLAYER_1, emptyMap(),
        )

        assertEquals(1, result.entries.size)
        val entry = result.entries[0]
        assertEquals("Wolverine", entry.attackerName)
        assertEquals(PlayerId.PLAYER_1, entry.ownerPlayer)
        assertFalse(entry.isDraft)
        assertEquals(1, entry.targets.size)
        assertTrue(entry.targets[0].isPrimary)
        assertEquals("Atlas", entry.targets[0].targetName)
        assertEquals(1, entry.targets[0].weapons.size)
        assertEquals("Medium Laser", entry.targets[0].weapons[0].weaponName)
    }

    @Test
    fun `draft with weapons produces isDraft=true entry`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(attacker, target), map)
        val turnState = turnState()

        val draft = UnitDeclaration(
            unitId = attacker.id,
            torsoFacing = HexDirection.N,
            primaryTargetId = target.id,
            weaponAssignments = mapOf(target.id to setOf(0)),
        )

        val result = buildDeclaredTargetsRender(
            gameState, turnState, PlayerId.PLAYER_1, mapOf(attacker.id to draft),
        )

        assertEquals(1, result.entries.size)
        assertTrue(result.entries[0].isDraft)
        assertEquals("Wolverine", result.entries[0].attackerName)
    }

    @Test
    fun `draft with no weapons is omitted`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val gameState = GameState(listOf(attacker), map)
        val turnState = turnState()

        val emptyDraft = UnitDeclaration(
            unitId = attacker.id,
            torsoFacing = HexDirection.N,
            primaryTargetId = null,
            weaponAssignments = emptyMap(),
        )

        val result = buildDeclaredTargetsRender(
            gameState, turnState, PlayerId.PLAYER_1, mapOf(attacker.id to emptyDraft),
        )

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `draft with empty weapon set is omitted`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(attacker, target), map)
        val turnState = turnState()

        val emptyDraft = UnitDeclaration(
            unitId = attacker.id,
            torsoFacing = HexDirection.N,
            primaryTargetId = null,
            weaponAssignments = mapOf(target.id to emptySet()),
        )

        val result = buildDeclaredTargetsRender(
            gameState, turnState, PlayerId.PLAYER_1, mapOf(attacker.id to emptyDraft),
        )

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `attackers ordered by attackSequence player order`() {
        val p1Unit = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val p2Unit = aUnit(id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(4, 3), facing = HexDirection.S, weapons = listOf(mediumLaser()))
        val p1Target = aUnit(id = "p1target", owner = PlayerId.PLAYER_2, name = "P1Target",
            position = HexCoordinates(2, 1))
        val p2Target = aUnit(id = "p2target", owner = PlayerId.PLAYER_1, name = "P2Target",
            position = HexCoordinates(4, 5))
        val gameState = GameState(listOf(p1Unit, p2Unit, p1Target, p2Target), map)

        val p1Decl = AttackDeclaration(
            attackerId = p1Unit.id, targetId = p1Target.id,
            weaponIndex = 0, isPrimary = true,
        )
        val p2Decl = AttackDeclaration(
            attackerId = p2Unit.id, targetId = p2Target.id,
            weaponIndex = 0, isPrimary = true,
        )

        // Sequence: P2 first, then P1
        val turnState = turnState(
            attackDeclarations = listOf(p1Decl, p2Decl),
            attackSequence = attackSequenceP2First(),
        )

        val result = buildDeclaredTargetsRender(
            gameState, turnState, PlayerId.PLAYER_1, emptyMap(),
        )

        assertEquals(2, result.entries.size)
        assertEquals("Atlas", result.entries[0].attackerName)   // P2 first
        assertEquals("Wolverine", result.entries[1].attackerName) // P1 second
    }

    @Test
    fun `primaryTargetId resolves to isPrimary=true only on primary target`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser(), srm6()))
        val primary = aUnit(id = "primary", owner = PlayerId.PLAYER_2, name = "Primary",
            position = HexCoordinates(2, 1))
        val secondary = aUnit(id = "secondary", owner = PlayerId.PLAYER_2, name = "Secondary",
            position = HexCoordinates(2, 2))
        val gameState = GameState(listOf(attacker, primary, secondary), map)

        val decls = listOf(
            AttackDeclaration(attackerId = attacker.id, targetId = primary.id,
                weaponIndex = 0, isPrimary = true),
            AttackDeclaration(attackerId = attacker.id, targetId = secondary.id,
                weaponIndex = 1, isPrimary = false),
        )
        val turnState = turnState(attackDeclarations = decls)

        val result = buildDeclaredTargetsRender(
            gameState, turnState, PlayerId.PLAYER_1, emptyMap(),
        )

        assertEquals(1, result.entries.size)
        val targets = result.entries[0].targets
        val primaryEntry = targets.first { it.targetName == "Primary" }
        val secondaryEntry = targets.first { it.targetName == "Secondary" }
        assertTrue(primaryEntry.isPrimary)
        assertFalse(secondaryEntry.isPrimary)
    }

    @Test
    fun `empty attack sequence falls back to P1 then P2 without crash`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(attacker, target), map)

        val decl = AttackDeclaration(
            attackerId = attacker.id, targetId = target.id,
            weaponIndex = 0, isPrimary = true,
        )
        val turnState = turnState(
            attackDeclarations = listOf(decl),
            attackSequence = ImpulseSequence(emptyList()),
        )

        val result = buildDeclaredTargetsRender(
            gameState, turnState, PlayerId.PLAYER_1, emptyMap(),
        )

        assertEquals(1, result.entries.size)
        assertEquals("Wolverine", result.entries[0].attackerName)
    }

    @Test
    fun `committed and draft for same player appear as separate entries`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser(), srm6()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(attacker, target), map)

        // Weapon 0 already committed
        val committed = AttackDeclaration(
            attackerId = attacker.id, targetId = target.id,
            weaponIndex = 0, isPrimary = true,
        )
        val turnState = turnState(attackDeclarations = listOf(committed))

        // Weapon 1 in draft (same unit)
        val draft = UnitDeclaration(
            unitId = attacker.id,
            torsoFacing = HexDirection.N,
            primaryTargetId = target.id,
            weaponAssignments = mapOf(target.id to setOf(1)),
        )

        val result = buildDeclaredTargetsRender(
            gameState, turnState, PlayerId.PLAYER_1, mapOf(attacker.id to draft),
        )

        assertEquals(2, result.entries.size)
        assertFalse(result.entries[0].isDraft)  // committed first
        assertTrue(result.entries[1].isDraft)   // draft second
    }

    @Test
    fun `draft for non-viewing player is excluded`() {
        val p2Attacker = aUnit(id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(4, 3), facing = HexDirection.S, weapons = listOf(mediumLaser()))
        val p2Target = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(4, 5))
        val gameState = GameState(listOf(p2Attacker, p2Target), map)
        val turnState = turnState()

        val p2Draft = UnitDeclaration(
            unitId = p2Attacker.id,
            torsoFacing = HexDirection.S,
            primaryTargetId = p2Target.id,
            weaponAssignments = mapOf(p2Target.id to setOf(0)),
        )

        // viewingPlayer = P1, so P2's draft should NOT appear
        val result = buildDeclaredTargetsRender(
            gameState, turnState, PlayerId.PLAYER_1, mapOf(p2Attacker.id to p2Draft),
        )

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `declaredTargetsRender on Declaring includes live editing state`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
            position = HexCoordinates(2, 1))
        val gameState = GameState(listOf(attacker, target), map)
        val turnState = turnState()

        val declaring = AttackPhase.Declaring(
            attackTurnPhase = TurnPhase.WEAPON_ATTACK,
            unitId = attacker.id,
            allocation = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(target.id to setOf(0)),
                primaryTargetId = target.id,
                cursorTargetIndex = 0,
                cursorWeaponIndex = 0,
            ),
            drafts = emptyMap(),
        )

        val result = declaring.declaredTargetsRender(gameState, turnState, PlayerId.PLAYER_1)

        assertEquals(1, result.entries.size)
        assertTrue(result.entries[0].isDraft)
    }
}
