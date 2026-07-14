package battletech.network.wire

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.FallResult
import battletech.tactical.attack.LocationDamage
import battletech.tactical.attack.LocationHit
import battletech.tactical.attack.RangeBand
import battletech.tactical.attack.ToHitFactor
import battletech.tactical.attack.ToHitModifier
import battletech.tactical.attack.physical.AttackDirection
import battletech.tactical.attack.physical.Knockdown
import battletech.tactical.attack.physical.PhysicalAttackDeclaration
import battletech.tactical.attack.physical.PhysicalAttackKind
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.attack.physical.Side
import battletech.tactical.dice.DiceRoll
import battletech.tactical.dice.RandomDiceRoller
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.MovementMode
import battletech.tactical.model.PlayerId
import battletech.tactical.model.Terrain
import battletech.tactical.model.TurnPhase
import battletech.tactical.movement.MovementStep
import battletech.tactical.movement.ReachableHex
import battletech.tactical.session.AmmoExploded
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.BattleSession
import battletech.tactical.session.CommandRejection
import battletech.tactical.session.CommandResult
import battletech.tactical.session.CommitAttackImpulse
import battletech.tactical.session.CommitPhysicalAttackImpulse
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.GameCommand
import battletech.tactical.session.GameEvent
import battletech.tactical.session.HeatDissipated
import battletech.tactical.session.Initiative
import battletech.tactical.session.InitiativeRolled
import battletech.tactical.session.LogEntry
import battletech.tactical.session.MatchEnded
import battletech.tactical.session.MoveUnit
import battletech.tactical.session.PhaseChanged
import battletech.tactical.session.PhysicalAttacksResolved
import battletech.tactical.session.PilotHit
import battletech.tactical.session.PilotKnockedUnconscious
import battletech.tactical.session.PilotRecoveredConsciousness
import battletech.tactical.session.RuleRejection
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.StandUp
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnEnded
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitDestroyed
import battletech.tactical.session.UnitFell
import battletech.tactical.session.UnitMoved
import battletech.tactical.session.UnitRestarted
import battletech.tactical.session.UnitShutdown
import battletech.tactical.session.UnitStoodUp
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.DestructionReason
import battletech.tactical.unit.PilotingSkillRoll
import battletech.tactical.unit.UnitId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * Confirms every wire type round-trips through [WireJson] byte-for-value
 * (encode then decode equals the original), including nested polymorphism
 * (a [GameCommand]/[GameEvent] inside a [ClientMessage]/[ServerMessage]) and
 * a full session snapshot built from a real [BattleSession].
 *
 * The `every ... subtype has a fixture` tests are a completeness guard: they
 * fail the build the moment a new sealed subtype is added to [GameCommand],
 * [GameEvent], [CommandRejection], or [RuleRejection] without a matching
 * fixture here.
 */
internal class WireFormatRoundTripTest {

    // ---------- GameCommand ----------

    @ParameterizedTest(name = "{0}")
    @MethodSource("gameCommands")
    fun `GameCommand round-trips`(command: GameCommand) {
        val line = WireJson.json.encodeToString(command)
        val decoded = WireJson.json.decodeFromString<GameCommand>(line)

        assertThat(decoded).isEqualTo(command)
    }

    @Test
    fun `every GameCommand subtype has a fixture`() {
        assertThat(gameCommandFixtures.keys)
            .containsExactlyInAnyOrderElementsOf(GameCommand::class.sealedSubclasses)
    }

    // ---------- GameEvent ----------

    @ParameterizedTest(name = "{0}")
    @MethodSource("gameEvents")
    fun `GameEvent round-trips`(event: GameEvent) {
        val line = WireJson.json.encodeToString(event)
        val decoded = WireJson.json.decodeFromString<GameEvent>(line)

        assertThat(decoded).isEqualTo(event)
    }

    @Test
    fun `every GameEvent subtype has a fixture`() {
        assertThat(gameEventFixtures.keys)
            .containsExactlyInAnyOrderElementsOf(GameEvent::class.sealedSubclasses)
    }

    // ---------- CommandRejection ----------

    @ParameterizedTest(name = "{0}")
    @MethodSource("commandRejections")
    fun `CommandRejection round-trips`(rejection: CommandRejection) {
        val line = WireJson.json.encodeToString(rejection)
        val decoded = WireJson.json.decodeFromString<CommandRejection>(line)

        assertThat(decoded).isEqualTo(rejection)
    }

