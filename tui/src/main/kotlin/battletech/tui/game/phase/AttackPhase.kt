package battletech.tui.game.phase

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.model.GameState
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.DefaultPlayerView
import battletech.tactical.query.PlayerView
import battletech.tactical.query.PublicUnit
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.CommandResult
import battletech.tactical.session.CommitAttackImpulse
import battletech.tactical.session.TurnState
import battletech.tactical.session.UnitDeclaration
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.PanelId
import battletech.tui.game.RenderData
import battletech.tui.game.losHighlights
import battletech.tui.game.mapToTuiPhase
import battletech.tui.game.moveCursor
import battletech.tui.game.selectedLosHighlights
import battletech.tui.hex.HexHighlight
import battletech.tui.input.AttackAction
import battletech.tui.input.IdleAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

internal const val DECLARING_PROMPT =
    "←/→ twist torso | ↑/↓ navigate weapons | Space: toggle | Tab: next attacker | 'c': commit"

internal sealed interface AttackPhase : Phase {
    public val attackTurnPhase: TurnPhase

    /**
     * In-progress declarations for units other than the one currently being
     * edited (if any). On Tab/Cancel/Commit we fold the current Declaring's
     * state into [drafts] and read it back when re-entering the same unit.
     */
    public val drafts: Map<UnitId, UnitDeclaration>
    override val turnPhase: TurnPhase get() = attackTurnPhase

    override fun visiblePanels(gameState: GameState): Set<PanelId> = buildSet {
        // The declared-targets panel belongs to the weapon-attack declaration
        // flow only; the physical-attack flow leaves it empty (see
        // PhysicalAttackPhase), so reserving its column there would render as a
        // blank gap. Targets/TargetStatus follow whatever the active sub-phase
        // populates — SelectingAttacker has neither, Declaring has both.
        if (attackTurnPhase == TurnPhase.WEAPON_ATTACK) add(PanelId.DECLARED_TARGETS)
        if (attackRender(gameState)?.targets?.isNotEmpty() == true) add(PanelId.TARGETS)
        if (targetStatusUnit(gameState) != null) add(PanelId.TARGET_STATUS)
    }

    public data class SelectingAttacker(
        override val attackTurnPhase: TurnPhase,
        override val drafts: Map<UnitId, UnitDeclaration> = emptyMap(),
    ) : AttackPhase {

        override fun handle(event: InputEvent, app: AppState): Transition? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapIdleEvent(event)
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                    ?.let { IdleAction.ClickHex(it) }
            } ?: return null

