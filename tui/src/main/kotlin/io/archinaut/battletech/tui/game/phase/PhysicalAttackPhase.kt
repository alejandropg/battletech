package io.archinaut.battletech.tui.game.phase

import io.archinaut.battletech.tactical.attack.physical.PhysicalAttackDeclaration
import io.archinaut.battletech.tactical.attack.physical.PhysicalAttackKind
import io.archinaut.battletech.tactical.attack.physical.physicalImpulseViolation
import io.archinaut.battletech.tactical.attack.weapon.TargetInfo
import io.archinaut.battletech.tactical.attack.weapon.WeaponTargetInfo
import io.archinaut.battletech.tactical.model.TurnPhase
import io.archinaut.battletech.tactical.query.PhysicalAttackOption
import io.archinaut.battletech.tactical.session.CommitPhysicalAttackImpulse
import io.archinaut.battletech.tactical.unit.UnitId
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.battletech.tui.game.attackPlayer
import io.archinaut.battletech.tui.game.mapToTuiPhase
import io.archinaut.battletech.tui.input.AttackAction
import io.archinaut.battletech.tui.input.BoardClick
import io.archinaut.battletech.tui.input.ContextId
import io.archinaut.battletech.tui.input.IdleAction
import io.archinaut.tenter.input.InputAction
import io.archinaut.tenter.view.FlashMessage

internal const val PHYSICAL_DECLARING_PROMPT = "Declare punch/kick"

/** Chosen physical attacks per attacker: target id -> set of attack kinds. */
internal typealias PhysicalDrafts = Map<UnitId, Map<UnitId, Set<PhysicalAttackKind>>>

internal sealed interface PhysicalAttackPhase : Phase {
    override val turnPhase: TurnPhase get() = TurnPhase.PHYSICAL_ATTACK

    val drafts: PhysicalDrafts

    data class SelectingAttacker(
        override val drafts: PhysicalDrafts = emptyMap(),
    ) : PhysicalAttackPhase {

        override val keyContext: ContextId get() = ContextId.PHYSICAL_IDLE

        override fun handle(action: InputAction, app: AppState): Transition? {
            val turnState = app.turnState
            // Mirrors AttackPhase.SelectingAttacker's / MovementPhase.SelectingUnit's guard: the
            // (shared) attack impulse sequence may not be seeded yet, and every other field this
            // phase touches indexes into it.
            if (turnState.attack.isComplete) {
                return if (action is IdleAction.MoveCursor) handleCursorMove(app, action) else Transition(app)
            }
            return handleUnitSelection(
                action = action,
                app = app,
                activePlayer = { app.turnState.attack.activePlayer },
                selectableUnits = { app.turnState.selectableAttackUnits(app.state.units) },
                onCommit = { a -> commitPhysicalImpulse(a, drafts) },
                enterFor = { unit, a -> Transition(a.copy(phase = enterPhysicalDeclaring(unit.id, drafts))) },
            )
        }

        override fun status(app: AppState): PhaseStatus {
            val turnState = app.turnState
            if (turnState.attack.isComplete) return PhaseStatus("All physical attacks declared")
            val activePlayer = turnState.attack.activePlayer
            return PhaseStatus("Select a unit to punch/kick", activePlayer)
        }

        override fun unitStatus(app: AppState): UnitStatusRender = UnitStatusRender(cursorUnitStatus(app))

    }