    @Test
    fun `every CommandRejection subtype has a fixture`() {
        assertThat(commandRejectionFixtures.keys)
            .containsExactlyInAnyOrderElementsOf(CommandRejection::class.sealedSubclasses)
    }

    // ---------- RuleRejection ----------

    @ParameterizedTest(name = "{0}")
    @MethodSource("ruleRejections")
    fun `RuleRejection round-trips`(rejection: RuleRejection) {
        val line = WireJson.json.encodeToString(rejection)
        val decoded = WireJson.json.decodeFromString<RuleRejection>(line)

        assertThat(decoded).isEqualTo(rejection)
    }

    @Test
    fun `every RuleRejection subtype has a fixture`() {
        assertThat(ruleRejectionFixtures.keys)
            .containsExactlyInAnyOrderElementsOf(RuleRejection::class.sealedSubclasses)
    }

    // ---------- CommandResult ----------

    @Test
    fun `CommandResult Accepted with a non-empty event list round-trips`() {
        val accepted = CommandResult.Accepted(events = listOf(gameEventFixtures.getValue(UnitMoved::class), gameEventFixtures.getValue(PhaseChanged::class)))

        val line = WireJson.json.encodeToString<CommandResult>(accepted)
        val decoded = WireJson.json.decodeFromString<CommandResult>(line)

        assertThat(decoded).isEqualTo(accepted)
    }

    @Test
    fun `CommandResult Rejected round-trips`() {
        val rejected = CommandResult.Rejected(reason = commandRejectionFixtures.getValue(CommandRejection.WrongPhase::class))

        val line = WireJson.json.encodeToString<CommandResult>(rejected)
        val decoded = WireJson.json.decodeFromString<CommandResult>(line)

        assertThat(decoded).isEqualTo(rejected)
    }

    // ---------- Envelope nesting: ClientMessage / ServerMessage ----------

    @Test
    fun `ClientMessage Join round-trips`() {
        val message = ClientMessage.Join(sessionId = "ABCDEF", protocolVersion = PROTOCOL_VERSION)

        val line = WireJson.encodeToLine(message)
        val decoded = WireJson.decodeClientMessage(line)

        assertThat(decoded).isEqualTo(message)
    }

    @Test
    fun `ClientMessage SubmitCommand wrapping a GameCommand round-trips`() {
        val message = ClientMessage.SubmitCommand(requestId = 7L, command = gameCommandFixtures.getValue(MoveUnit::class))

        val line = WireJson.encodeToLine(message)
        val decoded = WireJson.decodeClientMessage(line)

        assertThat(decoded).isEqualTo(message)
    }

    @Test
    fun `ServerMessage JoinRejected round-trips`() {
        val message = ServerMessage.JoinRejected(reason = JoinRejectionReason.SEAT_TAKEN)

        val line = WireJson.encodeToLine(message)
        val decoded = WireJson.decodeServerMessage(line)

        assertThat(decoded).isEqualTo(message)
    }

    @Test
    fun `ServerMessage CommandReply wrapping a CommandResult round-trips`() {
        val message = ServerMessage.CommandReply(
            requestId = 42L,
            result = CommandResult.Accepted(events = listOf(gameEventFixtures.getValue(AttacksResolved::class))),
        )

        val line = WireJson.encodeToLine(message)
        val decoded = WireJson.decodeServerMessage(line)

        assertThat(decoded).isEqualTo(message)
    }

    @Test
    fun `ServerMessage JoinAccepted wrapping a GameSnapshot and log round-trips`() {
        val message = ServerMessage.JoinAccepted(
            playerId = PlayerId.PLAYER_2,
            snapshot = aGameSnapshot(),
            log = listOf(LogEntry(turn = 1, event = gameEventFixtures.getValue(PhaseChanged::class))),
        )

        val line = WireJson.encodeToLine(message)
        val decoded = WireJson.decodeServerMessage(line)

        assertThat(decoded).isEqualTo(message)
    }

    // ---------- Full session snapshot / log / command, built from a real BattleSession ----------

    @Test
    fun `a full GameSnapshot built from a real session round-trips`() {
        val session = aSampleSession()
        session.advance()

        val snapshot = GameSnapshot(
            gameState = session.gameState,
            turnState = session.turnState,
            currentPhase = session.currentPhase,
            activePlayer = session.activePlayer,
            isMatchOver = session.isMatchOver,
        )

        val line = WireJson.json.encodeToString(snapshot)
        val decoded = WireJson.json.decodeFromString<GameSnapshot>(line)

        assertThat(decoded).isEqualTo(snapshot)
    }

