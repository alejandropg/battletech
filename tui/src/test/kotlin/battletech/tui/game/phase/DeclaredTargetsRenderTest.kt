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
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.UnitRoster
import battletech.tui.aGameMap
import battletech.tui.aUnit
import battletech.tui.anAppState
import battletech.tui.game.AppState
import battletech.tui.mediumLaser
import battletech.tui.srm6
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class DeclaredTargetsRenderTest {

    private val map = aGameMap(cols = 7, rows = 7)

    /**
     * An [AppState] whose [AppState.viewer] is [viewer], composed the way host/join does it — one
     * seat. [buildDeclaredTargetsRender] reads through [AppState]'s viewer-scoped path and takes
     * no player argument, so pinning the seat IS how a test picks the perspective under test.
     */
    private fun anApp(gameState: GameState, turnState: TurnState, viewer: PlayerId): AppState =
        anAppState(MovementPhase.SelectingUnit, gameState = gameState, turnState = turnState)
            .let { it.copy(seats = mapOf(viewer to it.anySession)) }

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
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1))
        val gameState = GameState(UnitRoster(listOf(attacker, target)), map)

        val decl = AttackDeclaration(
            attackerId = attacker.id, targetId = target.id,
            weaponIndex = 0, isPrimary = true,
        )
        val turnState = turnState(attackDeclarations = listOf(decl))

        val result = buildDeclaredTargetsRender(
            anApp(gameState, turnState, PlayerId.PLAYER_1), emptyMap(),
        )

        assertEquals(1, result.entries.size)
        val entry = result.entries[0]
        assertEquals("wolf", entry.attackerId.value)
        assertEquals(PlayerId.PLAYER_1, entry.ownerPlayer)
        assertFalse(entry.isDraft)
        assertEquals(1, entry.targets.size)
        assertTrue(entry.targets[0].isPrimary)
        assertEquals("atlas", entry.targets[0].targetId.value)
        assertEquals(1, entry.targets[0].weapons.size)
        assertEquals("Medium Laser", entry.targets[0].weapons[0].weaponName)
    }

    @Test
    fun `committed P1 declaration is visible to P2 viewer too`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1))
        val gameState = GameState(UnitRoster(listOf(attacker, target)), map)

        val decl = AttackDeclaration(
            attackerId = attacker.id, targetId = target.id,
            weaponIndex = 0, isPrimary = true,
        )
        val turnState = turnState(attackDeclarations = listOf(decl))

        val result = buildDeclaredTargetsRender(
            anApp(gameState, turnState, PlayerId.PLAYER_2), emptyMap(),
        )

        assertEquals(1, result.entries.size)
        assertEquals("wolf", result.entries[0].attackerId.value)
        assertFalse(result.entries[0].isDraft)
    }

    @Test
    fun `draft with weapons produces isDraft=true entry`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1))
        val gameState = GameState(UnitRoster(listOf(attacker, target)), map)
        val turnState = turnState()

        val draft = UnitDeclaration(
            unitId = attacker.id,
            torsoFacing = HexDirection.N,
            primaryTargetId = target.id,
            weaponAssignments = mapOf(target.id to setOf(0)),
        )

        val result = buildDeclaredTargetsRender(
            anApp(gameState, turnState, PlayerId.PLAYER_1), mapOf(attacker.id to draft),
        )

        assertEquals(1, result.entries.size)
        assertTrue(result.entries[0].isDraft)
        assertEquals("wolf", result.entries[0].attackerId.value)
    }

    @Test
    fun `draft with no weapons is omitted`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val gameState = GameState(UnitRoster(listOf(attacker)), map)
        val turnState = turnState()

        val emptyDraft = UnitDeclaration(
            unitId = attacker.id,
            torsoFacing = HexDirection.N,
            weaponAssignments = emptyMap(),
        )

        val result = buildDeclaredTargetsRender(
            anApp(gameState, turnState, PlayerId.PLAYER_1), mapOf(attacker.id to emptyDraft),
        )

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `draft with empty weapon set is omitted`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1))
        val gameState = GameState(UnitRoster(listOf(attacker, target)), map)
        val turnState = turnState()

        val emptyDraft = UnitDeclaration(
            unitId = attacker.id,
            torsoFacing = HexDirection.N,
            weaponAssignments = mapOf(target.id to emptySet()),
        )

        val result = buildDeclaredTargetsRender(
            anApp(gameState, turnState, PlayerId.PLAYER_1), mapOf(attacker.id to emptyDraft),
        )

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `attackers ordered by attackSequence player order`() {
        val p1Unit = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val p2Unit = aUnit(id = "atlas", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(4, 3), facing = HexDirection.S, weapons = listOf(mediumLaser()))
        val p1Target = aUnit(id = "p1target", owner = PlayerId.PLAYER_2, name = "P1Target",
            position = HexCoordinates(2, 1))
        val p2Target = aUnit(id = "p2target", owner = PlayerId.PLAYER_1, name = "P2Target",
            position = HexCoordinates(4, 5))
        val gameState = GameState(UnitRoster(listOf(p1Unit, p2Unit, p1Target, p2Target)), map)

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
            anApp(gameState, turnState, PlayerId.PLAYER_1), emptyMap(),
        )

        assertEquals(2, result.entries.size)
        assertEquals("atlas", result.entries[0].attackerId.value)   // P2 first
        assertEquals("wolf", result.entries[1].attackerId.value) // P1 second
    }

    @Test
    fun `primaryTargetId resolves to isPrimary=true only on primary target`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser(), srm6()))
        val primary = aUnit(id = "primary", owner = PlayerId.PLAYER_2, name = "Primary",
            position = HexCoordinates(2, 1))
        val secondary = aUnit(id = "secondary", owner = PlayerId.PLAYER_2, name = "Secondary",
            position = HexCoordinates(2, 2))
        val gameState = GameState(UnitRoster(listOf(attacker, primary, secondary)), map)

        val decls = listOf(
            AttackDeclaration(attackerId = attacker.id, targetId = primary.id,
                weaponIndex = 0, isPrimary = true),
            AttackDeclaration(attackerId = attacker.id, targetId = secondary.id,
                weaponIndex = 1, isPrimary = false),
        )
        val turnState = turnState(attackDeclarations = decls)

        val result = buildDeclaredTargetsRender(
            anApp(gameState, turnState, PlayerId.PLAYER_1), emptyMap(),
        )

        assertEquals(1, result.entries.size)
        val targets = result.entries[0].targets
        val primaryEntry = targets.first { it.targetId.value == "primary" }
        val secondaryEntry = targets.first { it.targetId.value == "secondary" }
        assertTrue(primaryEntry.isPrimary)
        assertFalse(secondaryEntry.isPrimary)
    }

    @Test
    fun `empty attack sequence still renders committed entries`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1))
        val gameState = GameState(UnitRoster(listOf(attacker, target)), map)

        val decl = AttackDeclaration(
            attackerId = attacker.id, targetId = target.id,
            weaponIndex = 0, isPrimary = true,
        )
        val turnState = turnState(
            attackDeclarations = listOf(decl),
            attackSequence = ImpulseSequence(emptyList()),
        )

        val result = buildDeclaredTargetsRender(
            anApp(gameState, turnState, PlayerId.PLAYER_1), emptyMap(),
        )

        assertEquals(1, result.entries.size)
        assertEquals("wolf", result.entries[0].attackerId.value)
    }

    @Test
    fun `committed and draft for same player appear as separate entries`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser(), srm6()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1))
        val gameState = GameState(UnitRoster(listOf(attacker, target)), map)

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
            anApp(gameState, turnState, PlayerId.PLAYER_1), mapOf(attacker.id to draft),
        )

        assertEquals(2, result.entries.size)
        assertFalse(result.entries[0].isDraft)  // committed first
        assertTrue(result.entries[1].isDraft)   // draft second
    }

    @Test
    fun `draft for non-viewing player is excluded`() {
        val p2Attacker = aUnit(id = "atlas", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(4, 3), facing = HexDirection.S, weapons = listOf(mediumLaser()))
        val p2Target = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(4, 5))
        val gameState = GameState(UnitRoster(listOf(p2Attacker, p2Target)), map)
        val turnState = turnState()

        val p2Draft = UnitDeclaration(
            unitId = p2Attacker.id,
            torsoFacing = HexDirection.S,
            primaryTargetId = p2Target.id,
            weaponAssignments = mapOf(p2Target.id to setOf(0)),
        )

        // viewer = P1, so P2's draft should NOT appear
        val result = buildDeclaredTargetsRender(
            anApp(gameState, turnState, PlayerId.PLAYER_1), mapOf(p2Attacker.id to p2Draft),
        )

        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `declaredTargetsRender on Declaring includes live editing state`() {
        val attacker = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
            position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
        val target = aUnit(id = "atlas", owner = PlayerId.PLAYER_2,
            position = HexCoordinates(2, 1))
        val gameState = GameState(UnitRoster(listOf(attacker, target)), map)
        val turnState = turnState()

        val declaring = AttackPhase.Declaring(
            attackTurnPhase = TurnPhase.WEAPON_ATTACK,
            unitId = attacker.id,
            allocation = WeaponAllocation(
                torsoFacing = HexDirection.N,
                weaponAssignments = mapOf(target.id to setOf(0)),
                primaryTargetId = target.id,
            ),
            drafts = emptyMap(),
        )

        val result = declaring.declaredTargets(anApp(gameState, turnState, PlayerId.PLAYER_1), declaring.allDrafts())

        assertEquals(1, result.entries.size)
        assertTrue(result.entries[0].isDraft)
    }

    /**
     * Regression: host/join play. [AppState.seats] holds ONE seat there, and the panel used to be
     * scoped to the globally active attacker — routinely the OPPONENT, whom this process holds no
     * seat (and so no projection) for, so every render during the opponent's attack impulse threw
     * `NoSuchElementException: Key PLAYER_x is missing in the map` out of [AppState.stateFor].
     * Reproduced live as a `server` + two `join` clients: P1's client died the moment the session
     * cascaded into WEAPON_ATTACK with P2 attacking first. The panel is now scoped to
     * [AppState.viewer] — the seat this process actually drives.
     */
    @Nested
    inner class SingleSeatClient {

        /**
         * Unlike [anApp], this pins the fixture's session at the weapon-attack phase, which is
         * what production guarantees whenever an [AttackPhase] is the TUI phase (`AppState.phase`
         * is derived from `session.currentPhase`). [AppState.viewer] reads
         * [battletech.tactical.session.GameSession.activePlayer], and that is phase-dependent —
         * a movement-phase session would answer for the wrong impulse sequence.
         */
        private fun anAttackApp(gameState: GameState, turnState: TurnState): AppState =
            anAppState(
                AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK),
                gameState = gameState,
                turnState = turnState,
            )

        private fun singleSeat(app: AppState, seat: PlayerId): AppState =
            app.copy(seats = mapOf(seat to app.anySession))

        private fun p2AttacksP1(): Pair<GameState, TurnState> {
            val p1Unit = aUnit(id = "wolf", owner = PlayerId.PLAYER_1, name = "Wolverine",
                position = HexCoordinates(2, 3), facing = HexDirection.N, weapons = listOf(mediumLaser()))
            val p2Unit = aUnit(id = "atlas", owner = PlayerId.PLAYER_2, name = "Atlas",
                position = HexCoordinates(2, 1), facing = HexDirection.S, weapons = listOf(mediumLaser()))
            val gameState = GameState(UnitRoster(listOf(p1Unit, p2Unit)), map)
            val decl = AttackDeclaration(
                attackerId = p2Unit.id, targetId = p1Unit.id,
                weaponIndex = 0, isPrimary = true,
            )
            return gameState to turnState(
                attackDeclarations = listOf(decl),
                attackSequence = attackSequenceP2First(),
            )
        }

        @Test
        fun `P1-only client renders the opponent's impulse instead of throwing`() {
            val (gameState, turnState) = p2AttacksP1()
            val app = singleSeat(anAttackApp(gameState, turnState), PlayerId.PLAYER_1)
            // The active attacker is PLAYER_2 — the seat this client does NOT hold.
            assertEquals(PlayerId.PLAYER_2, turnState.attack.activePlayer)

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
                .declaredTargets(app, emptyMap())

            // P2's committed attack is still shown: it comes from the server-authoritative
            // projection, which P1 can read for itself.
            assertEquals(1, result.entries.size)
            assertEquals("atlas", result.entries[0].attackerId.value)
            assertFalse(result.entries[0].isDraft)
        }

        @Test
        fun `P2-only client renders a complete sequence instead of falling back to PLAYER_1`() {
            val (gameState, _) = p2AttacksP1()
            // Sequence run out: the old code defaulted to PLAYER_1, a seat this joiner lacks.
            val completed = turnState(
                attackSequence = ImpulseSequence(attackSequenceP2First().order, currentIndex = 2),
            )
            val app = singleSeat(anAttackApp(gameState, completed), PlayerId.PLAYER_2)
            assertTrue(completed.attack.isComplete)

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
                .declaredTargets(app, emptyMap())

            assertTrue(result.entries.isEmpty())
        }

        @Test
        fun `hot-seat still scopes to the active attacker`() {
            val (gameState, turnState) = p2AttacksP1()
            val app = anAttackApp(gameState, turnState) // both seats
            val p2Draft = UnitDeclaration(
                unitId = UnitId("atlas"),
                torsoFacing = HexDirection.S,
                primaryTargetId = UnitId("wolf"),
                weaponAssignments = mapOf(UnitId("wolf") to setOf(0)),
            )

            val result = AttackPhase.SelectingAttacker(TurnPhase.WEAPON_ATTACK)
                .declaredTargets(app, mapOf(p2Draft.unitId to p2Draft))

            // PLAYER_2 is both the active attacker and the viewer, so their draft folds in
            // alongside their committed entry — unchanged from before the single-seat fix.
            assertEquals(PlayerId.PLAYER_2, app.viewer)
            assertTrue(result.entries.any { it.isDraft })
        }
    }
}
