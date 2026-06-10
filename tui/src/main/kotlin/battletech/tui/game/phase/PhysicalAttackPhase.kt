package battletech.tui.game.phase

import battletech.tactical.attack.physical.PhysicalAttackDeclaration
import battletech.tactical.attack.physical.PhysicalAttackKind
import battletech.tactical.attack.physical.physicalImpulseViolation
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.model.GameState
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.query.PhysicalAttackOption
import battletech.tactical.query.PlayerView
import battletech.tactical.session.CommitPhysicalAttackImpulse
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.PanelId
import battletech.tui.game.attackPlayerLabel
import battletech.tui.game.displayName
import battletech.tui.game.mapToTuiPhase
import battletech.tui.input.AttackAction
import battletech.tui.input.IdleAction
import battletech.tui.input.InputMapper
import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent

internal const val PHYSICAL_DECLARING_PROMPT =
    "↑/↓ navigate | Space: toggle punch/kick | Tab: next attacker | 'c': commit"

/** Chosen physical attacks per attacker: target id -> set of attack kinds. */
internal typealias PhysicalDrafts = Map<UnitId, Map<UnitId, Set<PhysicalAttackKind>>>

internal sealed interface PhysicalAttackPhase : Phase {
    override val turnPhase: TurnPhase get() = TurnPhase.PHYSICAL_ATTACK

    val drafts: PhysicalDrafts

    override fun visiblePanels(gameState: GameState): Set<PanelId> = buildSet {
        // Physical attacks reuse the TARGETS panel (Declaring populates it) but
        // never the declared-targets column. The freed width goes to the map.
        if (attackRender(gameState)?.targets?.isNotEmpty() == true) add(PanelId.TARGETS)
    }

    data class SelectingAttacker(
        override val drafts: PhysicalDrafts = emptyMap(),
    ) : PhysicalAttackPhase {

        override fun handle(event: InputEvent, app: AppState): Transition? {
            val action = mapIdleInput(event) ?: return null

            return when (action) {
                is IdleAction.MoveCursor -> handleCursorMove(app, action)
                is IdleAction.ClickHex -> trySelect(app.copy(cursor = action.coords))
                is IdleAction.SelectUnit -> trySelect(app)
                is IdleAction.CycleUnit -> cycleSelectable(app, app.turnState.selectableAttackUnits(app.gameState))
                is IdleAction.CommitDeclarations -> commitPhysicalImpulse(app, drafts)
            }
        }

        override fun prompt(app: AppState): String {
            val turnState = app.turnState
            if (turnState.attack.isComplete) return "All physical attacks declared"
            val name = turnState.attack.activePlayer.displayName
            return "$name: select a unit to punch/kick | 'c' to commit"
        }

        override fun selectedUnit(app: AppState): CombatUnit? = app.gameState.unitAt(app.cursor)

        override fun activePlayerLabel(app: AppState): String? = attackPlayerLabel(app.turnState)

        private fun trySelect(app: AppState): Transition =
            selectOwnUnit(
                app = app,
                activePlayer = app.turnState.attack.activePlayer,
                onSelect = { unit ->
                    Transition(app.copy(phase = enterPhysicalDeclaring(unit.id, app, drafts)))
                },
            )
    }

