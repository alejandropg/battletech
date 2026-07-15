package battletech.tui.game.phase

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.heat.weaponHeatSource
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.DeclaredWeaponAttack
import battletech.tactical.query.DeclaredWeaponLine
import battletech.tactical.query.ForeignUnit
import battletech.tactical.query.OwnUnit
import battletech.tactical.query.PlayerView
import battletech.tactical.query.VisibleUnit
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
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

internal const val DECLARING_PROMPT =
    "←/→ twist torso | ↑/↓ navigate weapons | Space: toggle | Esc: back | Tab: next attacker | 'c': commit"

internal sealed interface AttackPhase : Phase {
    public val attackTurnPhase: TurnPhase

    /**
     * In-progress declarations for units other than the one currently being
     * edited (if any). On Tab/Cancel/Commit we fold the current Declaring's
     * state into [drafts] and read it back when re-entering the same unit.
     */
    public val drafts: Map<UnitId, UnitDeclaration>
    override val turnPhase: TurnPhase get() = attackTurnPhase

    override fun visiblePanels(app: AppState): Set<PanelId> = buildSet {
        // The declared-targets panel belongs to the weapon-attack declaration
        // flow only; the physical-attack flow leaves it empty (see
        // PhysicalAttackPhase), so reserving its column there would render as a
        // blank gap. Targets/TargetStatus follow whatever the active sub-phase
        // populates — SelectingAttacker has neither, Declaring has both.
        if (attackTurnPhase == TurnPhase.WEAPON_ATTACK) add(PanelId.DECLARED_TARGETS)
        if (attackRender(app)?.targets?.isNotEmpty() == true) add(PanelId.TARGETS)
        if (targetStatusUnit(app) != null) add(PanelId.TARGET_STATUS)
    }

    public data class SelectingAttacker(
        override val attackTurnPhase: TurnPhase,
        override val drafts: Map<UnitId, UnitDeclaration> = emptyMap(),
    ) : AttackPhase {

        override fun handle(event: InputEvent, app: AppState): Transition? =
            handleUnitSelection(
                event = event,
                app = app,
                activePlayer = { app.turnState.attack.activePlayer },
                selectableUnits = { app.turnState.selectableAttackUnits(app.visibleState) },
                onCommit = { a -> commitAttackImpulse(a, attackTurnPhase, drafts) },
                enterFor = { unit, a ->
                    Transition(a.copy(phase = enterDeclaring(unit, attackTurnPhase, a.viewFor(unit.owner), drafts)))
                },
            )

        override fun prompt(app: AppState): String {
            val turnState = app.turnState
            if (turnState.attack.isComplete) return "All attacks declared"
            val playerName = turnState.attack.activePlayer.displayName
            return "$playerName: select units, toggle weapons | 'c' to commit"
        }

        override fun selectedUnit(app: AppState): VisibleUnit? = app.visibleState.unitAt(app.cursor)

        override fun unitStatus(app: AppState): VisibleUnit? = cursorUnitStatus(app)

        override fun activePlayerLabel(app: AppState): String? = attackPlayerLabel(app.turnState)

        override fun declaredTargetsRender(app: AppState): DeclaredTargetsRender =
            buildDeclaredTargetsRender(app, declaredTargetsViewingPlayer(app.turnState), drafts)
    }

    public data class Declaring(
        override val attackTurnPhase: TurnPhase,
        val unitId: UnitId,
        val allocation: WeaponAllocation,
        override val drafts: Map<UnitId, UnitDeclaration> = emptyMap(),
    ) : AttackPhase, CancelableSubPhase {

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
            val attacker = app.ownUnit(unitId)
            val view = app.viewFor(attacker.owner)
            val targets = view.targetInfos(unitId, torsoFacing)

            return when (action) {
                is AttackAction.NextAttacker -> nextAttacker(app)
                is AttackAction.Commit -> commitAttackImpulse(app, attackTurnPhase, allDrafts())
                is AttackAction.Cancel -> onCancel(app)
                is AttackAction.ToggleWeapon -> {
                    val newAllocation = allocation.toggle(targets)
                    Transition(app.copy(phase = copy(allocation = newAllocation)))
                }
                is AttackAction.TwistTorso -> {
                    val newTorso = if (action.clockwise) torsoFacing.rotateClockwise()
                    else torsoFacing.rotateCounterClockwise()
                    if (newTorso !in view.legalTorsoFacings(unitId)) return Transition(app)
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
                    val targetUnitId = app.visibleState.unitAt(app.cursor)?.id
                    val validIds = view.validTargets(unitId, torsoFacing)
                    if (targetUnitId == null || targetUnitId !in validIds) return Transition(app)
                    val newAllocation = allocation.clickTarget(targetUnitId, targets)
                    Transition(app.copy(phase = copy(allocation = newAllocation)))
                }
            }
        }

        override fun prompt(app: AppState): String = DECLARING_PROMPT