    @Test
    fun `a StatePush carrying real gameLog entries round-trips`() {
        val session = aSampleSession()
        session.advance()

        val push = ServerMessage.StatePush(
            entries = session.gameLog.snapshot(),
            snapshot = GameSnapshot(
                gameState = session.gameState,
                turnState = session.turnState,
                currentPhase = session.currentPhase,
                activePlayer = session.activePlayer,
                isMatchOver = session.isMatchOver,
            ),
        )
        assertThat(push.entries).isNotEmpty // sanity: advance() actually logged something real

        val line = WireJson.encodeToLine(push)
        val decoded = WireJson.decodeServerMessage(line)

        assertThat(decoded).isEqualTo(push)
    }

    @Test
    fun `a ClientMessage SubmitCommand carrying a real MoveUnit built from session state round-trips`() {
        val session = aSampleSession()
        session.advance()
        val mover = session.gameState.unitsOf(PlayerId.PLAYER_1).first()
        val destination = ReachableHex(
            position = mover.position.neighbor(mover.facing),
            facing = mover.facing,
            mpSpent = 1,
            path = listOf(MovementStep(mover.position.neighbor(mover.facing), mover.facing)),
        )
        val command = MoveUnit(playerId = PlayerId.PLAYER_1, unitId = mover.id, destination = destination, mode = MovementMode.WALK)
        val message = ClientMessage.SubmitCommand(requestId = 1L, command = command)

        val line = WireJson.encodeToLine(message)
        val decoded = WireJson.decodeClientMessage(line)

        assertThat(decoded).isEqualTo(message)
    }

    // ---------- fixtures ----------

