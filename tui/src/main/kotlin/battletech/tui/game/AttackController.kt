package battletech.tui.game

import battletech.tactical.action.CombatUnit
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackDeclaration
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tui.input.AttackAction

public data class CommitResult(
    val torsoFacings: Map<UnitId, HexDirection>,
)

public class AttackController {

    public fun initializeImpulse(turnState: TurnState, playerId: PlayerId): TurnState =
        turnState.copy(attackImpulse = ImpulseDeclarations(playerId))

    public fun commitImpulse(turnState: TurnState): Pair<TurnState, CommitResult> {
        val impulse = turnState.attackImpulse ?: return turnState to CommitResult(emptyMap())
        val torsoFacings = impulse.declarations.values.associate { it.unitId to it.torsoFacing }
        val newTurnState = turnState.copy(
            attackDeclarations = turnState.attackDeclarations + impulse.toAttackDeclarations(),
            attackImpulse = null,
        )
        return newTurnState to CommitResult(torsoFacings)
    }

    public fun collectDeclarations(turnState: TurnState): List<AttackDeclaration> =
        turnState.attackDeclarations

    public fun clearAttackDeclarations(turnState: TurnState): TurnState =
        turnState.copy(attackDeclarations = emptyList())

    public fun enter(
        unit: CombatUnit,
        phase: TurnPhase,
        gameState: GameState,
        turnState: TurnState,
    ): AttackPhaseState {
        val existingDecl = turnState.attackImpulse?.declarations?.get(unit.id)
        val torsoFacing = existingDecl?.torsoFacing ?: unit.torsoFacing
        val targets = targetInfos(unit, torsoFacing, gameState)

        val (weaponAssignments, primaryTargetId) = if (existingDecl != null && existingDecl.torsoFacing == torsoFacing) {
            existingDecl.weaponAssignments to existingDecl.primaryTargetId
        } else {
            emptyMap<UnitId, Set<Int>>() to null
        }

        val (firstTargetIdx, firstWeaponIdx) = firstCursorPosition(targets)

        return AttackPhaseState(
            unitId = unit.id,
            attackPhase = phase,
            torsoFacing = torsoFacing,
            cursorTargetIndex = firstTargetIdx,
            cursorWeaponIndex = firstWeaponIdx,
            weaponAssignments = weaponAssignments,
            primaryTargetId = primaryTargetId,
        )
    }

    public fun handle(
        action: AttackAction,
        state: AttackPhaseState,
        cursor: HexCoordinates,
        gameState: GameState,
        turnState: TurnState,
    ): Pair<PhaseOutcome, TurnState> = when (action) {
        is AttackAction.Cancel -> PhaseOutcome.Cancelled to turnState
        is AttackAction.Confirm -> PhaseOutcome.Cancelled to turnState

        is AttackAction.ToggleWeapon -> toggleWeapon(state, gameState, turnState)

        is AttackAction.TwistTorso -> {
            val attacker = gameState.unitById(state.unitId)
            if (attacker == null) {
                PhaseOutcome.Continue(state) to turnState
            } else {
                twistTorso(state, attacker, action.clockwise, gameState, turnState)
            }
        }

        is AttackAction.NavigateWeapons ->
            PhaseOutcome.Continue(navigateWeapons(state, action.delta, gameState)) to turnState

        is AttackAction.NextAttacker ->
            error("NextAttacker is intercepted in AttackPhaseState.processEvent and must not reach the controller")

        is AttackAction.Commit ->
            error("Commit is intercepted in AttackPhaseState.processEvent and must not reach the controller")

        is AttackAction.ClickTarget -> {
            val attacker = gameState.unitById(state.unitId)
            if (attacker == null) {
                PhaseOutcome.Continue(state) to turnState
            } else {
                val targetUnit = gameState.unitAt(cursor)
                val validIds = validTargets(attacker, state.torsoFacing, gameState)
                val outcome = if (targetUnit != null && targetUnit.id in validIds) {
                    val targets = targetInfos(attacker, state.torsoFacing, gameState)
                    val idx = targets.indexOfFirst { it.unitId == targetUnit.id }
                    if (idx >= 0) {
                        PhaseOutcome.Continue(state.copy(cursorTargetIndex = idx, cursorWeaponIndex = 0))
                    } else {
                        PhaseOutcome.Continue(state)
                    }
                } else {
                    PhaseOutcome.Continue(state)
                }
                outcome to turnState
            }
        }
    }