    public data class Declaring(
        val unitId: UnitId,
        val cursorIndex: Int,
        val assignments: Map<UnitId, Set<PhysicalAttackKind>>,
        override val drafts: PhysicalDrafts = emptyMap(),
    ) : PhysicalAttackPhase, CancelableSubPhase {

        override val keyContext: ContextId get() = ContextId.PHYSICAL_DECLARING

        override fun handle(action: InputAction, app: AppState): Transition? = when (action) {
            is BoardClick -> Transition(app)
            is AttackAction -> when (action) {
                is AttackAction.NavigateWeapons -> Transition(app.copy(phase = navigate(action.delta, app)))
                is AttackAction.ToggleWeapon -> toggle(app)
                is AttackAction.NextAttacker -> nextAttacker(app)
                is AttackAction.Commit -> commitPhysicalImpulse(app, allDrafts())
                is AttackAction.Cancel -> onCancel(app)
                // No chord reaches this in PHYSICAL_DECLARING (see Keybindings' declaringLayer) —
                // kept only so this `when` stays exhaustive over AttackAction.
                is AttackAction.TwistTorso -> Transition(app)
            }
            else -> null
        }

        override fun status(app: AppState): PhaseStatus =
            PhaseStatus(PHYSICAL_DECLARING_PROMPT, attackPlayer(app.turnState, requireSeeded = false), unitId)

        override fun unitStatus(app: AppState): UnitStatusRender = UnitStatusRender(app.state.units.byId(unitId))

        override fun onCancel(app: AppState): Transition = Transition(app.copy(phase = SelectingAttacker(allDrafts())))

        // Physical attacks reuse the TARGETS panel but never the declared-targets column (that
        // belongs to the weapon-attack flow — see AttackPhase.declaredTargetsPanel) or TARGET
        // STATUS (physical attacks have no separate target-cursor concept to show there).
        override fun panels(app: AppState): PhasePanels {
            val render = attackRender(app)
            return PhasePanels(targets = render.takeIf { it.targets.isNotEmpty() })
        }

        /** This attacker's TARGETS-panel content — also the source of [panels]' visibility decision. */
        private fun attackRender(app: AppState): AttackRender {
            val options = optionsFor(app)
            val byTarget = options.groupBy { it.targetId }
            val targets = byTarget.map { (targetId, opts) ->
                TargetInfo(
                    unitId = targetId,
                    unitName = opts.first().targetName,
                    weapons = opts.mapIndexed { index, option ->
                        if (option.available) {
                            WeaponTargetInfo.Available(
                                weaponIndex = index,
                                weaponName = option.label,
                                damage = option.expectedDamage,
                                toHit = option.toHit,
                            )
                        } else {
                            WeaponTargetInfo.Unavailable(
                                weaponIndex = index,
                                weaponName = option.label,
                                damage = option.expectedDamage,
                            )
                        }
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

        private fun allDrafts(): PhysicalDrafts =
            if (assignments.values.any { it.isNotEmpty() }) drafts + (unitId to assignments) else drafts - unitId

        private fun optionsFor(app: AppState): List<PhysicalAttackOption> =
            app.view.physicalAttackOptions(unitId)

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
            val options = optionsFor(app)
            if (options.isEmpty()) return this
            val next = (cursorIndex + delta + options.size) % options.size
            return copy(cursorIndex = next)
        }

        private fun toggle(app: AppState): Transition {
            val options = optionsFor(app)
            val option = options.getOrNull(cursorIndex) ?: return Transition(app)
            if (!option.available) return Transition(app, FlashMessage("Not available"))

            val current = assignments[option.targetId] ?: emptySet()
            val newSet = if (option.kind in current) current - option.kind else current + option.kind
            val candidate = assignments + (option.targetId to newSet)

            // Enforce per-turn limits (punch XOR kick, one kick, no limb reuse).
            val declarations = candidate.flatMap { (targetId, kinds) ->
                kinds.map { PhysicalAttackDeclaration(unitId, targetId, it) }
            }
            physicalImpulseViolation(declarations) { id -> app.ownUnit(id) }?.let {
                return Transition(app, FlashMessage("Illegal combination"))
            }
            return Transition(app.copy(phase = copy(assignments = candidate)))
        }

        private fun nextAttacker(app: AppState): Transition {
            val attackers = app.turnState.selectableAttackUnits(app.state.units)
            val saved = allDrafts()
            if (attackers.isEmpty()) return Transition(app.copy(phase = SelectingAttacker(saved)))
            val idx = attackers.indexOfFirst { it.id == unitId }.coerceAtLeast(0)
            val next = attackers[(idx + 1) % attackers.size]
            return Transition(app.copy(phase = enterPhysicalDeclaring(next.id, saved), cursor = next.position))
        }
    }
}

internal fun enterPhysicalDeclaring(unitId: UnitId, drafts: PhysicalDrafts): PhysicalAttackPhase.Declaring =
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
    val result = app.submitCommand(
        CommitPhysicalAttackImpulse(
            playerId = turnState.attack.activePlayer,
            declarations = declarations,
            torsoFacings = emptyMap(),
        ),
    )
    return Transition(app.copy(phase = mapToTuiPhase(app.anySession.currentPhase)), flash = rejectionFlash(result))
}
