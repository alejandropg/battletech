package battletech.tui.game.phase

import battletech.tactical.attack.AttackDeclaration
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.heat.weaponHeatSource
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
import battletech.tactical.session.toAttackDeclarations
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tui.game.AppState
import battletech.tui.game.PanelId
import battletech.tui.game.RenderData
import battletech.tui.game.attackPlayerLabel
import battletech.tui.game.displayName
import battletech.tui.game.losHighlights
import battletech.tui.game.mapToTuiPhase
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
            val action = mapIdleInput(event) ?: return null

            return when (action) {
                is IdleAction.MoveCursor -> handleCursorMove(app, action)
                is IdleAction.ClickHex -> trySelect(app.copy(cursor = action.coords))
                is IdleAction.SelectUnit -> trySelect(app)
                is IdleAction.CycleUnit -> cycleSelectable(app, app.turnState.selectableAttackUnits(app.gameState))
                is IdleAction.CommitDeclarations -> commitAttackImpulse(app, attackTurnPhase, drafts)
            }
        }

        override fun prompt(app: AppState): String {
            val turnState = app.turnState
            if (turnState.attack.isComplete) return "All attacks declared"
            val playerName = turnState.attack.activePlayer.displayName
            return "$playerName: select units, toggle weapons | 'c' to commit"
        }

        override fun selectedUnit(app: AppState): CombatUnit? = app.gameState.unitAt(app.cursor)

        override fun activePlayerLabel(app: AppState): String? = attackPlayerLabel(app.turnState)

        override fun declaredTargetsRender(
            gameState: GameState,
            turnState: TurnState,
            viewingPlayer: PlayerId,
        ): DeclaredTargetsRender = buildDeclaredTargetsRender(gameState, turnState, viewingPlayer, drafts)

        private fun trySelect(app: AppState): Transition =
            selectOwnUnit(
                app = app,
                activePlayer = app.turnState.attack.activePlayer,
                onSelect = { unit ->
                    val newPhase = enterDeclaring(unit, attackTurnPhase, app.viewFor(unit.owner), drafts)
                    Transition(app.copy(phase = newPhase))
                },
            )
    }

    public data class Declaring(
        override val attackTurnPhase: TurnPhase,
        val unitId: UnitId,
        val allocation: WeaponAllocation,
        override val drafts: Map<UnitId, UnitDeclaration> = emptyMap(),
    ) : AttackPhase {

        /** Convenience accessors forwarded from [allocation]. */
        val torsoFacing: HexDirection get() = allocation.torsoFacing
        val weaponAssignments: Map<UnitId, Set<Int>> get() = allocation.weaponAssignments
        val primaryTargetId: UnitId? get() = allocation.primaryTargetId
        val cursorTargetIndex: Int get() = allocation.cursorTargetIndex
        val cursorWeaponIndex: Int get() = allocation.cursorWeaponIndex

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
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = BOARD_ORIGIN_X, boardY = BOARD_ORIGIN_Y)
                    ?.let { AttackAction.ClickTarget(it) }
            } ?: return null

            // Compute view + targets once per event; pass into pure allocation methods.
            val attacker = app.gameState.unitById(unitId)
            val view = attacker?.let { playerView(it.owner, app.gameState) }
            val targets = view?.targetInfos(unitId, torsoFacing) ?: emptyList()

            return when (action) {
                is AttackAction.NextAttacker -> nextAttacker(app)
                is AttackAction.Commit -> commitAttackImpulse(app, attackTurnPhase, allDrafts())
                is AttackAction.Cancel -> Transition(
                    app.copy(phase = SelectingAttacker(attackTurnPhase, allDrafts())),
                )
                is AttackAction.ToggleWeapon -> {
                    val newAllocation = allocation.toggle(targets)
                    Transition(app.copy(phase = copy(allocation = newAllocation)))
                }
                is AttackAction.TwistTorso -> {
                    if (attacker == null || view == null) return Transition(app)
                    val legFacing = attacker.facing
                    val newTorso = if (action.clockwise) torsoFacing.rotateClockwise()
                    else torsoFacing.rotateCounterClockwise()
                    if (legFacing.turnCostTo(newTorso) > 1) return Transition(app)
                    val newValidIds = view.validTargets(unitId, newTorso)
                    val newTargets = view.targetInfos(unitId, newTorso)
                    val newAllocation = allocation.twist(newTorso, newTargets, newValidIds)
                    Transition(app.copy(phase = copy(allocation = newAllocation)))
                }
                is AttackAction.NavigateWeapons -> {
                    val newAllocation = allocation.navigate(action.delta, targets)
                    Transition(app.copy(phase = copy(allocation = newAllocation)))
                }
                is AttackAction.ClickTarget -> {
                    if (attacker == null || view == null) return Transition(app)
                    val targetUnit = app.gameState.unitAt(app.cursor)
                    val validIds = view.validTargets(unitId, torsoFacing)
                    if (targetUnit == null || targetUnit.id !in validIds) return Transition(app)
                    val newAllocation = allocation.clickTarget(targetUnit.id, targets)
                    Transition(app.copy(phase = copy(allocation = newAllocation)))
                }
            }
        }

        override fun prompt(app: AppState): String = DECLARING_PROMPT

        override fun render(gameState: GameState): RenderData {
            val attacker = gameState.unitById(unitId) ?: return RenderData.EMPTY
            val view: PlayerView = playerView(attacker.owner, gameState)
            val arc = view.fireArc(unitId, torsoFacing)
            val validIds = view.validTargets(unitId, torsoFacing)
            val targets = targetTable(view)
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

        override fun pendingHeat(app: AppState): List<battletech.tactical.unit.HeatSource> {
            val attacker = app.gameState.unitById(unitId) ?: return emptyList()
            val firedWeaponIndices = weaponAssignments.values.flatten().toSet()
            return firedWeaponIndices.sorted().mapNotNull { index ->
                attacker.weapons.getOrNull(index)?.let(::weaponHeatSource)
            }
        }

        override fun attackRender(gameState: GameState): AttackRender {
            val attacker = gameState.unitById(unitId)
                ?: return AttackRender(emptyList(), weaponAssignments, primaryTargetId, cursorTargetIndex, cursorWeaponIndex)
            val view = playerView(attacker.owner, gameState)
            return AttackRender(
                targets = targetTable(view),
                weaponAssignments = weaponAssignments,
                primaryTargetId = primaryTargetId,
                cursorTargetIndex = cursorTargetIndex,
                cursorWeaponIndex = cursorWeaponIndex,
            )
        }

        override fun targetStatusUnit(gameState: GameState): PublicUnit? {
            val attacker = gameState.unitById(unitId) ?: return null
            val view = playerView(attacker.owner, gameState)
            val targets = targetTable(view)
            val target = targets.getOrNull(cursorTargetIndex) ?: return null
            return view.publicUnit(target.unitId)
        }

        override fun activePlayerLabel(app: AppState): String? = attackPlayerLabel(app.turnState, requireSeeded = false)

        override fun declaredTargetsRender(
            gameState: GameState,
            turnState: TurnState,
            viewingPlayer: PlayerId,
        ): DeclaredTargetsRender = buildDeclaredTargetsRender(gameState, turnState, viewingPlayer, allDrafts())

        /** Query target infos for this attacker's current torso facing — one call per render entry point. */
        private fun targetTable(view: PlayerView): List<battletech.tactical.attack.weapon.TargetInfo> =
            view.targetInfos(unitId, torsoFacing)

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
        allocation = WeaponAllocation(
            torsoFacing = torsoFacing,
            weaponAssignments = weaponAssignments,
            primaryTargetId = primaryTargetId,
            cursorTargetIndex = firstTargetIdx,
            cursorWeaponIndex = firstWeaponIdx,
        ),
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
    if (turnState.attack.sequence.order.isEmpty()) return Transition(app)

    val activePlayer = turnState.attack.activePlayer
    val torsoFacings: Map<UnitId, HexDirection> = drafts.values.associate { it.unitId to it.torsoFacing }
    val declarations = drafts.values.toAttackDeclarations()

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
    val playerOrder: List<PlayerId> = if (turnState.attack.sequence.order.isNotEmpty()) {
        turnState.attack.sequence.order.map { it.player }.distinct()
    } else {
        listOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2)
    }

    val committedByAttacker: Map<UnitId, List<AttackDeclaration>> =
        turnState.attack.weaponDeclarations.groupBy { it.attackerId }

    val activeDrafts: Map<UnitId, UnitDeclaration> = drafts.filter { (unitId, decl) ->
        val unit = gameState.unitById(unitId) ?: return@filter false
        unit.owner == viewingPlayer && decl.weaponAssignments.values.any { it.isNotEmpty() }
    }

    // Normalize both sources to (targetId, sortedWeaponIndices, isPrimary) triples,
    // then delegate to a single helper that builds one DeclaredAttackerEntry.
    val entries = buildList {
        for (player in playerOrder) {
            committedByAttacker.keys
                .filter { id -> gameState.unitById(id)?.owner == player }
                .sortedBy { it.value }
                .forEach { attackerId ->
                    val attackerUnit = gameState.unitById(attackerId) ?: return@forEach
                    val declarations = committedByAttacker[attackerId] ?: return@forEach
                    val normalized = declarations.groupBy { it.targetId }
                        .map { (targetId, decls) ->
                            Triple(targetId, decls.sortedBy { it.weaponIndex }.map { it.weaponIndex }, decls.any { it.isPrimary })
                        }
                    val targetInfos = DefaultPlayerView(attackerUnit.owner, gameState)
                        .targetInfos(attackerId, attackerUnit.torsoFacing)
                    add(attackerEntry(attackerUnit, normalized, isDraft = false, player, targetInfos))
                }

            if (player == viewingPlayer) {
                activeDrafts.keys
                    .filter { id -> gameState.unitById(id)?.owner == player }
                    .sortedBy { it.value }
                    .forEach { attackerId ->
                        val decl = activeDrafts[attackerId] ?: return@forEach
                        val attackerUnit = gameState.unitById(attackerId) ?: return@forEach
                        val normalized = decl.weaponAssignments.entries
                            .filter { (_, weapons) -> weapons.isNotEmpty() }
                            .map { (targetId, weaponIndices) ->
                                Triple(targetId, weaponIndices.sorted(), decl.primaryTargetId == targetId)
                            }
                        val targetInfos = DefaultPlayerView(attackerUnit.owner, gameState)
                            .targetInfos(attackerId, decl.torsoFacing)
                        add(attackerEntry(attackerUnit, normalized, isDraft = true, player, targetInfos))
                    }
            }
        }
    }

    return DeclaredTargetsRender(entries = entries)
}

