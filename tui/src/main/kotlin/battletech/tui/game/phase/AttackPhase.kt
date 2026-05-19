package battletech.tui.game.phase

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.Impulse
import battletech.tactical.action.Initiative
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.resolveAttacks
import battletech.tactical.action.calculateAttackOrder
import battletech.tactical.model.GameState
import battletech.tactical.model.HexDirection
import battletech.tactical.model.applyTorsoFacings
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.ImpulseDeclarations
import battletech.tui.game.ImpulseSequence
import battletech.tui.game.RenderData
import battletech.tui.game.TargetInfo
import battletech.tui.game.TurnState
import battletech.tui.game.UnitDeclaration
import battletech.tui.game.fireArc
import battletech.tui.game.losHighlights
import battletech.tui.game.moveCursor
import battletech.tui.game.resolveTargetPositions
import battletech.tui.game.selectedLosHighlights
import battletech.tui.game.targetInfos
import battletech.tui.game.validTargets
import battletech.tui.hex.HexHighlight
import battletech.tui.input.AttackAction
import battletech.tui.input.IdleAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent

internal const val DECLARING_PROMPT =
    "←/→ twist torso | ↑/↓ navigate weapons | Space: toggle | Tab: next attacker | 'c': commit"

public sealed interface AttackPhase : Phase {
    public val attackTurnPhase: TurnPhase
    override val turnPhase: TurnPhase get() = attackTurnPhase

    public data class SelectingAttacker(override val attackTurnPhase: TurnPhase) : AttackPhase {

        override fun handle(event: InputEvent, app: AppState, svc: PhaseServices): Transition? {
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
                is IdleAction.CommitDeclarations -> commitAttackImpulse(app, attackTurnPhase, svc)
            }
        }

