package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.CombatUnit
import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.action.UnitId
import battletech.tactical.action.attack.AttackDeclaration
import battletech.tactical.model.FiringArc
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tui.input.InputAction
import kotlin.math.ceil

public class AttackController(
    private val actionQueryService: ActionQueryService,
) {
    private val allDeclarations: MutableList<AttackDeclaration> = mutableListOf()
    private var currentImpulse: ImpulseDeclarations? = null

    public fun initializeImpulse(playerId: PlayerId, unitCount: Int) {
        currentImpulse = ImpulseDeclarations(playerId, unitCount)
    }

    public fun isDeclared(unitId: UnitId): Boolean =
        currentImpulse?.isDeclared(unitId) == true

    public fun declaredCount(): Int =
        currentImpulse?.declarations?.values?.count { it.status != DeclarationStatus.PENDING } ?: 0

    public fun currentImpulseUnitCount(): Int = currentImpulse?.unitCount ?: 0

    public fun canCommit(): Boolean = currentImpulse?.allDeclared() == true

    /** Commits the current impulse's declarations into the accumulated list and returns committed unit IDs. */
    public fun commitImpulse(): Set<UnitId> {
        val impulse = currentImpulse ?: return emptySet()
        val unitIds = impulse.declarations.keys.toSet()
        allDeclarations.addAll(impulse.toAttackDeclarations())
        currentImpulse = null
        return unitIds
    }

    public fun collectDeclarations(): List<AttackDeclaration> = allDeclarations.toList()

    public fun clearDeclarations() {
        allDeclarations.clear()
    }

    public fun enter(unit: CombatUnit, phase: TurnPhase, gameState: GameState): PhaseState.Attack {
        val existingDecl = currentImpulse?.declarations?.get(unit.id)
        val torsoFacing = existingDecl?.torsoFacing ?: unit.facing
        val arc = FiringArc.forwardArc(unit.position, torsoFacing, gameState.map)
        val validTargetIds = findValidTargets(unit, arc, gameState)
        val targets = buildTargetInfoList(unit, validTargetIds, gameState)

        val (weaponAssignments, primaryTargetId) = if (existingDecl != null && existingDecl.torsoFacing == torsoFacing) {
            existingDecl.weaponAssignments to existingDecl.primaryTargetId
        } else {
            emptyMap<UnitId, Set<Int>>() to null
        }

        val (firstTargetIdx, firstWeaponIdx) = firstCursorPosition(targets)

        return PhaseState.Attack(
            unitId = unit.id,
            attackPhase = phase,
            torsoFacing = torsoFacing,
            arc = arc,
            validTargetIds = validTargetIds,
            targets = targets,
            cursorTargetIndex = firstTargetIdx,
            cursorWeaponIndex = firstWeaponIdx,
            weaponAssignments = weaponAssignments,
            primaryTargetId = primaryTargetId,
            prompt = DECLARING_PROMPT,
        )
    }

    public fun handle(
        action: InputAction,
        state: PhaseState.Attack,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is InputAction.Cancel -> {
            // Discard assignments — remove any pending declaration for this unit
            currentImpulse?.declarations?.remove(state.unitId)
            PhaseOutcome.Cancelled
        }

        is InputAction.Confirm -> {
            // Save assignments and return to Idle
            val hasAssignments = state.weaponAssignments.values.any { it.isNotEmpty() }
            val status = if (hasAssignments) DeclarationStatus.WEAPONS_ASSIGNED else DeclarationStatus.NO_ATTACK
            currentImpulse?.declarations?.put(
                state.unitId,
                UnitDeclaration(
                    unitId = state.unitId,
                    torsoFacing = state.torsoFacing,
                    status = status,
                    primaryTargetId = state.primaryTargetId,
                    weaponAssignments = state.weaponAssignments,
                ),
            )
            PhaseOutcome.Cancelled
        }

        is InputAction.ToggleWeapon -> toggleWeapon(state)

        is InputAction.MoveCursor -> {
            val dir = action.direction
            val attacker = gameState.unitById(state.unitId) ?: return PhaseOutcome.Continue(state)
            when {
                // Left/Right: twist torso
                dir == HexDirection.NE || dir == HexDirection.SE ->
                    PhaseOutcome.Continue(twistTorso(state, attacker, clockwise = true, gameState))
                dir == HexDirection.NW || dir == HexDirection.SW ->
                    PhaseOutcome.Continue(twistTorso(state, attacker, clockwise = false, gameState))
                // Up/Down: navigate weapons
                dir == HexDirection.N || dir == HexDirection.S ->
                    PhaseOutcome.Continue(navigateWeapons(state, if (dir == HexDirection.S) 1 else -1))
                else -> PhaseOutcome.Continue(state)
            }
        }

        is InputAction.CycleUnit -> {
            // Tab: jump to first weapon of next target (round-robin, no "No Attack" sentinel)
            if (state.targets.isEmpty()) return PhaseOutcome.Continue(state)
            val nextTargetIdx = (state.cursorTargetIndex + 1) % state.targets.size
            PhaseOutcome.Continue(state.copy(cursorTargetIndex = nextTargetIdx, cursorWeaponIndex = 0))
        }

        is InputAction.ClickHex -> {
            val targetUnit = gameState.unitAt(cursor)
            if (targetUnit != null && targetUnit.id in state.validTargetIds) {
                val idx = state.targets.indexOfFirst { it.unitId == targetUnit.id }
                if (idx >= 0) {
                    PhaseOutcome.Continue(state.copy(cursorTargetIndex = idx, cursorWeaponIndex = 0))
                } else {
                    PhaseOutcome.Continue(state)
                }
            } else {
                PhaseOutcome.Continue(state)
            }
        }

        else -> PhaseOutcome.Continue(state)
    }

    private fun toggleWeapon(state: PhaseState.Attack): PhaseOutcome {
        if (state.targets.isEmpty() || state.cursorTargetIndex >= state.targets.size) {
            return PhaseOutcome.Continue(state)
        }

        val currentTarget = state.targets[state.cursorTargetIndex]
        val weapons = currentTarget.weapons
        if (weapons.isEmpty()) return PhaseOutcome.Continue(state)

        val weapon = weapons[state.cursorWeaponIndex]
        if (!weapon.available) return PhaseOutcome.Continue(state)

        val targetId = currentTarget.unitId
        val assignedElsewhere = state.weaponAssignments.any { (otherId, indices) ->
            otherId != targetId && weapon.weaponIndex in indices
        }
        if (assignedElsewhere) return PhaseOutcome.Continue(state)

        val currentAssigned = state.weaponAssignments[targetId] ?: emptySet()
        val newAssigned = if (weapon.weaponIndex in currentAssigned) {
            currentAssigned - weapon.weaponIndex
        } else {
            currentAssigned + weapon.weaponIndex
        }
        val newAssignments = state.weaponAssignments + (targetId to newAssigned)
        val newPrimaryTargetId = state.primaryTargetId ?: targetId

        // Update declaration immediately
        val hasAny = newAssignments.values.any { it.isNotEmpty() }
        val status = if (hasAny) DeclarationStatus.WEAPONS_ASSIGNED else DeclarationStatus.PENDING
        currentImpulse?.declarations?.put(
            state.unitId,
            UnitDeclaration(
                unitId = state.unitId,
                torsoFacing = state.torsoFacing,
                status = status,
                primaryTargetId = newPrimaryTargetId,
                weaponAssignments = newAssignments,
            ),
        )

        return PhaseOutcome.Continue(
            state.copy(weaponAssignments = newAssignments, primaryTargetId = newPrimaryTargetId),
        )
    }

    /**
     * Navigate the weapon cursor by [delta] steps (+1 = down, -1 = up) across the flat weapon list.
     *
     * Flat order: all weapons of target 0, all weapons of target 1, ...
     */
    private fun navigateWeapons(state: PhaseState.Attack, delta: Int): PhaseState.Attack {
        if (state.targets.isEmpty()) return state

        data class Entry(val targetIdx: Int, val weaponIdx: Int)

        val flat = mutableListOf<Entry>()
        for ((ti, target) in state.targets.withIndex()) {
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
        state: PhaseState.Attack,
        attacker: CombatUnit,
        clockwise: Boolean,
        gameState: GameState,
    ): PhaseState.Attack {
        val legFacing = attacker.facing
        val newTorso = if (clockwise) state.torsoFacing.rotateClockwise() else state.torsoFacing.rotateCounterClockwise()
        if (legFacing.turnCostTo(newTorso) > 1) return state

        val newArc = FiringArc.forwardArc(attacker.position, newTorso, gameState.map)
        val newTargetIds = findValidTargets(attacker, newArc, gameState)
        val newTargets = buildTargetInfoList(attacker, newTargetIds, gameState)

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

        return state.copy(
            torsoFacing = newTorso,
            arc = newArc,
            validTargetIds = newTargetIds,
            targets = newTargets,
            cursorTargetIndex = newCursorTargetIdx,
            cursorWeaponIndex = newCursorWeaponIdx,
            weaponAssignments = newAssignments,
            primaryTargetId = newPrimary,
        )
    }

    private fun buildTargetInfoList(
        attacker: CombatUnit,
        validTargetIds: Set<UnitId>,
        gameState: GameState,
    ): List<TargetInfo> =
        validTargetIds.mapNotNull { targetId ->
            val target = gameState.unitById(targetId) ?: return@mapNotNull null
            val distance = attacker.position.distanceTo(target.position)

            val weapons = attacker.weapons.mapIndexed { index, weapon ->
                val inRange = !weapon.destroyed &&
                    (weapon.ammo?.let { it > 0 } != false) &&
                    distance <= weapon.longRange

                if (!inRange) {
                    WeaponTargetInfo(
                        weaponIndex = index,
                        weaponName = weapon.name,
                        successChance = 0,
                        damage = weapon.damage,
                        modifiers = emptyList(),
                        available = false,
                    )
                } else {
                    val rangeModifier = when {
                        distance <= weapon.shortRange -> 0
                        distance <= weapon.mediumRange -> 2
                        else -> 4
                    }
                    val heatPenalty = heatPenaltyModifier(attacker)
                    val modifiers = mutableListOf<String>()
                    when {
                        distance <= weapon.shortRange -> {}
                        distance <= weapon.mediumRange -> modifiers.add("+2 med")
                        else -> modifiers.add("+4 long")
                    }
                    if (heatPenalty > 0) modifiers.add("+$heatPenalty heat")

                    val chance = TWO_D6_PROBABILITY[attacker.gunnerySkill + rangeModifier + heatPenalty] ?: 0
                    WeaponTargetInfo(
                        weaponIndex = index,
                        weaponName = weapon.name,
                        successChance = chance,
                        damage = weapon.damage,
                        modifiers = modifiers,
                        available = true,
                    )
                }
            }

            val hasAnyEligible = weapons.any { it.available }
            if (!hasAnyEligible) return@mapNotNull null

            TargetInfo(unitId = targetId, unitName = target.name, weapons = weapons)
        }

    private fun findValidTargets(attacker: CombatUnit, arc: Set<HexCoordinates>, gameState: GameState): Set<UnitId> {
        val enemies = gameState.units.filter { it.owner != attacker.owner }
        return enemies
            .filter { it.position in arc }
            .filter { enemy -> hasEligibleWeapon(attacker, enemy) }
            .map { it.id }
            .toSet()
    }

    private fun hasEligibleWeapon(attacker: CombatUnit, target: CombatUnit): Boolean {
        val distance = attacker.position.distanceTo(target.position)
        return attacker.weapons.any { weapon ->
            !weapon.destroyed &&
                (weapon.ammo?.let { it > 0 } != false) &&
                distance <= weapon.longRange
        }
    }

    private fun heatPenaltyModifier(actor: CombatUnit): Int {
        val excessHeat = actor.currentHeat - actor.heatSinkCapacity
        return if (excessHeat <= 0) 0 else ceil(excessHeat / 3.0).toInt()
    }

    private companion object {
        const val DECLARING_PROMPT = "←/→ twist torso | ↑/↓ navigate weapons | Space: toggle | Enter: confirm | Esc: cancel"

        val TWO_D6_PROBABILITY: Map<Int, Int> = mapOf(
            2 to 100, 3 to 97, 4 to 92, 5 to 83, 6 to 72,
            7 to 58, 8 to 42, 9 to 28, 10 to 17, 11 to 8, 12 to 3,
        )
    }
}