            return when (action) {
                is IdleAction.MoveCursor -> {
                    val newCursor = moveCursor(app.cursor, action.direction, app.gameState.map)
                    Transition(app.copy(cursor = newCursor))
                }

                is IdleAction.ClickHex -> trySelect(app.copy(cursor = action.coords))
                is IdleAction.SelectUnit -> trySelect(app)
                is IdleAction.CycleUnit -> cycleUnit(app)
                is IdleAction.CommitDeclarations -> commitAttackImpulse(app, attackTurnPhase, drafts)
            }
        }

        override fun prompt(app: AppState): String {
            val turnState = app.turnState
            if (turnState.allAttackImpulsesComplete) return "All attacks declared"
            val playerName = if (turnState.activeAttackPlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
            return "$playerName: select units, toggle weapons | 'c' to commit"
        }

        override fun selectedUnit(app: AppState): CombatUnit? = app.gameState.unitAt(app.cursor)

        override fun activePlayerLabel(app: AppState): String? {
            val turnState = app.turnState
            if (turnState.attackSequence.order.isEmpty() || turnState.allAttackImpulsesComplete) return null
            return if (turnState.activeAttackPlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
        }

        override fun declaredTargetsRender(
            gameState: GameState,
            turnState: TurnState,
            viewingPlayer: PlayerId,
        ): DeclaredTargetsRender = buildDeclaredTargetsRender(gameState, turnState, viewingPlayer, drafts)

        private fun trySelect(app: AppState): Transition {
            val unit = app.gameState.unitAt(app.cursor) ?: return Transition(app)
            val turnState = app.turnState

            if (unit.owner != turnState.activeAttackPlayer) {
                return Transition(app, FlashMessage("Not your unit"))
            }

            val newPhase = enterDeclaring(unit, attackTurnPhase, app.viewFor(unit.owner), drafts)
            return Transition(app.copy(phase = newPhase))
        }

        private fun cycleUnit(app: AppState): Transition {
            val turnState = app.turnState
            val units = turnState.selectableAttackUnits(app.gameState)
            if (units.isEmpty()) return Transition(app)

            val currentIdx = units.indexOfFirst { it.position == app.cursor }
            val nextIdx = (currentIdx + 1) % units.size
            return Transition(app.copy(cursor = units[nextIdx].position))
        }
    }

    public data class Declaring(
        override val attackTurnPhase: TurnPhase,
        val unitId: UnitId,
        val torsoFacing: HexDirection,
        val cursorTargetIndex: Int,
        val cursorWeaponIndex: Int,
        val weaponAssignments: Map<UnitId, Set<Int>>,
        val primaryTargetId: UnitId?,
        override val drafts: Map<UnitId, UnitDeclaration> = emptyMap(),
    ) : AttackPhase {

        /** Snapshot of this unit's current draft as a [UnitDeclaration]. */
        public fun currentDeclaration(): UnitDeclaration = UnitDeclaration(
            unitId = unitId,
            torsoFacing = torsoFacing,
            primaryTargetId = primaryTargetId,
            weaponAssignments = weaponAssignments,
        )

        /** Drafts for ALL units including the one currently being edited. */
        public fun allDrafts(): Map<UnitId, UnitDeclaration> =
            drafts + (unitId to currentDeclaration())

        override fun handle(event: InputEvent, app: AppState): Transition? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapAttackEvent(event)
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                    ?.let { AttackAction.ClickTarget(it) }
            } ?: return null

            return when (action) {
                is AttackAction.NextAttacker -> nextAttacker(app)
                is AttackAction.Commit -> commitAttackImpulse(app, attackTurnPhase, allDrafts())
                is AttackAction.Cancel -> Transition(
                    app.copy(phase = SelectingAttacker(attackTurnPhase, allDrafts())),
                )
                is AttackAction.ToggleWeapon -> toggleWeapon(app)
                is AttackAction.TwistTorso -> twistTorso(app, action.clockwise)
                is AttackAction.NavigateWeapons ->
                    Transition(app.copy(phase = navigateWeapons(action.delta, app.gameState)))

                is AttackAction.ClickTarget -> clickTarget(app)
            }
        }

        override fun prompt(app: AppState): String = DECLARING_PROMPT

        override fun render(gameState: GameState): RenderData {
            val attacker = gameState.unitById(unitId) ?: return RenderData.EMPTY
            val view: PlayerView = playerView(attacker.owner, gameState)
            val arc = view.fireArc(unitId, torsoFacing)
            val validIds = view.validTargets(unitId, torsoFacing)
            val targets = view.targetInfos(unitId, torsoFacing)
            val arcHighlights = arc.associateWith { HexHighlight.ATTACK_RANGE }
            val torsoFacings = mapOf(attacker.position to torsoFacing)
            val targetPositions = view.resolveTargetPositions(validIds)
            val los = losHighlights(attacker, validIds, gameState)
            val selectedLos = selectedLosHighlights(attacker, this, targets, gameState)
            val selectedTargetPosition = targets.getOrNull(cursorTargetIndex)
                ?.let { gameState.unitById(it.unitId)?.position }
            return RenderData(
                hexHighlights = arcHighlights + los + selectedLos,
                torsoFacings = torsoFacings,
                validTargetPositions = targetPositions,
                selectedTargetPosition = selectedTargetPosition,
            )
        }

        override fun selectedUnit(app: AppState): CombatUnit? = app.gameState.unitById(unitId)

        override fun attackRender(gameState: GameState): AttackRender {
            val attacker = gameState.unitById(unitId)
                ?: return AttackRender(emptyList(), weaponAssignments, primaryTargetId, cursorTargetIndex, cursorWeaponIndex)
            val view = playerView(attacker.owner, gameState)
            return AttackRender(
                targets = view.targetInfos(unitId, torsoFacing),
                weaponAssignments = weaponAssignments,
                primaryTargetId = primaryTargetId,
                cursorTargetIndex = cursorTargetIndex,
                cursorWeaponIndex = cursorWeaponIndex,
            )
        }

        override fun targetStatusUnit(gameState: GameState): PublicUnit? {
            val attacker = gameState.unitById(unitId) ?: return null
            val view = playerView(attacker.owner, gameState)
            val targets = view.targetInfos(unitId, torsoFacing)
            val target = targets.getOrNull(cursorTargetIndex) ?: return null
            return view.publicUnit(target.unitId)
        }

        override fun activePlayerLabel(app: AppState): String? {
            val turnState = app.turnState
            if (turnState.allAttackImpulsesComplete) return null
            return if (turnState.activeAttackPlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
        }

        override fun declaredTargetsRender(
            gameState: GameState,
            turnState: TurnState,
            viewingPlayer: PlayerId,
        ): DeclaredTargetsRender = buildDeclaredTargetsRender(gameState, turnState, viewingPlayer, allDrafts())

        private fun nextAttacker(app: AppState): Transition {
            val turn = app.turnState
            val attackers = turn.selectableAttackUnits(app.gameState)
            val savedDrafts = allDrafts()
            if (attackers.isEmpty()) {
                return Transition(app.copy(phase = SelectingAttacker(attackTurnPhase, savedDrafts)))
            }
            val currentIdx = attackers.indexOfFirst { it.id == unitId }.coerceAtLeast(0)
            val nextIdx = (currentIdx + 1) % attackers.size
            val nextUnit = attackers[nextIdx]
            val newPhase = enterDeclaring(nextUnit, attackTurnPhase, app.viewFor(nextUnit.owner), savedDrafts)
            return Transition(app.copy(phase = newPhase, cursor = nextUnit.position))
        }

        private fun toggleWeapon(app: AppState): Transition {
            val attacker = app.gameState.unitById(unitId) ?: return Transition(app)
            val view = playerView(attacker.owner, app.gameState)
            val targets = view.targetInfos(unitId, torsoFacing)
            if (targets.isEmpty() || cursorTargetIndex >= targets.size) return Transition(app)

            val currentTarget = targets[cursorTargetIndex]
            val weapons = currentTarget.weapons
            if (weapons.isEmpty()) return Transition(app)

            val weapon = weapons[cursorWeaponIndex]
            if (!weapon.available) return Transition(app)

            val targetId = currentTarget.unitId
            val assignedElsewhere = weaponAssignments.any { (otherId, indices) ->
                otherId != targetId && weapon.weaponIndex in indices
            }
            if (assignedElsewhere) return Transition(app)

            val currentAssigned = weaponAssignments[targetId] ?: emptySet()
            val newAssigned = if (weapon.weaponIndex in currentAssigned) {
                currentAssigned - weapon.weaponIndex
            } else {
                currentAssigned + weapon.weaponIndex
            }
            val newAssignments = weaponAssignments + (targetId to newAssigned)
            val newPrimaryTargetId = when {
                newAssigned.isEmpty() && targetId == primaryTargetId ->
                    newAssignments.entries
                        .firstOrNull { (id, indices) -> id != targetId && indices.isNotEmpty() }
                        ?.key

                primaryTargetId == null -> targetId
                else -> primaryTargetId
            }

            return Transition(
                app.copy(
                    phase = copy(weaponAssignments = newAssignments, primaryTargetId = newPrimaryTargetId),
                ),
            )
        }

        private fun twistTorso(app: AppState, clockwise: Boolean): Transition {
            val attacker = app.gameState.unitById(unitId) ?: return Transition(app)
            val legFacing = attacker.facing
            val newTorso = if (clockwise) torsoFacing.rotateClockwise() else torsoFacing.rotateCounterClockwise()
            if (legFacing.turnCostTo(newTorso) > 1) return Transition(app)

            val view = playerView(attacker.owner, app.gameState)
            val newTargetIds = view.validTargets(unitId, newTorso)
            val newTargets = view.targetInfos(unitId, newTorso)

            val newAssignments = weaponAssignments.filterKeys { it in newTargetIds }
            val newPrimary = if (primaryTargetId in newTargetIds) primaryTargetId else null

            val newCursorTargetIdx = if (newTargets.isEmpty()) 0
            else cursorTargetIndex.coerceIn(0, newTargets.size - 1)
            val newCursorWeaponIdx = if (newTargets.isEmpty()) 0
            else {
                val maxWeapon = (newTargets[newCursorTargetIdx].weapons.size - 1).coerceAtLeast(0)
                cursorWeaponIndex.coerceIn(0, maxWeapon)
            }

            return Transition(
                app.copy(
                    phase = copy(
                        torsoFacing = newTorso,
                        cursorTargetIndex = newCursorTargetIdx,
                        cursorWeaponIndex = newCursorWeaponIdx,
                        weaponAssignments = newAssignments,
                        primaryTargetId = newPrimary,
                    ),
                ),
            )
        }

        private fun navigateWeapons(delta: Int, gameState: GameState): Declaring {
            val attacker = gameState.unitById(unitId) ?: return this
            val view = playerView(attacker.owner, gameState)
            val targets = view.targetInfos(unitId, torsoFacing)
            if (targets.isEmpty()) return this

            data class Entry(val targetIdx: Int, val weaponIdx: Int)

            val flat = mutableListOf<Entry>()
            for ((ti, target) in targets.withIndex()) {
                for (wi in target.weapons.indices) {
                    flat.add(Entry(ti, wi))
                }
            }
            if (flat.isEmpty()) return this

            val currentFlatIdx = flat.indexOfFirst {
                it.targetIdx == cursorTargetIndex && it.weaponIdx == cursorWeaponIndex
            }.let { if (it < 0) 0 else it }

            val newFlatIdx = (currentFlatIdx + delta + flat.size) % flat.size
            val newEntry = flat[newFlatIdx]
            return copy(cursorTargetIndex = newEntry.targetIdx, cursorWeaponIndex = newEntry.weaponIdx)
        }

        private fun clickTarget(app: AppState): Transition {
            val attacker = app.gameState.unitById(unitId) ?: return Transition(app)
            val targetUnit = app.gameState.unitAt(app.cursor)
            val view = playerView(attacker.owner, app.gameState)
            val validIds = view.validTargets(unitId, torsoFacing)
            if (targetUnit == null || targetUnit.id !in validIds) return Transition(app)
            val targets = view.targetInfos(unitId, torsoFacing)
            val idx = targets.indexOfFirst { it.unitId == targetUnit.id }
            return if (idx >= 0) {
                Transition(app.copy(phase = copy(cursorTargetIndex = idx, cursorWeaponIndex = 0)))
            } else {
                Transition(app)
            }
        }
    }
}

