package battletech.tui.view

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.LocationDamage
import battletech.tactical.attack.RangeBand
import battletech.tactical.attack.physical.AttackDirection
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.GameMap
import battletech.tactical.model.GameState
import battletech.tactical.model.Hex
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.HeatDissipated
import battletech.tactical.session.Initiative
import battletech.tactical.session.InitiativeRolled
import battletech.tactical.session.MatchEnded
import battletech.tactical.session.PhaseChanged
import battletech.tactical.session.PhysicalAttacksResolved
import battletech.tactical.session.PilotHit
import battletech.tactical.session.PilotKnockedUnconscious
import battletech.tactical.session.PilotRecoveredConsciousness
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
import battletech.tactical.unit.WeaponMountId
import battletech.tactical.unit.WeaponModels
import battletech.tui.aUnit
import battletech.tui.anArmorLayout
import battletech.tui.mediumLaser
import battletech.tui.hex.diceIcon
import battletech.tui.hex.movementModeIcon
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GameLogFormatterTest {

    private val emptyState = GameState(
        units = emptyList(),
        map = GameMap(mapOf(HexCoordinates(0, 0) to Hex(HexCoordinates(0, 0)))),
    )

    @Test
    fun `PhaseChanged is not logged`() {
        val text = GameLogFormatter.format(
            event = PhaseChanged(TurnPhase.INITIATIVE, TurnPhase.MOVEMENT),
            state = emptyState,
        )

        assertThat(text).isNull()
    }

    @Test
    fun `UnitMoved shows the unit name, mode icon, hex coords, and MP spent`() {
        val atlas = aMech(id = "atlas", name = "Atlas", position = HexCoordinates(2, 0))
        val stateWithAtlas = emptyState.copy(units = listOf(atlas))

        val text = GameLogFormatter.format(
            event = UnitMoved(
                unitId = atlas.id,
                from = HexCoordinates(2, 0),
                to = HexCoordinates(2, 1),
                finalFacing = HexDirection.N,
                mode = MovementMode.WALK,
                mpSpent = 3,
            ),
            state = stateWithAtlas,
        )

        assertThat(text).isEqualTo("Atlas ${movementModeIcon(MovementMode.WALK)} (3 MP) 0301→0302")
    }

    @Test
    fun `UnitMoved uses the run icon for RUN and jump icon for JUMP`() {
        val atlas = aMech(id = "atlas", name = "Atlas", position = HexCoordinates(0, 0))
        val stateWithAtlas = emptyState.copy(units = listOf(atlas))

        val ran = GameLogFormatter.format(
            event = UnitMoved(atlas.id, HexCoordinates(0, 0), HexCoordinates(0, 1),
                HexDirection.N, MovementMode.RUN, 5),
            stateWithAtlas,
        )
        val jumped = GameLogFormatter.format(
            event = UnitMoved(atlas.id, HexCoordinates(0, 0), HexCoordinates(0, 1),
                HexDirection.N, MovementMode.JUMP, 4),
            stateWithAtlas,
        )

        assertThat(ran).isEqualTo("Atlas ${movementModeIcon(MovementMode.RUN)} (5 MP) 0101→0102")
        assertThat(jumped).isEqualTo("Atlas ${movementModeIcon(MovementMode.JUMP)} (4 MP) 0101→0102")
    }

    @Test
    fun `InitiativeRolled shows both rolls with dice icons and the player who moves first`() {
        val initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(3, 3), PlayerId.PLAYER_2 to DiceRoll(1, 2)),
            loser = PlayerId.PLAYER_2,
            winner = PlayerId.PLAYER_1,
        )

        val text = GameLogFormatter.format(
            event = InitiativeRolled(initiative),
            state = emptyState,
        )

        assertThat(text).isEqualTo(
            "Initiative: P1 ${diceIcon(3)}+${diceIcon(3)}=6, P2 ${diceIcon(1)}+${diceIcon(2)}=3 — P2 moves first"
        )
    }

    @Test
    fun `AttacksResolved summarizes fired, hit, and damage`() {
        val results = listOf(
            anAttackResult(hit = true, damage = 5),
            anAttackResult(hit = true, damage = 7),
            anAttackResult(hit = false, damage = 0),
        )

        val text = GameLogFormatter.format(
            event = AttacksResolved(results),
            state = emptyState,
        )

        assertThat(text).isEqualTo("Attacks: 3 fired, 2 hit, 12 damage")
    }

    @Test
    fun `AttacksResolved with no attacks reads gracefully`() {
        val text = GameLogFormatter.format(
            event = AttacksResolved(emptyList()),
            state = emptyState,
        )

        assertThat(text).isEqualTo("Attacks: 0 fired, 0 hit, 0 damage")
    }

    @Test
    fun `AttacksResolved appends a destroyed-location clause when a location was blown off`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))
        val results = listOf(
            anAttackResult(
                hit = true,
                damage = 24,
                targetId = locust.id,
                locationDamage = listOf(
                    LocationDamage(MechLocation.LEFT_ARM, armorDamage = 20, structureDamage = 6, destroyed = true),
                    LocationDamage(MechLocation.LEFT_TORSO, armorDamage = 4, structureDamage = 0, destroyed = false),
                ),
            ),
        )

        val text = GameLogFormatter.format(
            event = AttacksResolved(results),
            state = stateWithLocust,
        )

        assertThat(text).isEqualTo("Attacks: 1 fired, 1 hit, 24 damage — Locust Left Arm destroyed")
    }

    @Test
    fun `AttacksResolved omits the destroyed-location clause when nothing was destroyed`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))
        val results = listOf(
            anAttackResult(
                hit = true,
                damage = 5,
                targetId = locust.id,
                locationDamage = listOf(
                    LocationDamage(MechLocation.LEFT_ARM, armorDamage = 5, structureDamage = 0, destroyed = false),
                ),
            ),
        )

        val text = GameLogFormatter.format(
            event = AttacksResolved(results),
            state = stateWithLocust,
        )

        assertThat(text).isEqualTo("Attacks: 1 fired, 1 hit, 5 damage")
    }

    @Test
    fun `PhysicalAttacksResolved appends a destroyed-location clause when a location was blown off`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))
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

        val text = GameLogFormatter.format(
            event = PhysicalAttacksResolved(results),
            state = stateWithLocust,
        )

        assertThat(text).isEqualTo("Physical attacks: 1 made, 1 hit, 18 damage — Locust Right Leg destroyed")
    }

    @Test
    fun `PhysicalAttacksResolved omits the destroyed-location clause when nothing was destroyed`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))
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

        val text = GameLogFormatter.format(
            event = PhysicalAttacksResolved(results),
            state = stateWithLocust,
        )

        assertThat(text).isEqualTo("Physical attacks: 1 made, 1 hit, 3 damage")
    }

    @Test
    fun `AttackDeclarationsRecorded shows attacker, target, and fired weapons`() {
        val atlas = aMech(id = "atlas", name = "Atlas", owner = PlayerId.PLAYER_1)
        val locust = aMech(id = "locust", name = "Locust", owner = PlayerId.PLAYER_2)
        val stateWithUnits = emptyState.copy(units = listOf(atlas, locust))

        val text = GameLogFormatter.format(
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
        assertThat(text).isEqualTo("P1 declared: Atlas→Locust (Medium Laser)")
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
        val stateWithUnits = emptyState.copy(units = listOf(atlas, locust))

        val text = GameLogFormatter.format(
            event = AttackDeclarationsRecorded(
                player = PlayerId.PLAYER_1,
                declarations = listOf(
                    AttackDeclaration(atlas.id, locust.id, 0, true),
                    AttackDeclaration(atlas.id, locust.id, 1, false),
                ),
            ),
            state = stateWithUnits,
        )

        assertThat(text).isEqualTo("P1 declared: Atlas→Locust (Medium Laser, LRM 5)")
    }

    @Test
    fun `AttackDeclarationsRecorded lists multiple attacker-target pairs separated by commas`() {
        val atlas = aMech(id = "atlas", name = "Atlas", owner = PlayerId.PLAYER_1)
        val locust = aMech(id = "locust", name = "Locust", owner = PlayerId.PLAYER_2)
        val marauder = aMech(id = "marauder", name = "Marauder", owner = PlayerId.PLAYER_2)
        val stateWithUnits = emptyState.copy(units = listOf(atlas, locust, marauder))

        val text = GameLogFormatter.format(
            event = AttackDeclarationsRecorded(
                player = PlayerId.PLAYER_1,
                declarations = listOf(
                    AttackDeclaration(atlas.id, locust.id, 0, true),
                    AttackDeclaration(atlas.id, marauder.id, 0, false),
                ),
            ),
            state = stateWithUnits,
        )

        assertThat(text).isEqualTo("P1 declared: Atlas→Locust (Medium Laser), Atlas→Marauder (Medium Laser)")
    }

    @Test
    fun `TorsoFacingsApplied lists each unit and its torso facing`() {
        val atlas = aMech(id = "atlas", name = "Atlas")
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithUnits = emptyState.copy(units = listOf(atlas, locust))

        val text = GameLogFormatter.format(
            event = TorsoFacingsApplied(
                facings = mapOf(
                    atlas.id to HexDirection.NE,
                    locust.id to HexDirection.S,
                ),
            ),
            state = stateWithUnits,
        )

        assertThat(text).isEqualTo("Torso facings: Atlas→NE, Locust→S")
    }

    @Test
    fun `TorsoFacingsApplied with no facings says 'no changes'`() {
        val text = GameLogFormatter.format(
            event = TorsoFacingsApplied(facings = emptyMap()),
            state = emptyState,
        )

        assertThat(text).isEqualTo("Torso facings: no changes")
    }

    @Test
    fun `HeatDissipated shows before-arrow-after per unit that had heat`() {
        val atlas = aMech(id = "atlas", name = "Atlas")
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithUnits = emptyState.copy(units = listOf(atlas, locust))

        val text = GameLogFormatter.format(
            event = HeatDissipated(
                heatBefore = mapOf(atlas.id to 8, locust.id to 0),
                heatAfter = mapOf(atlas.id to 4, locust.id to 0),
            ),
            state = stateWithUnits,
        )

        assertThat(text).isEqualTo("Heat: Atlas 8→4")
    }

    @Test
    fun `HeatDissipated with no heat reports 'no heat'`() {
        val atlas = aMech(id = "atlas", name = "Atlas")
        val stateWithAtlas = emptyState.copy(units = listOf(atlas))

        val text = GameLogFormatter.format(
            event = HeatDissipated(
                heatBefore = mapOf(atlas.id to 0),
                heatAfter = mapOf(atlas.id to 0),
            ),
            state = stateWithAtlas,
        )

        assertThat(text).isEqualTo("Heat: no heat to dissipate")
    }

    @Test
    fun `TurnEnded is not logged`() {
        val text = GameLogFormatter.format(event = TurnEnded(turnNumber = 3), state = emptyState)
        assertThat(text).isNull()
    }

    @Test
    fun `UnitDestroyed shows unit name and a readable destruction reason`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))

        val text = GameLogFormatter.format(
            event = UnitDestroyed(unitId = locust.id, reason = DestructionReason.CENTER_TORSO_DESTROYED),
            state = stateWithLocust,
        )

        assertThat(text).isEqualTo("Locust destroyed (center torso destroyed)")
    }

    @Test
    fun `UnitDestroyed renders every destruction reason readably`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))

        val expected = mapOf(
            DestructionReason.HEAD_DESTROYED to "head destroyed",
            DestructionReason.CENTER_TORSO_DESTROYED to "center torso destroyed",
            DestructionReason.BOTH_LEGS_DESTROYED to "both legs destroyed",
            DestructionReason.ENGINE_DESTROYED to "engine destroyed",
            DestructionReason.PILOT_DEAD to "pilot dead",
        )

        for ((reason, label) in expected) {
            val text = GameLogFormatter.format(
                event = UnitDestroyed(unitId = locust.id, reason = reason),
                state = stateWithLocust,
            )
            assertThat(text).isEqualTo("Locust destroyed ($label)")
        }
    }

    @Test
    fun `MatchEnded reports the winner`() {
        val text = GameLogFormatter.format(
            event = MatchEnded(winner = PlayerId.PLAYER_1),
            state = emptyState,
        )

        assertThat(text).isEqualTo("Match over — P1 wins!")
    }

    @Test
    fun `MatchEnded reports a draw when winner is null`() {
        val text = GameLogFormatter.format(
            event = MatchEnded(winner = null),
            state = emptyState,
        )

        assertThat(text).isEqualTo("Match over — draw")
    }

    @Test
    fun `CriticalHit on a named component shows unit, component, and location`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))

        val text = GameLogFormatter.format(
            event = CriticalHit(
                unitId = locust.id,
                location = MechLocation.CENTER_TORSO,
                slotIndex = 0,
                content = CriticalSlotContent.Engine,
            ),
            state = stateWithLocust,
        )

        assertThat(text).isEqualTo("Locust critical hit: Engine in Center Torso")
    }

    @Test
    fun `CriticalHit on a weapon mount resolves the weapon's name`() {
        val weapon = Weapon(model = WeaponModels.mediumLaser, mountId = WeaponMountId(0))
        val locust = aMech(id = "locust", name = "Locust", weapons = listOf(weapon))
        val stateWithLocust = emptyState.copy(units = listOf(locust))

        val text = GameLogFormatter.format(
            event = CriticalHit(
                unitId = locust.id,
                location = MechLocation.RIGHT_ARM,
                slotIndex = 5,
                content = CriticalSlotContent.WeaponMount(WeaponMountId(0)),
            ),
            state = stateWithLocust,
        )

        assertThat(text).isEqualTo("Locust critical hit: Medium Laser in Right Arm")
    }

    @Test
    fun `CriticalHit on an ammo bin and an actuator render readably`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))

        val ammoText = GameLogFormatter.format(
            event = CriticalHit(
                unitId = locust.id,
                location = MechLocation.LEFT_TORSO,
                slotIndex = 2,
                content = CriticalSlotContent.AmmoBin(battletech.tactical.unit.AmmoType.AC20, shots = 5),
            ),
            state = stateWithLocust,
        )
        assertThat(ammoText).isEqualTo("Locust critical hit: AC20 ammo in Left Torso")

        val actuatorText = GameLogFormatter.format(
            event = CriticalHit(
                unitId = locust.id,
                location = MechLocation.LEFT_ARM,
                slotIndex = 1,
                content = CriticalSlotContent.Actuator(ActuatorType.UPPER_ARM),
            ),
            state = stateWithLocust,
        )
        assertThat(actuatorText).isEqualTo("Locust critical hit: Upper arm actuator in Left Arm")
    }

    @Test
    fun `PilotHit shows the running pilot hit total`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))

        val text = GameLogFormatter.format(
            event = PilotHit(unitId = locust.id, pilotHits = 1, consciousnessRoll = null, conscious = true),
            state = stateWithLocust,
        )

        assertThat(text).isEqualTo("Locust pilot wounded (1 hit total)")
    }

    @Test
    fun `PilotHit pluralizes hits when more than one`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))

        val text = GameLogFormatter.format(
            event = PilotHit(
                unitId = locust.id, pilotHits = 3,
                consciousnessRoll = DiceRoll(4, 4), conscious = true,
            ),
            state = stateWithLocust,
        )

        assertThat(text).isEqualTo("Locust pilot wounded (3 hits total)")
    }

    @Test
    fun `PilotKnockedUnconscious and PilotRecoveredConsciousness render readably`() {
        val locust = aMech(id = "locust", name = "Locust")
        val stateWithLocust = emptyState.copy(units = listOf(locust))

        val unconsciousText = GameLogFormatter.format(
            event = PilotKnockedUnconscious(unitId = locust.id),
            state = stateWithLocust,
        )
        assertThat(unconsciousText).isEqualTo("Locust pilot knocked unconscious")

        val recoveredText = GameLogFormatter.format(
            event = PilotRecoveredConsciousness(unitId = locust.id, roll = DiceRoll(5, 5)),
            state = stateWithLocust,
        )
        assertThat(recoveredText).isEqualTo("Locust pilot regained consciousness")
    }

    private fun anAttackResult(
        hit: Boolean,
        damage: Int,
        targetId: UnitId = UnitId("t"),
        locationDamage: List<LocationDamage> = emptyList(),
    ): AttackResult =
        AttackResult(
            attackerId = UnitId("a"),
            targetId = targetId,
            weaponName = "ML",
            hit = hit,
            hitLocation = null,
            damageApplied = damage,
            targetNumber = 8,
            roll = if (hit) 10 else 5,
            toHitRoll = if (hit) DiceRoll(5, 5) else DiceRoll(2, 3),
            locationRoll = null,
            gunnery = 4,
            rangeModifier = 0,
            rangeBand = RangeBand.SHORT,
            heatPenalty = 0,
            secondaryPenalty = 0,
            damage = locationDamage,
        )

    private fun aPhysicalAttackResult(
        hit: Boolean,
        damage: Int,
        targetId: UnitId = UnitId("t"),
        locationDamage: List<LocationDamage> = emptyList(),
    ): PhysicalAttackResult =
        PhysicalAttackResult(
            attackerId = UnitId("a"),
            targetId = targetId,
            attackName = "Punch",
            hit = hit,
            hitLocation = null,
            damageApplied = damage,
            targetNumber = 8,
            roll = if (hit) 10 else 5,
            toHitRoll = if (hit) DiceRoll(5, 5) else DiceRoll(2, 3),
            locationRoll = null,
            attackDirection = AttackDirection.FRONT,
            damage = locationDamage,
        )

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