    public data class Declaring(
        val unitId: UnitId,
        val cursorIndex: Int,
        val assignments: Map<UnitId, Set<PhysicalAttackKind>>,
        override val drafts: PhysicalDrafts = emptyMap(),
    ) : PhysicalAttackPhase {

        override fun handle(event: InputEvent, app: AppState): Transition? {
            val action = (event as? KeyboardEvent)?.let { InputMapper.mapAttackEvent(it) } ?: return null
            return when (action) {
                is AttackAction.NavigateWeapons -> Transition(app.copy(phase = navigate(action.delta, app)))
                is AttackAction.ToggleWeapon -> toggle(app)
                is AttackAction.NextAttacker -> nextAttacker(app)
                is AttackAction.Commit -> commitPhysicalImpulse(app, allDrafts())
                is AttackAction.Cancel -> Transition(app.copy(phase = SelectingAttacker(allDrafts())))
                is AttackAction.TwistTorso -> Transition(app) // physical attacks don't twist
                is AttackAction.ClickTarget -> Transition(app)
            }
        }

        override fun prompt(app: AppState): String = PHYSICAL_DECLARING_PROMPT

        override fun selectedUnit(app: AppState): CombatUnit? = app.gameState.unitById(unitId)

        override fun activePlayerLabel(app: AppState): String? = attackPlayerLabel(app.turnState, requireSeeded = false)

        override fun attackRender(gameState: GameState): AttackRender {
            val options = optionsFor(gameState)
            val byTarget = options.groupBy { it.targetId }
            val targets = byTarget.map { (targetId, opts) ->
                TargetInfo(
                    unitId = targetId,
                    unitName = opts.first().targetName,
                    weapons = opts.mapIndexed { index, option ->
                        WeaponTargetInfo(
                            weaponIndex = index,
                            weaponName = option.label,
                            successChance = option.successChance,
                            damage = option.expectedDamage,
                            modifiers = emptyList(),
                            available = option.available,
                        )
                    },
                )
            }
            val weaponAssignments = targets.associate { target ->
                val opts = byTarget.getValue(target.unitId)
                val chosenKinds = assignments[target.unitId] ?: emptySet()
                target.unitId to opts.mapIndexedNotNull { i, o -> i.takeIf { o.kind in chosenKinds } }.toSet()
            }
            val (cursorTarget, cursorOption) = cursorPosition(options)
            return AttackRender(
                targets = targets,
                weaponAssignments = weaponAssignments,
                primaryTargetId = null,
                cursorTargetIndex = cursorTarget,
                cursorWeaponIndex = cursorOption,
            )
        }

        internal fun allDrafts(): PhysicalDrafts =
            if (assignments.values.any { it.isNotEmpty() }) drafts + (unitId to assignments) else drafts - unitId

        private fun optionsFor(gameState: GameState): List<PhysicalAttackOption> {
            val owner = gameState.unitById(unitId)?.owner ?: return emptyList()
            return view(owner, gameState).physicalAttackOptions(unitId)
        }

        private fun cursorPosition(options: List<PhysicalAttackOption>): Pair<Int, Int> {
            if (options.isEmpty()) return 0 to 0
            val byTarget = options.groupBy { it.targetId }.values.toList()
            var remaining = cursorIndex.coerceIn(0, options.size - 1)
            byTarget.forEachIndexed { ti, opts ->
                if (remaining < opts.size) return ti to remaining
                remaining -= opts.size
            }
            return 0 to 0
        }

        private fun navigate(delta: Int, app: AppState): Declaring {
            val options = optionsFor(app.gameState)
            if (options.isEmpty()) return this
            val next = (cursorIndex + delta + options.size) % options.size
            return copy(cursorIndex = next)
        }

        private fun toggle(app: AppState): Transition {
            val options = optionsFor(app.gameState)
            val option = options.getOrNull(cursorIndex) ?: return Transition(app)
            if (!option.available) return Transition(app, FlashMessage("Not available"))

            val current = assignments[option.targetId] ?: emptySet()
            val newSet = if (option.kind in current) current - option.kind else current + option.kind
            val candidate = assignments + (option.targetId to newSet)

            // Enforce per-turn limits (punch XOR kick, one kick, no limb reuse).
            val declarations = candidate.flatMap { (targetId, kinds) ->
                kinds.map { PhysicalAttackDeclaration(unitId, targetId, it) }
            }
            physicalImpulseViolation(declarations, app.gameState)?.let {
                return Transition(app, FlashMessage("Illegal combination"))
            }
            return Transition(app.copy(phase = copy(assignments = candidate)))
        }

        private fun nextAttacker(app: AppState): Transition {
            val attackers = app.turnState.selectableAttackUnits(app.gameState)
            val saved = allDrafts()
            if (attackers.isEmpty()) return Transition(app.copy(phase = SelectingAttacker(saved)))
            val idx = attackers.indexOfFirst { it.id == unitId }.coerceAtLeast(0)
            val next = attackers[(idx + 1) % attackers.size]
            return Transition(app.copy(phase = enterPhysicalDeclaring(next.id, app, saved), cursor = next.position))
        }
    }
}

internal fun enterPhysicalDeclaring(unitId: UnitId, app: AppState, drafts: PhysicalDrafts): PhysicalAttackPhase.Declaring =
    PhysicalAttackPhase.Declaring(
        unitId = unitId,
        cursorIndex = 0,
        assignments = drafts[unitId] ?: emptyMap(),
        drafts = drafts - unitId,
    )

internal fun commitPhysicalImpulse(app: AppState, drafts: PhysicalDrafts): Transition {
    val turnState = app.turnState
    if (turnState.attack.sequence.order.isEmpty()) return Transition(app)

    val declarations = drafts.flatMap { (attackerId, byTarget) ->
        byTarget.flatMap { (targetId, kinds) -> kinds.map { PhysicalAttackDeclaration(attackerId, targetId, it) } }
    }
    app.session.submitCommand(
        CommitPhysicalAttackImpulse(
            playerId = turnState.attack.activePlayer,
            declarations = declarations,
            torsoFacings = emptyMap(),
        ),
    )
    return Transition(app.copy(phase = mapToTuiPhase(app.session.currentPhase)))
}

private fun view(player: PlayerId, gameState: GameState): PlayerView =
    battletech.tactical.query.DefaultPlayerView(player, gameState)