    private companion object {

        private val unitA = UnitId("A1")
        private val unitB = UnitId("W1")

        private fun aSampleSession(): BattleSession = BattleSession(
            initialGameState = GameStateFactory().sampleGameState(),
            roller = RandomDiceRoller(Random(42L)),
        )

        private fun aGameSnapshot(): GameSnapshot = GameSnapshot(
            gameState = GameStateFactory().sampleGameState(),
            turnState = aTurnStateFixture(),
            currentPhase = TurnPhase.MOVEMENT,
            activePlayer = PlayerId.PLAYER_1,
            isMatchOver = false,
        )

        private fun aTurnStateFixture(): TurnState = TurnState(initiative = anInitiativeFixture())

        private fun anInitiativeFixture(): Initiative = Initiative(
            rolls = mapOf(PlayerId.PLAYER_1 to DiceRoll(2, 3), PlayerId.PLAYER_2 to DiceRoll(4, 4)),
            loser = PlayerId.PLAYER_1,
            winner = PlayerId.PLAYER_2,
        )

        private fun aPilotingSkillRoll(): PilotingSkillRoll =
            PilotingSkillRoll(targetNumber = 7, roll = DiceRoll(4, 4), passed = true)

        private fun aFallResult(): FallResult = FallResult(
            damage = 5,
            hitLocation = MechLocation.CENTER_TORSO,
            locationRoll = DiceRoll(3, 4),
            newFacing = HexDirection.SE,
            facingRoll = 3,
        )

        private fun anAttackResult(): AttackResult = AttackResult.SingleHit(
            attackerId = unitA,
            targetId = unitB,
            weaponName = "Medium Laser",
            targetNumber = 7,
            toHitRoll = DiceRoll(4, 4),
            gunnery = 4,
            rangeBand = RangeBand.MEDIUM,
            damage = listOf(LocationDamage(MechLocation.CENTER_TORSO, armorDamage = 5, structureDamage = 0, destroyed = false)),
            modifiers = listOf(
                ToHitModifier(ToHitFactor.RANGE, "range", 2),
                ToHitModifier(ToHitFactor.HEAT, "heat", 0),
            ),
            partialCover = false,
            useRearArmor = false,
            locationHits = listOf(LocationHit(MechLocation.CENTER_TORSO, damage = 5, locationRoll = DiceRoll(3, 4))),
        )

        private fun aPhysicalAttackResult(): PhysicalAttackResult = PhysicalAttackResult.Hit(
            attackerId = unitA,
            targetId = unitB,
            attackName = "Kick",
            hitLocation = MechLocation.LEFT_LEG,
            damageApplied = 4,
            targetNumber = 6,
            toHitRoll = DiceRoll(4, 4),
            locationRoll = 5,
            attackDirection = AttackDirection.FRONT,
            damage = listOf(LocationDamage(MechLocation.LEFT_LEG, armorDamage = 4, structureDamage = 0, destroyed = false)),
            knockdown = Knockdown.Fell(
                unitId = unitB,
                psr = aPilotingSkillRoll(),
                fall = aFallResult(),
                pilotEvents = listOf(PilotHit(unitB, pilotHits = 1, consciousnessRoll = DiceRoll(3, 3), conscious = true)),
            ),
        )

        private val gameCommandFixtures: Map<KClass<out GameCommand>, GameCommand> = mapOf(
            MoveUnit::class to MoveUnit(
                playerId = PlayerId.PLAYER_1,
                unitId = unitA,
                destination = ReachableHex(
                    position = HexCoordinates(2, 2),
                    facing = HexDirection.N,
                    mpSpent = 2,
                    path = listOf(MovementStep(HexCoordinates(1, 1), HexDirection.N), MovementStep(HexCoordinates(2, 2), HexDirection.N)),
                ),
                mode = MovementMode.WALK,
            ),
            StandUp::class to StandUp(playerId = PlayerId.PLAYER_1, unitId = unitA),
            CommitAttackImpulse::class to CommitAttackImpulse(
                playerId = PlayerId.PLAYER_1,
                declarations = listOf(AttackDeclaration(attackerId = unitA, targetId = unitB, weaponIndex = 0, isPrimary = true)),
                torsoFacings = mapOf(unitA to HexDirection.NE),
            ),
            CommitPhysicalAttackImpulse::class to CommitPhysicalAttackImpulse(
                playerId = PlayerId.PLAYER_2,
                declarations = listOf(PhysicalAttackDeclaration(attackerId = unitB, targetId = unitA, kind = PhysicalAttackKind.Kick(Side.LEFT))),
                torsoFacings = emptyMap(),
            ),
        )

        private val gameEventFixtures: Map<KClass<out GameEvent>, GameEvent> = mapOf(
            UnitMoved::class to UnitMoved(
                unitId = unitA,
                from = HexCoordinates(1, 1),
                to = HexCoordinates(2, 2),
                finalFacing = HexDirection.NE,
                mode = MovementMode.WALK,
                mpSpent = 3,
            ),
            AttacksResolved::class to AttacksResolved(results = listOf(anAttackResult())),
            PhysicalAttacksResolved::class to PhysicalAttacksResolved(results = listOf(aPhysicalAttackResult())),
            UnitFell::class to UnitFell(unitId = unitA, fall = aFallResult()),
            UnitStoodUp::class to UnitStoodUp(unitId = unitA, psr = aPilotingSkillRoll(), stoodUp = true),
            AttackDeclarationsRecorded::class to AttackDeclarationsRecorded(
                player = PlayerId.PLAYER_1,
                declarations = listOf(AttackDeclaration(unitA, unitB, 0, true)),
            ),
            TorsoFacingsApplied::class to TorsoFacingsApplied(facings = mapOf(unitA to HexDirection.NE)),
            PhaseChanged::class to PhaseChanged(from = TurnPhase.MOVEMENT, to = TurnPhase.WEAPON_ATTACK),
            InitiativeRolled::class to InitiativeRolled(initiative = anInitiativeFixture()),
            HeatDissipated::class to HeatDissipated(heatBefore = mapOf(unitA to 5), heatAfter = mapOf(unitA to 3)),
            UnitShutdown::class to UnitShutdown(unitId = unitA, roll = DiceRoll(3, 4), auto = false),
            UnitRestarted::class to UnitRestarted(unitId = unitA, roll = null),
            AmmoExploded::class to AmmoExploded(unitId = unitA, ammoType = AmmoType.SRM6, damage = 12),
            TurnEnded::class to TurnEnded(turnNumber = 2),
            UnitDestroyed::class to UnitDestroyed(unitId = unitA, reason = DestructionReason.ENGINE_DESTROYED),
            MatchEnded::class to MatchEnded(winner = PlayerId.PLAYER_2),
            CriticalHit::class to CriticalHit(
                unitId = unitA,
                location = MechLocation.LEFT_TORSO,
                slotIndex = 3,
                content = CriticalSlotContent.AmmoBin(type = AmmoType.LRM10, shots = 6),
            ),
            PilotHit::class to PilotHit(unitId = unitA, pilotHits = 2, consciousnessRoll = DiceRoll(3, 3), conscious = true),
            PilotKnockedUnconscious::class to PilotKnockedUnconscious(unitId = unitA),
            PilotRecoveredConsciousness::class to PilotRecoveredConsciousness(unitId = unitA, roll = DiceRoll(5, 6)),
            SessionNotice::class to SessionNotice(text = "Opponent connected"),
        )

        private val ruleRejectionFixtures: Map<KClass<out RuleRejection>, RuleRejection> = mapOf(
            RuleRejection.NotAdjacent::class to RuleRejection.NotAdjacent(distance = 3),
            RuleRejection.NoAmmo::class to RuleRejection.NoAmmo(weaponName = "LRM-10"),
            RuleRejection.OutOfRange::class to RuleRejection.OutOfRange(weaponName = "Medium Laser", distance = 10, maxRange = 6),
            RuleRejection.NoLineOfSight::class to RuleRejection.NoLineOfSight(blockerAt = HexCoordinates(3, 3), blockingTerrain = Terrain.HEAVY_WOODS),
            RuleRejection.WeaponDestroyed::class to RuleRejection.WeaponDestroyed(weaponName = "PPC"),
            RuleRejection.LimbAlreadyUsed::class to RuleRejection.LimbAlreadyUsed(attackerId = unitA),
            RuleRejection.PunchAndKickSameTurn::class to RuleRejection.PunchAndKickSameTurn(attackerId = unitA),
            RuleRejection.LimbDestroyed::class to RuleRejection.LimbDestroyed(attackerId = unitA),
            RuleRejection.CannotKickAfterRunningOrJumping::class to RuleRejection.CannotKickAfterRunningOrJumping,
            RuleRejection.CannotPunchAfterJumping::class to RuleRejection.CannotPunchAfterJumping,
            RuleRejection.ElevationOutOfReach::class to RuleRejection.ElevationOutOfReach(levelDifference = 2),
            RuleRejection.TargetUnderwater::class to RuleRejection.TargetUnderwater(depth = 2),
            RuleRejection.AttackerProne::class to RuleRejection.AttackerProne,
            RuleRejection.TargetDestroyed::class to RuleRejection.TargetDestroyed,
            RuleRejection.AttackerSubmerged::class to RuleRejection.AttackerSubmerged(depth = 2),
        )

        private val commandRejectionFixtures: Map<KClass<out CommandRejection>, CommandRejection> = mapOf(
            CommandRejection.NotYourTurn::class to CommandRejection.NotYourTurn(activePlayer = PlayerId.PLAYER_1, attemptedBy = PlayerId.PLAYER_2),
            CommandRejection.WrongPhase::class to CommandRejection.WrongPhase(actual = TurnPhase.HEAT),
            CommandRejection.UnitAlreadyActed::class to CommandRejection.UnitAlreadyActed(unitId = unitA),
            CommandRejection.UnknownUnit::class to CommandRejection.UnknownUnit(unitId = UnitId("ghost")),
            CommandRejection.UnitProne::class to CommandRejection.UnitProne(unitId = unitA),
            CommandRejection.UnitNotProne::class to CommandRejection.UnitNotProne(unitId = unitA),
            CommandRejection.GyroDestroyed::class to CommandRejection.GyroDestroyed(unitId = unitA),
            CommandRejection.LegDestroyed::class to CommandRejection.LegDestroyed(unitId = unitA),
            CommandRejection.NoSuchWeapon::class to CommandRejection.NoSuchWeapon(unitId = unitA, weaponIndex = 3),
            CommandRejection.FriendlyFire::class to CommandRejection.FriendlyFire(targetId = unitB),
            CommandRejection.IllegalTorsoTwist::class to CommandRejection.IllegalTorsoTwist(unitId = unitA, facing = HexDirection.S),
            CommandRejection.DestinationUnreachable::class to CommandRejection.DestinationUnreachable(unitId = unitA, destination = HexCoordinates(5, 5)),
            CommandRejection.MatchOver::class to CommandRejection.MatchOver,
            CommandRejection.RuleViolation::class to CommandRejection.RuleViolation(rule = ruleRejectionFixtures.getValue(RuleRejection.NotAdjacent::class)),
            CommandRejection.OpponentUnavailable::class to CommandRejection.OpponentUnavailable,
        )

        @JvmStatic
        fun gameCommands(): Stream<Arguments> = gameCommandFixtures.values.map { Arguments.of(it) }.stream()

        @JvmStatic
        fun gameEvents(): Stream<Arguments> = gameEventFixtures.values.map { Arguments.of(it) }.stream()

        @JvmStatic
        fun commandRejections(): Stream<Arguments> = commandRejectionFixtures.values.map { Arguments.of(it) }.stream()

        @JvmStatic
        fun ruleRejections(): Stream<Arguments> = ruleRejectionFixtures.values.map { Arguments.of(it) }.stream()
    }
}