private fun attackerEntry(
    attackerUnit: CombatUnit,
    targets: List<Triple<UnitId, List<Int>, Boolean>>,
    isDraft: Boolean,
    ownerPlayer: PlayerId,
    targetInfos: List<TargetInfo>,
): DeclaredAttackerEntry {
    val targetEntries = targets.map { (targetId, weaponIndices, isPrimary) ->
        val weaponEntries = weaponIndices.map { weaponIndex ->
            resolveWeaponEntry(attackerUnit, weaponIndex, targetId, targetInfos)
        }
        DeclaredTargetEntry(targetId = targetId, isPrimary = isPrimary, weapons = weaponEntries)
    }
    return DeclaredAttackerEntry(
        attackerId = attackerUnit.id,
        ownerPlayer = ownerPlayer,
        isDraft = isDraft,
        targets = targetEntries,
    )
}

private fun resolveWeaponEntry(
    attackerUnit: battletech.tactical.unit.CombatUnit,
    weaponIndex: Int,
    targetId: UnitId,
    targetInfos: List<battletech.tactical.attack.weapon.TargetInfo>,
): DeclaredWeaponEntry {
    val weaponName = attackerUnit.weapons.getOrNull(weaponIndex)?.name ?: "Unknown"
    val weaponInfo = targetInfos
        .firstOrNull { it.unitId == targetId }
        ?.weapons
        ?.firstOrNull { it.weaponIndex == weaponIndex }
    return DeclaredWeaponEntry(
        weaponName = weaponName,
        successChance = weaponInfo?.successChance ?: 0,
        targetDiceRoll = weaponInfo?.targetDiceRoll ?: 13,
    )
}