    private fun toggleWeapon(
        state: AttackPhaseState,
        gameState: GameState,
        turnState: TurnState,
    ): Pair<PhaseOutcome, TurnState> {
        val attacker = gameState.unitById(state.unitId)
            ?: return PhaseOutcome.Continue(state) to turnState
        val targets = targetInfos(attacker, state.torsoFacing, gameState)
        if (targets.isEmpty() || state.cursorTargetIndex >= targets.size) {
            return PhaseOutcome.Continue(state) to turnState
        }

        val currentTarget = targets[state.cursorTargetIndex]
        val weapons = currentTarget.weapons
        if (weapons.isEmpty()) return PhaseOutcome.Continue(state) to turnState

        val weapon = weapons[state.cursorWeaponIndex]
        if (!weapon.available) return PhaseOutcome.Continue(state) to turnState

        val targetId = currentTarget.unitId
        val assignedElsewhere = state.weaponAssignments.any { (otherId, indices) ->
            otherId != targetId && weapon.weaponIndex in indices
        }
        if (assignedElsewhere) return PhaseOutcome.Continue(state) to turnState

        val currentAssigned = state.weaponAssignments[targetId] ?: emptySet()
        val newAssigned = if (weapon.weaponIndex in currentAssigned) {
            currentAssigned - weapon.weaponIndex
        } else {
            currentAssigned + weapon.weaponIndex
        }
        val newAssignments = state.weaponAssignments + (targetId to newAssigned)
        val newPrimaryTargetId = when {
            newAssigned.isEmpty() && targetId == state.primaryTargetId ->
                newAssignments.entries
                    .firstOrNull { (id, indices) -> id != targetId && indices.isNotEmpty() }
                    ?.key
            state.primaryTargetId == null -> targetId
            else -> state.primaryTargetId
        }

        val newState = state.copy(weaponAssignments = newAssignments, primaryTargetId = newPrimaryTargetId)
        val newTurnState = persistDeclaration(newState, turnState)
        return PhaseOutcome.Continue(newState) to newTurnState
    }

    private fun persistDeclaration(state: AttackPhaseState, turnState: TurnState): TurnState {
        val impulse = turnState.attackImpulse ?: return turnState
        val decl = UnitDeclaration(
            unitId = state.unitId,
            torsoFacing = state.torsoFacing,
            primaryTargetId = state.primaryTargetId,
            weaponAssignments = state.weaponAssignments,
        )
        return turnState.copy(attackImpulse = impulse.withDeclaration(decl))
    }

    /**
     * Navigate the weapon cursor by [delta] steps (+1 = down, -1 = up) across the flat weapon list.
     *
     * Flat order: all weapons of target 0, all weapons of target 1, ...
     */
    private fun navigateWeapons(state: AttackPhaseState, delta: Int, gameState: GameState): AttackPhaseState {
        val attacker = gameState.unitById(state.unitId) ?: return state
        val targets = targetInfos(attacker, state.torsoFacing, gameState)
        if (targets.isEmpty()) return state

        data class Entry(val targetIdx: Int, val weaponIdx: Int)

        val flat = mutableListOf<Entry>()
        for ((ti, target) in targets.withIndex()) {
            for (wi in target.weapons.indices) {
                flat.add(Entry(ti, wi))
            }
        }
        if (flat.isEmpty()) return state

        val currentFlatIdx = flat.indexOfFirst {
            it.targetIdx == state.cursorTargetIndex && it.weaponIdx == state.cursorWeaponIndex
        }.let { if (it < 0) 0 else it }

        val newFlatIdx = (currentFlatIdx + delta + flat.size) % flat.size
        val newEntry = flat[newFlatIdx]
        return state.copy(cursorTargetIndex = newEntry.targetIdx, cursorWeaponIndex = newEntry.weaponIdx)
    }

    /** Returns (targetIndex, weaponIndex) for the first available weapon of the first target, or (0, 0) if none. */
    private fun firstCursorPosition(targets: List<TargetInfo>): Pair<Int, Int> {
        for ((ti, target) in targets.withIndex()) {
            val wi = target.weapons.indexOfFirst { it.available }
            if (wi >= 0) return ti to wi
            if (target.weapons.isNotEmpty()) return ti to 0
        }
        return 0 to 0
    }

    private fun twistTorso(
        state: AttackPhaseState,
        attacker: CombatUnit,
        clockwise: Boolean,
        gameState: GameState,
        turnState: TurnState,
    ): Pair<PhaseOutcome, TurnState> {
        val legFacing = attacker.facing
        val newTorso = if (clockwise) state.torsoFacing.rotateClockwise() else state.torsoFacing.rotateCounterClockwise()
        if (legFacing.turnCostTo(newTorso) > 1) return PhaseOutcome.Continue(state) to turnState

        val newTargetIds = validTargets(attacker, newTorso, gameState)
        val newTargets = targetInfos(attacker, newTorso, gameState)

        // Clear assignments for targets that left the arc
        val newAssignments = state.weaponAssignments.filterKeys { it in newTargetIds }
        val newPrimary = if (state.primaryTargetId in newTargetIds) state.primaryTargetId else null

        // Clamp cursor if targets shrunk
        val newCursorTargetIdx = if (newTargets.isEmpty()) 0
        else state.cursorTargetIndex.coerceIn(0, newTargets.size - 1)
        val newCursorWeaponIdx = if (newTargets.isEmpty()) 0
        else {
            val maxWeapon = (newTargets[newCursorTargetIdx].weapons.size - 1).coerceAtLeast(0)
            state.cursorWeaponIndex.coerceIn(0, maxWeapon)
        }

        val newState = state.copy(
            torsoFacing = newTorso,
            cursorTargetIndex = newCursorTargetIdx,
            cursorWeaponIndex = newCursorWeaponIdx,
            weaponAssignments = newAssignments,
            primaryTargetId = newPrimary,
        )
        val newTurnState = persistDeclaration(newState, turnState)
        return PhaseOutcome.Continue(newState) to newTurnState
    }
}