internal fun enterDeclaring(
    unit: CombatUnit,
    attackTurnPhase: TurnPhase,
    view: PlayerView,
    drafts: Map<UnitId, UnitDeclaration> = emptyMap(),
): AttackPhase.Declaring {
    val existingDecl = drafts[unit.id]
    val torsoFacing = existingDecl?.torsoFacing ?: unit.torsoFacing
    val targets = view.targetInfos(unit.id, torsoFacing)

    val (weaponAssignments, primaryTargetId) = if (existingDecl != null && existingDecl.torsoFacing == torsoFacing) {
        existingDecl.weaponAssignments to existingDecl.primaryTargetId
    } else {
        emptyMap<UnitId, Set<Int>>() to null
    }

    val (firstTargetIdx, firstWeaponIdx) = firstCursorPosition(targets)

    // Drop this unit's slot from the carried-over drafts so it's not
    // duplicated alongside the live editing state.
    val carriedDrafts = drafts - unit.id

    return AttackPhase.Declaring(
        attackTurnPhase = attackTurnPhase,
        unitId = unit.id,
        torsoFacing = torsoFacing,
        cursorTargetIndex = firstTargetIdx,
        cursorWeaponIndex = firstWeaponIdx,
        weaponAssignments = weaponAssignments,
        primaryTargetId = primaryTargetId,
        drafts = carriedDrafts,
    )
}