        override fun render(app: AppState): RenderData {
            val visibleState = app.visibleState
            val attacker = app.ownUnit(unitId)
            val view: PlayerView = app.viewFor(attacker.owner)
            val arc = view.fireArc(unitId, torsoFacing)
            val validIds = view.validTargets(unitId, torsoFacing)
            val targets = targetTable(view)
            val arcHighlights = arc.associateWith { HexHighlight.ATTACK_RANGE }
            val torsoFacings = mapOf(attacker.position to torsoFacing)
            val targetPositions = view.resolveTargetPositions(validIds)
            val selectedTargetPosition = targets.getOrNull(cursorTargetIndex)
                ?.let { visibleState.unitById(it.unitId).position }
            val los = losHighlights(attacker.position, targetPositions, visibleState.map)
            val selectedLos = selectedTargetPosition
                ?.let { selectedLosHighlights(attacker.position, it, visibleState.map) }
                ?: emptyMap()
            return RenderData(
                hexHighlights = arcHighlights + los + selectedLos,
                torsoFacings = torsoFacings,
                validTargetPositions = targetPositions,
                selectedTargetPosition = selectedTargetPosition,
            )
        }

        override fun selectedUnit(app: AppState): VisibleUnit? = app.visibleState.unitById(unitId)

        override fun onCancel(app: AppState): Transition =
            Transition(app.copy(phase = SelectingAttacker(attackTurnPhase, allDrafts())))

        override fun pendingHeat(app: AppState): List<battletech.tactical.unit.HeatSource> {
            val attacker = app.ownUnit(unitId)
            val firedWeaponIndices = weaponAssignments.values.flatten().toSet()
            return firedWeaponIndices.sorted().mapNotNull { index ->
                attacker.weapons.getOrNull(index)?.let(::weaponHeatSource)
            }
        }

        override fun attackRender(app: AppState): AttackRender {
            val owner = app.visibleState.unitById(unitId).owner
            val view = app.viewFor(owner)
            return AttackRender(
                targets = targetTable(view),
                weaponAssignments = weaponAssignments,
                primaryTargetId = primaryTargetId,
                cursorTargetIndex = cursorTargetIndex,
                cursorWeaponIndex = cursorWeaponIndex,
            )
        }

        override fun targetStatusUnit(app: AppState): ForeignUnit? {
            val owner = app.visibleState.unitById(unitId).owner
            val view = app.viewFor(owner)
            val targets = targetTable(view)
            val target = targets.getOrNull(cursorTargetIndex) ?: return null
            return app.visibleState.findUnit(target.unitId) as? ForeignUnit
        }

        override fun activePlayerLabel(app: AppState): String? = attackPlayerLabel(app.turnState, requireSeeded = false)

        override fun declaredTargetsRender(app: AppState): DeclaredTargetsRender =
            buildDeclaredTargetsRender(app, declaredTargetsViewingPlayer(app.turnState), allDrafts())

        /** Query target infos for this attacker's current torso facing — one call per render entry point. */
        private fun targetTable(view: PlayerView): List<battletech.tactical.attack.weapon.TargetInfo> =
            view.targetInfos(unitId, torsoFacing)

