package battletech.tui.view

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.LocationDamage
import battletech.tactical.attack.LocationHit
import battletech.tactical.attack.RangeBand
import battletech.tactical.attack.physical.AttackDirection
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.MechLocation
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.PlayerGameState
import battletech.tactical.query.projectFor
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.GameEvent
import battletech.tactical.session.HeatDissipated
import battletech.tactical.session.Initiative
import battletech.tactical.session.InitiativeRolled
import battletech.tactical.session.MatchEnded
import battletech.tactical.session.PhaseChanged
import battletech.tactical.session.PhysicalAttacksResolved
import battletech.tactical.session.PilotHit
import battletech.tactical.session.PilotKnockedUnconscious
import battletech.tactical.session.PilotRecoveredConsciousness
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnEnded
import battletech.tactical.session.UnitDestroyed
import battletech.tactical.session.UnitMoved
import battletech.tactical.unit.ActuatorType
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.DestructionReason
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.WeaponModels
import battletech.tactical.unit.WeaponMountId
import battletech.tui.aUnit
import battletech.tui.anArmorLayout
import battletech.tui.hex.attackOutcomeIcon
import battletech.tui.hex.criticalHitIcon
import battletech.tui.hex.diceIcon
import battletech.tui.hex.locationDestroyedIcon
import battletech.tui.hex.movementModeIcon
import battletech.tui.hex.sessionNoticeIcon
import battletech.tui.hex.targetIcon
import battletech.tui.hex.torsoArrowIcon
import battletech.tui.mediumLaser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GameLogFormatterTest {

    // Raw GameState fixture the per-test `stateWithXxx` derivatives build on via `.copy(units = ...)`;
    // [emptyState] itself is the PlayerGameState every call into GameLogFormatter actually takes —
    // `viewer = null, revealAll = true` reveals everything, which is what this formatter-output test
    // wants (it is not testing redaction; that's Stage 3).
    private val emptyGameState = GameState(
        units = emptyList(),
        map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)))),
    )
    private val emptyState = emptyGameState.projectFor(viewer = null, revealAll = true)

    private fun text(e: GameEvent, s: PlayerGameState = emptyState) = GameLogFormatter.lines(e, s).single().text
    private fun icon(e: GameEvent) = GameLogFormatter.lines(e, emptyState).single().icon

    @Test
    fun `PhaseChanged is not logged`() {
        assertThat(GameLogFormatter.lines(PhaseChanged(TurnPhase.INITIATIVE, TurnPhase.MOVEMENT), emptyState)).isEmpty()
    }

    @Test
    fun `UnitMoved shows the unit name, mode icon, hex coords, and MP spent`() {
        val atlas = aMech(id = "atlas", name = "Atlas", position = HexCoordinates(2, 0))
        val stateWithAtlas = emptyGameState.copy(units = listOf(atlas)).projectFor(viewer = null, revealAll = true)

        assertThat(text(
            UnitMoved(
                unitId = atlas.id,
                from = HexCoordinates(2, 0),
                to = HexCoordinates(2, 1),
                finalFacing = HexDirection.N,
                mode = MovementMode.WALK,
                mpSpent = 3,
            ),
            stateWithAtlas,
        )).isEqualTo("atlas (3 MP) 0301→0302")
    }

    @Test
    fun `UnitMoved uses the run icon for RUN and jump icon for JUMP`() {
        val atlas = aMech(id = "atlas", name = "Atlas", position = HexCoordinates(0, 0))
        val stateWithAtlas = emptyGameState.copy(units = listOf(atlas)).projectFor(viewer = null, revealAll = true)

        val ran = text(
            UnitMoved(atlas.id, HexCoordinates(0, 0), HexCoordinates(0, 1), HexDirection.N, MovementMode.RUN, 5),
            stateWithAtlas,
        )
        val jumped = text(
            UnitMoved(atlas.id, HexCoordinates(0, 0), HexCoordinates(0, 1), HexDirection.N, MovementMode.JUMP, 4),
            stateWithAtlas,
        )

        assertThat(ran).isEqualTo("atlas (5 MP) 0101→0102")
        assertThat(jumped).isEqualTo("atlas (4 MP) 0101→0102")
    }

    @Test
    fun `InitiativeRolled shows both rolls with dice icons and the player who moves first`() {
        val initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(3, 3), PlayerId.PLAYER_2 to DiceRoll(1, 2)),
            loser = PlayerId.PLAYER_2,
            winner = PlayerId.PLAYER_1,
        )

        assertThat(text(InitiativeRolled(initiative))).isEqualTo(
            "Initiative: P1 ${diceIcon(3)}+${diceIcon(3)}=6, P2 ${diceIcon(1)}+${diceIcon(2)}=3 — P2 moves first"
        )
    }

    @Test
    fun `AttacksResolved summarizes fired, hit, and damage`() {
        val results = listOf(
            anAttackResult(hit = true, damage = 5, locationHits = listOf(LocationHit(HitLocation.CENTER_TORSO, 5, DiceRoll(3, 4)))),
            anAttackResult(hit = true, damage = 7, locationHits = listOf(LocationHit(HitLocation.LEFT_TORSO, 7, DiceRoll(4, 4)))),
            anAttackResult(hit = false, damage = 0),
        )

        assertThat(GameLogFormatter.lines(AttacksResolved(results), emptyState).first().text)
            .isEqualTo("Attacks: 3 fired, 2 hit, 12 damage")
    }

    @Test
    fun `AttacksResolved with no attacks reads gracefully`() {
        assertThat(text(AttacksResolved(emptyList()))).isEqualTo("Attacks: 0 fired, 0 hit, 0 damage")
    }

    @Test
    fun `AttacksResolved appends a destroyed-location clause when a location was blown off`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)
        val results = listOf(
            anAttackResult(
                hit = true,
                damage = 24,
                targetId = locust.id,
                locationDamage = listOf(
                    LocationDamage(MechLocation.LEFT_ARM, armorDamage = 20, structureDamage = 6, destroyed = true),
                    LocationDamage(MechLocation.LEFT_TORSO, armorDamage = 4, structureDamage = 0, destroyed = false),
                ),
                locationHits = listOf(LocationHit(HitLocation.LEFT_ARM, 24, DiceRoll(3, 4))),
            ),
        )

        assertThat(GameLogFormatter.lines(AttacksResolved(results), stateWithLocust).first().text)
            .isEqualTo("Attacks: 1 fired, 1 hit, 24 damage — locust Left Arm destroyed")
    }

    @Test
    fun `AttacksResolved omits the destroyed-location clause when nothing was destroyed`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)
        val results = listOf(
            anAttackResult(
                hit = true,
                damage = 5,
                targetId = locust.id,
                locationDamage = listOf(
                    LocationDamage(MechLocation.LEFT_ARM, armorDamage = 5, structureDamage = 0, destroyed = false),
                ),
                locationHits = listOf(LocationHit(HitLocation.LEFT_ARM, 5, DiceRoll(3, 4))),
            ),
        )

        assertThat(GameLogFormatter.lines(AttacksResolved(results), stateWithLocust).first().text)
            .isEqualTo("Attacks: 1 fired, 1 hit, 5 damage")
    }

    @Test
    fun `PhysicalAttacksResolved appends a destroyed-location clause when a location was blown off`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)
        val results = listOf(
            aPhysicalAttackResult(
                hit = true,
                damage = 18,
                targetId = locust.id,
                locationDamage = listOf(
                    LocationDamage(MechLocation.RIGHT_LEG, armorDamage = 12, structureDamage = 6, destroyed = true),
                ),
            ),
        )

        assertThat(GameLogFormatter.lines(PhysicalAttacksResolved(results), stateWithLocust).first().text)
            .isEqualTo("Physical attacks: 1 made, 1 hit, 18 damage — locust Right Leg destroyed")
    }

    @Test
    fun `PhysicalAttacksResolved omits the destroyed-location clause when nothing was destroyed`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)
        val results = listOf(
            aPhysicalAttackResult(
                hit = true,
                damage = 3,
                targetId = locust.id,
                locationDamage = listOf(
                    LocationDamage(MechLocation.RIGHT_LEG, armorDamage = 3, structureDamage = 0, destroyed = false),
                ),
            ),
        )

        assertThat(GameLogFormatter.lines(PhysicalAttacksResolved(results), stateWithLocust).first().text)
            .isEqualTo("Physical attacks: 1 made, 1 hit, 3 damage")
    }

    @Test
    fun `PhysicalAttacksResolved with a hit location adds a detail line`() {
        val result = aPhysicalAttackResult(hit = true, damage = 8, hitLocation = MechLocation.RIGHT_TORSO)
        val lines = GameLogFormatter.lines(PhysicalAttacksResolved(listOf(result)), emptyState)

        assertThat(lines).hasSize(2)
        assertThat(lines[0].text).isEqualTo("Physical attacks: 1 made, 1 hit, 8 damage")
        assertThat(lines[1].text).isEqualTo("Punch → Right Torso (8 dmg)")
        assertThat(lines[1].icon).isEqualTo(attackOutcomeIcon(hit = true))
    }

    @Test
    fun `AttackDeclarationsRecorded shows attacker, target, and fired weapons`() {
        val atlas = aMech(id = "atlas", name = "Atlas", owner = PlayerId.PLAYER_1)
        val locust = aMech(id = "locust", name = "Locust", owner = PlayerId.PLAYER_2)
        val stateWithUnits = emptyGameState.copy(units = listOf(atlas, locust)).projectFor(viewer = null, revealAll = true)

        val lines = GameLogFormatter.lines(
            event = AttackDeclarationsRecorded(
                player = PlayerId.PLAYER_1,
                declarations = listOf(
                    AttackDeclaration(
                        attackerId = atlas.id, targetId = locust.id, weaponIndex = 0, isPrimary = true,
                    ),
                ),
            ),
            state = stateWithUnits,
        )

        // atlas has one weapon configured: medium laser ("Medium Laser").
        assertThat(lines).isEqualTo(
            listOf(GameLogFormatter.LogLine(targetIcon(), "atlas → locust (Medium Laser)")),
        )
    }

    @Test
    fun `AttackDeclarationsRecorded groups multiple weapons fired at the same target`() {
        val atlas = aMech(
            id = "atlas",
            name = "Atlas",
            owner = PlayerId.PLAYER_1,
            weapons = listOf(Weapon(model = WeaponModels.mediumLaser), Weapon(model = WeaponModels.lrm5)),
        )
        val locust = aMech(id = "locust", name = "Locust", owner = PlayerId.PLAYER_2)
        val stateWithUnits = emptyGameState.copy(units = listOf(atlas, locust)).projectFor(viewer = null, revealAll = true)

        val lines = GameLogFormatter.lines(
            event = AttackDeclarationsRecorded(
                player = PlayerId.PLAYER_1,
                declarations = listOf(
                    AttackDeclaration(atlas.id, locust.id, 0, true),
                    AttackDeclaration(atlas.id, locust.id, 1, false),
                ),
            ),
            state = stateWithUnits,
        )

        assertThat(lines).isEqualTo(
            listOf(GameLogFormatter.LogLine(targetIcon(), "atlas → locust (Medium Laser, LRM 5)")),
        )
    }

    @Test
    fun `AttackDeclarationsRecorded puts one attacker's multiple targets on a single line`() {
        val atlas = aMech(id = "atlas", name = "Atlas", owner = PlayerId.PLAYER_1)
        val locust = aMech(id = "locust", name = "Locust", owner = PlayerId.PLAYER_2)
        val marauder = aMech(id = "marauder", name = "Marauder", owner = PlayerId.PLAYER_2)
        val stateWithUnits = emptyGameState.copy(units = listOf(atlas, locust, marauder)).projectFor(viewer = null, revealAll = true)

        val lines = GameLogFormatter.lines(
            event = AttackDeclarationsRecorded(
                player = PlayerId.PLAYER_1,
                declarations = listOf(
                    AttackDeclaration(atlas.id, locust.id, 0, true),
                    AttackDeclaration(atlas.id, marauder.id, 0, false),
                ),
            ),
            state = stateWithUnits,
        )

        assertThat(lines).isEqualTo(
            listOf(GameLogFormatter.LogLine(targetIcon(), "atlas → locust (Medium Laser), marauder (Medium Laser)")),
        )
    }

    @Test
    fun `AttackDeclarationsRecorded emits one line per attacker`() {
        val atlas = aMech(id = "atlas", name = "Atlas", owner = PlayerId.PLAYER_1)
        val locust = aMech(id = "locust", name = "Locust", owner = PlayerId.PLAYER_2)
        val marauder = aMech(id = "marauder", name = "Marauder", owner = PlayerId.PLAYER_1)
        val phoenixHawk = aMech(id = "phoenixhawk", name = "Phoenix Hawk", owner = PlayerId.PLAYER_2)
        val stateWithUnits = emptyGameState.copy(units = listOf(atlas, locust, marauder, phoenixHawk)).projectFor(viewer = null, revealAll = true)

        val lines = GameLogFormatter.lines(
            event = AttackDeclarationsRecorded(
                player = PlayerId.PLAYER_1,
                declarations = listOf(
                    AttackDeclaration(atlas.id, locust.id, 0, true),
                    AttackDeclaration(marauder.id, phoenixHawk.id, 0, true),
                ),
            ),
            state = stateWithUnits,
        )

        assertThat(lines).containsExactlyInAnyOrder(
            GameLogFormatter.LogLine(targetIcon(), "atlas → locust (Medium Laser)"),
            GameLogFormatter.LogLine(targetIcon(), "marauder → phoenixhawk (Medium Laser)"),
        )
        assertThat(lines).hasSize(2)
    }

    @Test
    fun `TorsoFacingsApplied lists each unit and its torso facing`() {
        val atlas = aMech(id = "atlas", name = "Atlas")
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithUnits = emptyGameState.copy(units = listOf(atlas, locust)).projectFor(viewer = null, revealAll = true)

        val lines = GameLogFormatter.lines(
            event = TorsoFacingsApplied(
                facings = mapOf(
                    atlas.id to HexDirection.NE,
                    locust.id to HexDirection.S,
                ),
            ),
            state = stateWithUnits,
        )

        assertThat(lines).containsExactly(
            GameLogFormatter.LogLine(torsoArrowIcon(HexDirection.NE).first, "atlas torso → NE"),
            GameLogFormatter.LogLine(torsoArrowIcon(HexDirection.S).first, "locust torso → S"),
        )
    }

    @Test
    fun `TorsoFacingsApplied with no facings says 'no changes'`() {
        val lines = GameLogFormatter.lines(
            event = TorsoFacingsApplied(facings = emptyMap()),
            state = emptyState,
        )

        assertThat(lines).containsExactly(GameLogFormatter.LogLine(null, "Torso facings: no changes"))
    }

    @Test
    fun `HeatDissipated shows before-arrow-after per unit that had heat`() {
        val atlas = aMech(id = "atlas", name = "Atlas")
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithUnits = emptyGameState.copy(units = listOf(atlas, locust)).projectFor(viewer = null, revealAll = true)

        assertThat(text(
            HeatDissipated(
                heatBefore = mapOf(atlas.id to 8, locust.id to 0),
                heatAfter = mapOf(atlas.id to 4, locust.id to 0),
            ),
            stateWithUnits,
        )).isEqualTo("Heat: atlas 8→4")
    }

    @Test
    fun `HeatDissipated with no heat reports 'no heat'`() {
        val atlas = aMech(id = "atlas", name = "Atlas")
        val stateWithAtlas = emptyGameState.copy(units = listOf(atlas)).projectFor(viewer = null, revealAll = true)

        assertThat(text(
            HeatDissipated(
                heatBefore = mapOf(atlas.id to 0),
                heatAfter = mapOf(atlas.id to 0),
            ),
            stateWithAtlas,
        )).isEqualTo("Heat: no heat to dissipate")
    }

    @Test
    fun `TurnEnded is not logged`() {
        assertThat(GameLogFormatter.lines(TurnEnded(turnNumber = 3), emptyState)).isEmpty()
    }

    @Test
    fun `UnitDestroyed shows unit name and a readable destruction reason`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)

        assertThat(text(
            UnitDestroyed(unitId = locust.id, reason = DestructionReason.CENTER_TORSO_DESTROYED),
            stateWithLocust,
        )).isEqualTo("locust destroyed (center torso destroyed)")
    }

    @Test
    fun `UnitDestroyed renders every destruction reason readably`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)

        val expected = mapOf(
            DestructionReason.HEAD_DESTROYED to "head destroyed",
            DestructionReason.CENTER_TORSO_DESTROYED to "center torso destroyed",
            DestructionReason.BOTH_LEGS_DESTROYED to "both legs destroyed",
            DestructionReason.ENGINE_DESTROYED to "engine destroyed",
            DestructionReason.PILOT_DEAD to "pilot dead",
        )

        for ((reason, label) in expected) {
            assertThat(text(UnitDestroyed(unitId = locust.id, reason = reason), stateWithLocust))
                .isEqualTo("locust destroyed ($label)")
        }
    }

    @Test
    fun `MatchEnded reports the winner`() {
        assertThat(text(MatchEnded(MatchOutcome.Victory(PlayerId.PLAYER_1)))).isEqualTo("Match over — P1 wins!")
    }

    @Test
    fun `MatchEnded reports a draw`() {
        assertThat(text(MatchEnded(MatchOutcome.Draw))).isEqualTo("Match over — draw")
    }

    @Test
    fun `CriticalHit on a named component shows unit, component, and location`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)

        assertThat(text(
            CriticalHit(
                unitId = locust.id,
                location = MechLocation.CENTER_TORSO,
                slotIndex = 0,
                content = CriticalSlotContent.Engine,
            ),
            stateWithLocust,
        )).isEqualTo("locust critical hit: Engine in Center Torso")
    }

    @Test
    fun `CriticalHit on a weapon mount resolves the weapon's name`() {
        val weapon = Weapon(model = WeaponModels.mediumLaser, mountId = WeaponMountId(0))
        val locust = aMech(id = "locust", name = "Locust", weapons = listOf(weapon))
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)

        assertThat(text(
            CriticalHit(
                unitId = locust.id,
                location = MechLocation.RIGHT_ARM,
                slotIndex = 5,
                content = CriticalSlotContent.WeaponMount(WeaponMountId(0)),
            ),
            stateWithLocust,
        )).isEqualTo("locust critical hit: Medium Laser in Right Arm")
    }

    @Test
    fun `CriticalHit on an ammo bin and an actuator render readably`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)

        assertThat(text(
            CriticalHit(
                unitId = locust.id,
                location = MechLocation.LEFT_TORSO,
                slotIndex = 2,
                content = CriticalSlotContent.AmmoBin(battletech.tactical.unit.AmmoType.AC20, shots = 5),
            ),
            stateWithLocust,
        )).isEqualTo("locust critical hit: AC20 ammo in Left Torso")

        assertThat(text(
            CriticalHit(
                unitId = locust.id,
                location = MechLocation.LEFT_ARM,
                slotIndex = 1,
                content = CriticalSlotContent.Actuator(ActuatorType.UPPER_ARM),
            ),
            stateWithLocust,
        )).isEqualTo("locust critical hit: Upper arm actuator in Left Arm")
    }

    @Test
    fun `PilotHit shows the running pilot hit total`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)

        assertThat(text(
            PilotHit.Fatal(unitId = locust.id, pilotHits = 1),
            stateWithLocust,
        )).isEqualTo("locust pilot wounded (1 hit total)")
    }

    @Test
    fun `PilotHit pluralizes hits when more than one`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)

        assertThat(text(
            PilotHit.Checked(
                unitId = locust.id, pilotHits = 3,
                consciousnessRoll = DiceRoll(4, 4), conscious = true,
            ),
            stateWithLocust,
        )).isEqualTo("locust pilot wounded (3 hits total)")
    }

    @Test
    fun `PilotKnockedUnconscious and PilotRecoveredConsciousness render readably`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyGameState.copy(units = listOf(locust)).projectFor(viewer = null, revealAll = true)

        assertThat(text(PilotKnockedUnconscious(unitId = locust.id), stateWithLocust))
            .isEqualTo("locust pilot knocked unconscious")

        assertThat(text(PilotRecoveredConsciousness(unitId = locust.id, roll = DiceRoll(5, 5)), stateWithLocust))
            .isEqualTo("locust pilot regained consciousness")
    }

    @Test
    fun `SessionNotice renders its text with the lan-connect icon`() {
        val lines = GameLogFormatter.lines(SessionNotice("Opponent connected"), emptyState)

        assertThat(lines).containsExactly(GameLogFormatter.LogLine(sessionNoticeIcon(), "Opponent connected"))
    }

    @Test
    fun `iconFor uses the movement-mode glyph for a move`() {
        val moved = UnitMoved(
            unitId = UnitId("atlas"), from = HexCoordinates(0, 0), to = HexCoordinates(0, 1),
            finalFacing = HexDirection.N, mode = MovementMode.JUMP, mpSpent = 4,
        )

        assertThat(icon(moved)).isEqualTo(movementModeIcon(MovementMode.JUMP))
    }

    @Test
    fun `iconFor maps engine, gyro, sensor, and life-support crits to distinct glyphs`() {
        fun crit(content: CriticalSlotContent) = CriticalHit(
            unitId = UnitId("locust"), location = MechLocation.CENTER_TORSO, slotIndex = 0, content = content,
        )

        assertThat(icon(crit(CriticalSlotContent.Engine)))
            .isEqualTo(criticalHitIcon(CriticalSlotContent.Engine))
        assertThat(icon(crit(CriticalSlotContent.Gyro)))
            .isEqualTo(criticalHitIcon(CriticalSlotContent.Gyro))
        assertThat(icon(crit(CriticalSlotContent.Sensors)))
            .isEqualTo(criticalHitIcon(CriticalSlotContent.Sensors))
        assertThat(icon(crit(CriticalSlotContent.LifeSupport)))
            .isEqualTo(criticalHitIcon(CriticalSlotContent.LifeSupport))
    }

    @Test
    fun `iconFor marks a destroyed location and omits the icon otherwise`() {
        val destroyed = AttacksResolved(
            listOf(
                anAttackResult(
                    hit = true,
                    damage = 24,
                    locationDamage = listOf(
                        LocationDamage(MechLocation.LEFT_ARM, armorDamage = 20, structureDamage = 6, destroyed = true),
                    ),
                    locationHits = listOf(LocationHit(HitLocation.LEFT_ARM, 24, DiceRoll(3, 4))),
                ),
            ),
        )
        val intact = AttacksResolved(
            listOf(
                anAttackResult(
                    hit = true,
                    damage = 5,
                    locationHits = listOf(LocationHit(HitLocation.CENTER_TORSO, 5, DiceRoll(3, 4))),
                ),
            ),
        )

        assertThat(GameLogFormatter.lines(destroyed, emptyState).first().icon).isEqualTo(locationDestroyedIcon())
        assertThat(GameLogFormatter.lines(intact, emptyState).first().icon).isNull()
    }

    @Test
    fun `lines uses the torso-arrow glyph for every unit, even with several facing changes`() {
        val single = TorsoFacingsApplied(facings = mapOf(UnitId("atlas") to HexDirection.NE))
        val multiple = TorsoFacingsApplied(
            facings = mapOf(UnitId("atlas") to HexDirection.NE, UnitId("locust") to HexDirection.S),
        )

        assertThat(GameLogFormatter.lines(single, emptyState).map { it.icon })
            .containsExactly(torsoArrowIcon(HexDirection.NE).first)
        assertThat(GameLogFormatter.lines(multiple, emptyState).map { it.icon })
            .containsExactly(torsoArrowIcon(HexDirection.NE).first, torsoArrowIcon(HexDirection.S).first)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cluster-hit weapon rendering
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `AttacksResolved with cluster weapon hit adds a detail line`() {
        val result = aClusterAttackResult(
            weaponName = "LRM 20",
            missilesHit = 16,
            locationHits = listOf(
                LocationHit(HitLocation.CENTER_TORSO, 5, DiceRoll(3, 4)),
                LocationHit(HitLocation.RIGHT_TORSO,  5, DiceRoll(4, 4)),
                LocationHit(HitLocation.LEFT_ARM,     5, DiceRoll(5, 5)),
                LocationHit(HitLocation.RIGHT_ARM,    1, DiceRoll(1, 2)),
            ),
            totalDamage = 16,
        )
        val lines = GameLogFormatter.lines(AttacksResolved(listOf(result)), emptyState)

        assertThat(lines).hasSize(2)
        assertThat(lines[0].text).isEqualTo("Attacks: 1 fired, 1 hit, 16 damage")
        assertThat(lines[1].text)
            .isEqualTo("LRM 20: 16 missiles (16 dmg) → Center Torso (5 dmg), Right Torso (5 dmg), Left Arm (5 dmg), Right Arm (1 dmg)")
        assertThat(lines[1].icon).isEqualTo(attackOutcomeIcon(hit = true))
    }

    @Test
    fun `AttacksResolved with SRM cluster hit shows missile count and per-missile groups`() {
        val result = aClusterAttackResult(
            weaponName = "SRM 6",
            missilesHit = 4,
            locationHits = listOf(
                LocationHit(HitLocation.CENTER_TORSO, 2, DiceRoll(3, 4)),
                LocationHit(HitLocation.LEFT_TORSO,   2, DiceRoll(4, 4)),
                LocationHit(HitLocation.CENTER_TORSO, 2, DiceRoll(3, 4)),
                LocationHit(HitLocation.RIGHT_TORSO,  2, DiceRoll(4, 4)),
            ),
            totalDamage = 8,
        )
        val lines = GameLogFormatter.lines(AttacksResolved(listOf(result)), emptyState)

        assertThat(lines).hasSize(2)
        assertThat(lines[1].text)
            .isEqualTo("SRM 6: 4 missiles (8 dmg) → Center Torso (2 dmg), Left Torso (2 dmg), Center Torso (2 dmg), Right Torso (2 dmg)")
    }

    @Test
    fun `AttacksResolved with a single-shot hit adds a detail line`() {
        val result = anAttackResult(
            hit = true,
            damage = 5,
            locationHits = listOf(LocationHit(HitLocation.CENTER_TORSO, 5, DiceRoll(3, 4))),
        )
        val lines = GameLogFormatter.lines(AttacksResolved(listOf(result)), emptyState)

        assertThat(lines).hasSize(2)
        assertThat(lines[0].text).isEqualTo("Attacks: 1 fired, 1 hit, 5 damage")
        assertThat(lines[1].text).isEqualTo("ML → Center Torso (5 dmg)")
        assertThat(lines[1].icon).isEqualTo(attackOutcomeIcon(hit = true))
    }

    @Test
    fun `AttacksResolved with mix of cluster and non-cluster hits adds a detail line for both cluster and single-shot hits`() {
        val clusterResult = aClusterAttackResult(
            weaponName = "SRM 6",
            missilesHit = 2,
            locationHits = listOf(
                LocationHit(HitLocation.CENTER_TORSO, 2, DiceRoll(3, 4)),
                LocationHit(HitLocation.LEFT_TORSO,   2, DiceRoll(4, 4)),
            ),
            totalDamage = 4,
        )
        val plainResult = anAttackResult(
            hit = true,
            damage = 5,
            locationHits = listOf(LocationHit(HitLocation.RIGHT_ARM, 5, DiceRoll(3, 4))),
        )
        val lines = GameLogFormatter.lines(AttacksResolved(listOf(clusterResult, plainResult)), emptyState)

        // Summary line + 1 cluster detail line + 1 single-shot detail line
        assertThat(lines).hasSize(3)
        assertThat(lines[0].text).isEqualTo("Attacks: 2 fired, 2 hit, 9 damage")
        assertThat(lines[1].text).startsWith("SRM 6:")
        assertThat(lines[2].text).isEqualTo("ML → Right Arm (5 dmg)")
    }

    @Test
    fun `AttacksResolved cluster miss produces no detail line`() {
        // A cluster weapon that missed: missilesHit=null (not set), hit=false
        val missResult = anAttackResult(hit = false, damage = 0)
        val lines = GameLogFormatter.lines(AttacksResolved(listOf(missResult)), emptyState)
        assertThat(lines).hasSize(1) // only the summary line
    }

    private fun aClusterAttackResult(
        weaponName: String,
        missilesHit: Int,
        locationHits: List<LocationHit>,
        totalDamage: Int,
        targetId: UnitId = UnitId("t"),
    ): AttackResult =
        AttackResult.ClusterHit(
            attackerId = UnitId("a"),
            targetId = targetId,
            weaponName = weaponName,
            targetNumber = 5,
            toHitRoll = DiceRoll(4, 4),
            gunnery = 2,
            rangeBand = RangeBand.SHORT,
            locationHits = locationHits,
            missilesHit = missilesHit,
        )

    private fun anAttackResult(
        hit: Boolean,
        damage: Int,
        targetId: UnitId = UnitId("t"),
        locationDamage: List<LocationDamage> = emptyList(),
        locationHits: List<LocationHit> = emptyList(),
    ): AttackResult =
        if (hit) {
            AttackResult.SingleHit(
                attackerId = UnitId("a"),
                targetId = targetId,
                weaponName = "ML",
                targetNumber = 8,
                toHitRoll = DiceRoll(5, 5),
                gunnery = 4,
                rangeBand = RangeBand.SHORT,
                damage = locationDamage,
                locationHits = locationHits,
            )
        } else {
            AttackResult.Miss(
                attackerId = UnitId("a"),
                targetId = targetId,
                weaponName = "ML",
                targetNumber = 8,
                toHitRoll = DiceRoll(2, 3),
                gunnery = 4,
                rangeBand = RangeBand.SHORT,
            )
        }

    private fun aPhysicalAttackResult(
        hit: Boolean,
        damage: Int,
        targetId: UnitId = UnitId("t"),
        locationDamage: List<LocationDamage> = emptyList(),
        hitLocation: HitLocation = locationDamage.firstOrNull()?.location ?: HitLocation.CENTER_TORSO,
    ): PhysicalAttackResult =
        if (hit) {
            PhysicalAttackResult.Hit(
                attackerId = UnitId("a"),
                targetId = targetId,
                attackName = "Punch",
                hitLocation = hitLocation,
                locationRoll = 3,
                damageApplied = damage,
                targetNumber = 8,
                toHitRoll = DiceRoll(5, 5),
                attackDirection = AttackDirection.FRONT,
                damage = locationDamage,
            )
        } else {
            PhysicalAttackResult.Miss(
                attackerId = UnitId("a"),
                targetId = targetId,
                attackName = "Punch",
                targetNumber = 8,
                toHitRoll = DiceRoll(2, 3),
                attackDirection = AttackDirection.FRONT,
            )
        }

    private fun aMech(
        id: String,
        name: String = id,
        owner: PlayerId = PlayerId.PLAYER_1,
        position: HexCoordinates = HexCoordinates(0, 0),
        currentHeat: Int = 0,
        weapons: List<Weapon> = listOf(mediumLaser()),
    ): CombatUnit = aUnit(
        id = id,
        name = name,
        owner = owner,
        position = position,
        currentHeat = currentHeat,
        weapons = weapons,
        walkingMP = 4,
        runningMP = 6,
        jumpMP = 4,
        armor = anArmorLayout(
            centerTorso = 30, centerTorsoRear = 10,
            leftTorso = 25, leftTorsoRear = 8,
            rightTorso = 25, rightTorsoRear = 8,
            leftArm = 20, rightArm = 20,
            leftLeg = 25, rightLeg = 25,
        ),
    )
}