private fun firstCursorPosition(targets: List<TargetInfo>): Pair<Int, Int> {
    for ((ti, target) in targets.withIndex()) {
        val wi = target.weapons.indexOfFirst { it.available }
        if (wi >= 0) return ti to wi
        if (target.weapons.isNotEmpty()) return ti to 0
    }
    return 0 to 0
}

/**
 * Submit the impulse to the session and re-sync the TUI phase to whatever
 * phase the session settled at after the cascade. Domain events emitted by
 * the cascade are recorded in `session.gameLog` and rendered by the LOG panel.
 */
internal fun commitAttackImpulse(
    app: AppState,
    attackTurnPhase: TurnPhase,
    drafts: Map<UnitId, UnitDeclaration>,
): Transition {
    val turnState = app.turnState
    // Guard: nothing to commit if the sequence hasn't been seeded.
    if (turnState.attackSequence.order.isEmpty()) return Transition(app)

    val activePlayer = turnState.activeAttackPlayer
    val torsoFacings: Map<UnitId, HexDirection> = drafts.values.associate { it.unitId to it.torsoFacing }
    val declarations = drafts.values.flatMap { decl ->
        decl.weaponAssignments.flatMap { (targetId, weaponIndices) ->
            weaponIndices.map { weaponIndex ->
                AttackDeclaration(
                    attackerId = decl.unitId,
                    targetId = targetId,
                    weaponIndex = weaponIndex,
                    isPrimary = targetId == decl.primaryTargetId,
                )
            }
        }
    }

    val result = app.session.submitCommand(
        CommitAttackImpulse(
            playerId = activePlayer,
            declarations = declarations,
            torsoFacings = torsoFacings,
        ),
    )
    val accepted = result as? CommandResult.Accepted
    val resolvedResults = accepted?.events?.filterIsInstance<AttacksResolved>()?.firstOrNull()?.results
    val newPhase = mapToTuiPhase(app.session.currentPhase)
    val isNewWeaponAttackPhase = newPhase.turnPhase == TurnPhase.WEAPON_ATTACK
    val updatedApp = app.copy(
        phase = newPhase,
        lastAttackResults = when {
            resolvedResults != null -> resolvedResults
            isNewWeaponAttackPhase -> null
            else -> app.lastAttackResults
        },
    )
    return Transition(updatedApp)
}