        private fun nextAttacker(app: AppState): Transition {
            val turn = app.turnState
            val attackers = turn.selectableAttackUnits(app.visibleState)
            val savedDrafts = allDrafts()
            if (attackers.isEmpty()) {
                return Transition(app.copy(phase = SelectingAttacker(attackTurnPhase, savedDrafts)))
            }
            val currentIdx = attackers.indexOfFirst { it.id == unitId }.coerceAtLeast(0)
            val nextIdx = (currentIdx + 1) % attackers.size
            val nextUnit = attackers[nextIdx]
            val nextCombatUnit = app.ownUnit(nextUnit.id)
            val newPhase = enterDeclaring(nextCombatUnit, attackTurnPhase, app.viewFor(nextUnit.owner), savedDrafts)
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
    return Transition(updatedApp, flash = rejectionFlash(result))
}

/**
 * The player whose in-progress drafts [buildDeclaredTargetsRender] should mix in alongside
 * committed declarations: the active attacker once the impulse sequence is seeded and still
 * running, else a stable default (PLAYER_1) so the panel renders sensibly before the sequence
 * seeds or after all attacks are declared.
 */
internal fun declaredTargetsViewingPlayer(turnState: TurnState): PlayerId =
    if (turnState.attack.sequence.order.isEmpty() || turnState.attack.isComplete) {
        PlayerId.PLAYER_1
    } else {
        turnState.attack.activePlayer
    }

/**
 * Committed entries come from [PlayerView.declaredWeaponAttacks] — the same server-authoritative
 * projection any client would see. The viewing player's own in-progress (uncommitted) [drafts]
 * are folded in locally: they don't exist server-side until commit (see [WeaponAllocation]'s
 * KDoc for why that stays a client-side concern).
 *
 * Scoped to [viewingPlayer] via [battletech.tactical.session.GameSession.stateFor] directly —
 * deliberately NOT [AppState.visibleState]/[AppState.ownUnit], since [viewingPlayer] (the
 * attacker whose drafts this panel folds in) need not equal [AppState.viewer] for every caller
 * of this function (it does in the live TUI, where the active attacker always renders their own
 * screen, but callers testing this fold logic directly may pass either player's perspective).
 */
internal fun buildDeclaredTargetsRender(
    app: AppState,
    viewingPlayer: PlayerId,
    drafts: Map<UnitId, UnitDeclaration>,
): DeclaredTargetsRender {
    val scopedState = app.session.stateFor(viewingPlayer)
    val turnState = app.turnState
    val playerOrder: List<PlayerId> = if (turnState.attack.sequence.order.isNotEmpty()) {
        turnState.attack.sequence.order.map { it.player }.distinct()
    } else {
        listOf(PlayerId.PLAYER_1, PlayerId.PLAYER_2)
    }

    val committedByAttacker: Map<UnitId, List<DeclaredWeaponAttack>> =
        app.viewFor(viewingPlayer).declaredWeaponAttacks().groupBy { it.attackerId }

    val activeDrafts: Map<UnitId, UnitDeclaration> = drafts.filter { (unitId, decl) ->
        val unit = scopedState.unitById(unitId)
        unit.owner == viewingPlayer && decl.weaponAssignments.values.any { it.isNotEmpty() }
    }

    val entries = buildList {
        for (player in playerOrder) {
            committedByAttacker
                .filter { (id, _) -> scopedState.unitById(id).owner == player }
                .forEach { (attackerId, attacks) ->
                    add(committedAttackerEntry(attackerId, attacks, player))
                }

            if (player == viewingPlayer) {
                activeDrafts.keys
                    .filter { id -> scopedState.unitById(id).owner == player }
                    .sortedBy { it.value }
                    .forEach { attackerId ->
                        val decl = activeDrafts[attackerId] ?: return@forEach
                        // Own by construction: activeDrafts was already filtered to
                        // unit.owner == viewingPlayer above, and scopedState was projected
                        // for that same viewingPlayer.
                        val attackerUnit = (scopedState.unitById(attackerId) as OwnUnit).unit
                        val normalized = decl.weaponAssignments.entries
                            .filter { (_, weapons) -> weapons.isNotEmpty() }
                            .map { (targetId, weaponIndices) ->
                                Triple(targetId, weaponIndices.sorted(), decl.primaryTargetId == targetId)
                            }
                        val targetInfos = app.viewFor(attackerUnit.owner)
                            .targetInfos(attackerId, decl.torsoFacing)
                        add(draftAttackerEntry(attackerUnit, normalized, player, targetInfos))
                    }
            }
        }
    }

    return DeclaredTargetsRender(entries = entries)
}

private fun committedAttackerEntry(
    attackerId: UnitId,
    attacks: List<DeclaredWeaponAttack>,
    ownerPlayer: PlayerId,
): DeclaredAttackerEntry {
    val targetEntries = attacks.map { attack ->
        val weaponEntries = attack.weapons.map { line ->
            when (line) {
                is DeclaredWeaponLine.Detailed -> DeclaredWeaponEntry.Detailed(
                    weaponName = line.weaponName,
                    successChance = line.successChance,
                    targetDiceRoll = line.targetNumber,
                    modifiers = line.modifierLabels,
                )
                // An enemy attacker's to-hit math is not ours to render — and the type no
                // longer carries it. See DeclaredWeaponLine's KDoc.
                is DeclaredWeaponLine.Undisclosed -> DeclaredWeaponEntry.Undisclosed(weaponName = line.weaponName)
            }
        }
        DeclaredTargetEntry(targetId = attack.targetId, isPrimary = attack.isPrimary, weapons = weaponEntries)
    }
    return DeclaredAttackerEntry(
        attackerId = attackerId,
        ownerPlayer = ownerPlayer,
        isDraft = false,
        targets = targetEntries,
    )
}

private fun draftAttackerEntry(
    attackerUnit: CombatUnit,
    targets: List<Triple<UnitId, List<Int>, Boolean>>,
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
        isDraft = true,
        targets = targetEntries,
    )
}

private fun resolveWeaponEntry(
    attackerUnit: CombatUnit,
    weaponIndex: Int,
    targetId: UnitId,
    targetInfos: List<TargetInfo>,
): DeclaredWeaponEntry {
    val weaponName = attackerUnit.weapons.getOrNull(weaponIndex)?.name ?: "Unknown"
    val weaponInfo = targetInfos
        .firstOrNull { it.unitId == targetId }
        ?.weapons
        ?.firstOrNull { it.weaponIndex == weaponIndex }
    // Detailed unconditionally: drafts only ever exist for the viewer's own attacker (the
    // caller filters to unit.owner == viewingPlayer before building one).
    return DeclaredWeaponEntry.Detailed(
        weaponName = weaponName,
        successChance = weaponInfo?.successChance ?: 0,
        targetDiceRoll = weaponInfo?.targetDiceRoll ?: 13,
        modifiers = weaponInfo?.modifiers ?: emptyList(),
    )
}