        override fun tick(app: AppState, svc: PhaseServices): Transition? {
            val turnState = app.turnState
            if (turnState.attackSequence.order.isNotEmpty()) return null

            val seededSequence = ImpulseSequence(attackOrderFor(turnState.initiative, app.gameState))
            val seededTurnState = turnState.copy(
                attackSequence = seededSequence,
                attackImpulse = if (seededSequence.order.isNotEmpty()) {
                    ImpulseDeclarations(seededSequence.activePlayer)
                } else null,
            )
            val flashText =
                if (attackTurnPhase == TurnPhase.WEAPON_ATTACK) "Weapon Attack Phase" else "Physical Attack Phase"

            return enterFirstAttacker(
                app.copy(turnState = seededTurnState),
                attackTurnPhase,
                FlashMessage(flashText),
            )
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

        private fun trySelect(app: AppState): Transition {
            val unit = app.gameState.unitAt(app.cursor) ?: return Transition(app)
            val turnState = app.turnState

            if (unit.owner != turnState.activeAttackPlayer) {
                return Transition(app, FlashMessage("Not your unit"))
            }

            val newPhase = enterDeclaring(unit, attackTurnPhase, app.gameState, turnState)
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
    ) : AttackPhase {

        override fun handle(event: InputEvent, app: AppState, svc: PhaseServices): Transition? {
            val action = when (event) {
                is KeyboardEvent -> InputMapper.mapAttackEvent(event)
                is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                    ?.let { AttackAction.ClickTarget(it) }
            } ?: return null

            return when (action) {
                is AttackAction.NextAttacker -> nextAttacker(app)
                is AttackAction.Commit -> commitAttackImpulse(app, attackTurnPhase, svc)
                is AttackAction.Cancel -> Transition(app.copy(phase = SelectingAttacker(attackTurnPhase)))
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
            val arc = fireArc(attacker, torsoFacing, gameState)
            val validIds = validTargets(attacker, torsoFacing, gameState)
            val targets = targetInfos(attacker, torsoFacing, gameState)
            val arcHighlights = arc.associateWith { HexHighlight.ATTACK_RANGE }
            val torsoFacings = mapOf(attacker.position to torsoFacing)
            val targetPositions = resolveTargetPositions(validIds, gameState)
            val los = losHighlights(attacker, validIds, gameState)
            val selectedLos = selectedLosHighlights(attacker, this, targets, gameState)
            return RenderData(
                hexHighlights = arcHighlights + los + selectedLos,
                torsoFacings = torsoFacings,
                validTargetPositions = targetPositions,
            )
        }

        override fun selectedUnit(app: AppState): CombatUnit? = app.gameState.unitById(unitId)

        override fun attackRender(gameState: GameState): AttackRender {
            val attacker = gameState.unitById(unitId)
                ?: return AttackRender(emptyList(), weaponAssignments, primaryTargetId, cursorTargetIndex, cursorWeaponIndex)
            return AttackRender(
                targets = targetInfos(attacker, torsoFacing, gameState),
                weaponAssignments = weaponAssignments,
                primaryTargetId = primaryTargetId,
                cursorTargetIndex = cursorTargetIndex,
                cursorWeaponIndex = cursorWeaponIndex,
            )
        }

        override fun activePlayerLabel(app: AppState): String? {
            val turnState = app.turnState
            if (turnState.allAttackImpulsesComplete) return null
            return if (turnState.activeAttackPlayer == PlayerId.PLAYER_1) "Player 1" else "Player 2"
        }

        private fun nextAttacker(app: AppState): Transition {
            val turn = app.turnState
            val attackers = turn.selectableAttackUnits(app.gameState)
            if (attackers.isEmpty()) {
                return Transition(app.copy(phase = SelectingAttacker(attackTurnPhase)))
            }
            val currentIdx = attackers.indexOfFirst { it.id == unitId }.coerceAtLeast(0)
            val nextIdx = (currentIdx + 1) % attackers.size
            val nextUnit = attackers[nextIdx]
            val newPhase = enterDeclaring(nextUnit, attackTurnPhase, app.gameState, turn)
            return Transition(app.copy(phase = newPhase, cursor = nextUnit.position))
        }

        private fun toggleWeapon(app: AppState): Transition {
            val turnState = app.turnState
            val attacker = app.gameState.unitById(unitId) ?: return Transition(app)
            val targets = targetInfos(attacker, torsoFacing, app.gameState)
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

            val newState = copy(weaponAssignments = newAssignments, primaryTargetId = newPrimaryTargetId)
            val newTurnState = persistDeclaration(newState, turnState)
            return Transition(app.copy(phase = newState, turnState = newTurnState))
        }

        private fun twistTorso(app: AppState, clockwise: Boolean): Transition {
            val turnState = app.turnState
            val attacker = app.gameState.unitById(unitId) ?: return Transition(app)
            val legFacing = attacker.facing
            val newTorso = if (clockwise) torsoFacing.rotateClockwise() else torsoFacing.rotateCounterClockwise()
            if (legFacing.turnCostTo(newTorso) > 1) return Transition(app)

            val newTargetIds = validTargets(attacker, newTorso, app.gameState)
            val newTargets = targetInfos(attacker, newTorso, app.gameState)

            val newAssignments = weaponAssignments.filterKeys { it in newTargetIds }
            val newPrimary = if (primaryTargetId in newTargetIds) primaryTargetId else null

            val newCursorTargetIdx = if (newTargets.isEmpty()) 0
            else cursorTargetIndex.coerceIn(0, newTargets.size - 1)
            val newCursorWeaponIdx = if (newTargets.isEmpty()) 0
            else {
                val maxWeapon = (newTargets[newCursorTargetIdx].weapons.size - 1).coerceAtLeast(0)
                cursorWeaponIndex.coerceIn(0, maxWeapon)
            }

            val newState = copy(
                torsoFacing = newTorso,
                cursorTargetIndex = newCursorTargetIdx,
                cursorWeaponIndex = newCursorWeaponIdx,
                weaponAssignments = newAssignments,
                primaryTargetId = newPrimary,
            )
            val newTurnState = persistDeclaration(newState, turnState)
            return Transition(app.copy(phase = newState, turnState = newTurnState))
        }

        private fun navigateWeapons(delta: Int, gameState: GameState): Declaring {
            val attacker = gameState.unitById(unitId) ?: return this
            val targets = targetInfos(attacker, torsoFacing, gameState)
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
            val validIds = validTargets(attacker, torsoFacing, app.gameState)
            if (targetUnit == null || targetUnit.id !in validIds) return Transition(app)
            val targets = targetInfos(attacker, torsoFacing, app.gameState)
            val idx = targets.indexOfFirst { it.unitId == targetUnit.id }
            return if (idx >= 0) {
                Transition(app.copy(phase = copy(cursorTargetIndex = idx, cursorWeaponIndex = 0)))
            } else {
                Transition(app)
            }
        }
    }
}

internal fun attackOrderFor(initiative: Initiative, gameState: GameState): List<Impulse> {
    val loser = initiative.loser
    val winner = initiative.winner
    return calculateAttackOrder(
        loser = loser,
        loserUnitCount = gameState.unitsOf(loser).size,
        winner = winner,
        winnerUnitCount = gameState.unitsOf(winner).size,
    )
}

internal fun enterDeclaring(
    unit: CombatUnit,
    attackTurnPhase: TurnPhase,
    gameState: GameState,
    turnState: TurnState,
): AttackPhase.Declaring {
    val existingDecl = turnState.attackImpulse?.declarations?.get(unit.id)
    val torsoFacing = existingDecl?.torsoFacing ?: unit.torsoFacing
    val targets = targetInfos(unit, torsoFacing, gameState)

    val (weaponAssignments, primaryTargetId) = if (existingDecl != null && existingDecl.torsoFacing == torsoFacing) {
        existingDecl.weaponAssignments to existingDecl.primaryTargetId
    } else {
        emptyMap<UnitId, Set<Int>>() to null
    }

    val (firstTargetIdx, firstWeaponIdx) = firstCursorPosition(targets)

    return AttackPhase.Declaring(
        attackTurnPhase = attackTurnPhase,
        unitId = unit.id,
        torsoFacing = torsoFacing,
        cursorTargetIndex = firstTargetIdx,
        cursorWeaponIndex = firstWeaponIdx,
        weaponAssignments = weaponAssignments,
        primaryTargetId = primaryTargetId,
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

private fun persistDeclaration(declaring: AttackPhase.Declaring, turnState: TurnState): TurnState {
    val impulse = turnState.attackImpulse ?: return turnState
    val decl = UnitDeclaration(
        unitId = declaring.unitId,
        torsoFacing = declaring.torsoFacing,
        primaryTargetId = declaring.primaryTargetId,
        weaponAssignments = declaring.weaponAssignments,
    )
    return turnState.copy(attackImpulse = impulse.withDeclaration(decl))
}

/** Enter Declaring on the first available attacker, or stay in SelectingAttacker if none. */
internal fun enterFirstAttacker(
    app: AppState,
    attackTurnPhase: TurnPhase,
    flash: FlashMessage? = null,
): Transition {
    val turnState = app.turnState
    val units = turnState.selectableAttackUnits(app.gameState)
    return if (units.isEmpty()) {
        Transition(app.copy(phase = AttackPhase.SelectingAttacker(attackTurnPhase)), flash)
    } else {
        val first = units.first()
        val newPhase = enterDeclaring(first, attackTurnPhase, app.gameState, turnState)
        Transition(app.copy(phase = newPhase, cursor = first.position), flash)
    }
}

internal fun commitAttackImpulse(app: AppState, attackTurnPhase: TurnPhase, svc: PhaseServices): Transition {
    val turnState = app.turnState

    val impulse = turnState.attackImpulse
    val torsoFacings: Map<UnitId, HexDirection> = impulse?.declarations?.values
        ?.associate { it.unitId to it.torsoFacing }
        ?: emptyMap()
    val newDeclarations = impulse?.toAttackDeclarations().orEmpty()
    val afterCommit = turnState.copy(
        attackDeclarations = turnState.attackDeclarations + newDeclarations,
        attackImpulse = null,
        attackSequence = turnState.attackSequence.advance(),
    )
    val gameStateWithTorso = app.gameState.applyTorsoFacings(torsoFacings)

    return if (afterCommit.allAttackImpulsesComplete) {
        if (attackTurnPhase == TurnPhase.WEAPON_ATTACK) {
            resolveWeaponAttacksAndAdvance(app, gameStateWithTorso, afterCommit, svc)
        } else {
            val nextTurnState = afterCommit.copy(attackDeclarations = emptyList())
            Transition(
                app.copy(
                    gameState = gameStateWithTorso,
                    phase = HeatPhase,
                    turnState = nextTurnState,
                ),
            )
        }
    } else {
        val withNewImpulse = afterCommit.copy(
            attackImpulse = ImpulseDeclarations(afterCommit.activeAttackPlayer),
        )
        enterFirstAttacker(
            app.copy(gameState = gameStateWithTorso, turnState = withNewImpulse),
            attackTurnPhase,
        )
    }
}

private fun resolveWeaponAttacksAndAdvance(
    app: AppState,
    gameStateWithTorso: GameState,
    afterCommit: TurnState,
    svc: PhaseServices,
): Transition {
    val declarations = afterCommit.attackDeclarations
    val (resolvedGameState, flash) = if (declarations.isNotEmpty()) {
        val (resolved, results) = resolveAttacks(declarations, gameStateWithTorso, svc.roller)
        val hitCount = results.count { it.hit }
        val totalDamage = results.sumOf { it.damageApplied }
        resolved to FlashMessage("Attacks resolved: ${results.size} attacks, $hitCount hits, $totalDamage damage")
    } else {
        gameStateWithTorso to null
    }

    val physicalTurnState = afterCommit.copy(
        attackDeclarations = emptyList(),
        attackSequence = ImpulseSequence(emptyList()),
        attackImpulse = null,
    )
    return Transition(
        app.copy(
            gameState = resolvedGameState,
            phase = AttackPhase.SelectingAttacker(TurnPhase.PHYSICAL_ATTACK),
            turnState = physicalTurnState,
        ),
        flash,
    )
}