private fun playerView(player: PlayerId, gameState: GameState): PlayerView =
    battletech.tactical.query.DefaultPlayerView(player, gameState)

internal fun buildDeclaredTargetsRender(
    gameState: GameState,
    turnState: TurnState,
    viewingPlayer: PlayerId,
    drafts: Map<UnitId, UnitDeclaration>,
): DeclaredTargetsRender {
    val playerOrder: List<PlayerId> = if (turnState.attackSequence.order.isNotEmpty()) {
        turnState.attackSequence.order.map { it.player }.distinct()
    } else {
        listOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2)
    }

    val committedByAttacker: Map<UnitId, List<AttackDeclaration>> =
        turnState.attackDeclarations.groupBy { it.attackerId }

    val activeDrafts: Map<UnitId, UnitDeclaration> = drafts.filter { (unitId, decl) ->
        val unit = gameState.unitById(unitId) ?: return@filter false
        unit.owner == viewingPlayer && decl.weaponAssignments.values.any { it.isNotEmpty() }
    }

    val entries = mutableListOf<DeclaredAttackerEntry>()

    for (player in playerOrder) {
        val committedAttackerIds = committedByAttacker.keys
            .filter { id -> gameState.unitById(id)?.owner == player }
            .sortedBy { it.value }

        for (attackerId in committedAttackerIds) {
            val attackerUnit = gameState.unitById(attackerId) ?: continue
            val declarations = committedByAttacker[attackerId] ?: continue
            val view = DefaultPlayerView(attackerUnit.owner, gameState)
            val targetInfos = view.targetInfos(attackerId, attackerUnit.torsoFacing)

            val byTarget = declarations.groupBy { it.targetId }
            val targetEntries = byTarget.entries.map { (targetId, decls) ->
                val targetName = gameState.unitById(targetId)?.name ?: targetId.value
                val isPrimary = decls.any { it.isPrimary }
                val weaponEntries = decls.sortedBy { it.weaponIndex }.map { decl ->
                    resolveWeaponEntry(attackerUnit, decl.weaponIndex, targetId, targetInfos)
                }
                DeclaredTargetEntry(targetName = targetName, isPrimary = isPrimary, weapons = weaponEntries)
            }

            entries += DeclaredAttackerEntry(
                attackerName = attackerUnit.name,
                ownerPlayer = player,
                isDraft = false,
                targets = targetEntries,
            )
        }

        if (player == viewingPlayer) {
            val draftAttackerIds = activeDrafts.keys
                .filter { id -> gameState.unitById(id)?.owner == player }
                .sortedBy { it.value }

            for (attackerId in draftAttackerIds) {
                val decl = activeDrafts[attackerId] ?: continue
                val attackerUnit = gameState.unitById(attackerId) ?: continue
                val view = DefaultPlayerView(attackerUnit.owner, gameState)
                val targetInfos = view.targetInfos(attackerId, decl.torsoFacing)

                val targetEntries = decl.weaponAssignments.entries
                    .filter { (_, weapons) -> weapons.isNotEmpty() }
                    .map { (targetId, weaponIndices) ->
                        val targetName = gameState.unitById(targetId)?.name ?: targetId.value
                        val isPrimary = decl.primaryTargetId == targetId
                        val weaponEntries = weaponIndices.sorted().map { weaponIndex ->
                            resolveWeaponEntry(attackerUnit, weaponIndex, targetId, targetInfos)
                        }
                        DeclaredTargetEntry(targetName = targetName, isPrimary = isPrimary, weapons = weaponEntries)
                    }

                entries += DeclaredAttackerEntry(
                    attackerName = attackerUnit.name,
                    ownerPlayer = player,
                    isDraft = true,
                    targets = targetEntries,
                )
            }
        }
    }

    return DeclaredTargetsRender(entries = entries)
}

private fun resolveWeaponEntry(
    attackerUnit: battletech.tactical.unit.CombatUnit,
    weaponIndex: Int,
    targetId: UnitId,
    targetInfos: List<battletech.tactical.attack.weapon.TargetInfo>,
): DeclaredWeaponEntry {
    val weaponName = attackerUnit.weapons.getOrNull(weaponIndex)?.name ?: "Unknown"
    val chance = targetInfos
        .firstOrNull { it.unitId == targetId }
        ?.weapons
        ?.firstOrNull { it.weaponIndex == weaponIndex }
        ?.successChance ?: 0
    return DeclaredWeaponEntry(weaponName = weaponName, successChance = chance)
}
