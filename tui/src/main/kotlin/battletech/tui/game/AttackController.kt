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

    public fun enter(unit: CombatUnit, phase: TurnPhase, gameState: GameState): PhaseState.Attack.TorsoFacing {
        val existingDecl = currentImpulse?.declarations?.get(unit.id)
        val torsoFacing = existingDecl?.torsoFacing ?: unit.facing
        val arc = FiringArc.forwardArc(unit.position, torsoFacing, gameState.map)
        val validTargetIds = findValidTargets(unit, arc, gameState)
        val targets = buildTargetInfoList(unit, validTargetIds, gameState)

        return PhaseState.Attack.TorsoFacing(
            unitId = unit.id,
            attackPhase = phase,
            torsoFacing = torsoFacing,
            arc = arc,
            validTargetIds = validTargetIds,
            targets = targets,
            prompt = TORSO_FACING_PROMPT,
        )
    }

    public fun handle(
        action: InputAction,
        state: PhaseState.Attack,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (state) {
        is PhaseState.Attack.TorsoFacing -> handleTorsoFacing(action, state, gameState)
        is PhaseState.Attack.TargetBrowsing -> handleTargetBrowsing(action, state, cursor, gameState)
        is PhaseState.Attack.WeaponAssignment -> handleWeaponAssignment(action, state)
    }

    private fun handleTorsoFacing(
        action: InputAction,
        state: PhaseState.Attack.TorsoFacing,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is InputAction.Cancel -> PhaseOutcome.Cancelled

        is InputAction.Confirm -> {
            val existingDecl = currentImpulse?.declarations?.get(state.unitId)
            val (weaponAssignments, primaryTargetId) = if (existingDecl != null && existingDecl.torsoFacing == state.torsoFacing) {
                existingDecl.weaponAssignments to existingDecl.primaryTargetId
            } else {
                emptyMap<UnitId, Set<Int>>() to null
            }

            // Create/update declaration with current torso
            val status = if (weaponAssignments.isNotEmpty()) DeclarationStatus.WEAPONS_ASSIGNED else DeclarationStatus.PENDING
            currentImpulse?.declarations?.put(
                state.unitId,
                UnitDeclaration(
                    unitId = state.unitId,
                    torsoFacing = state.torsoFacing,
                    status = status,
                    primaryTargetId = primaryTargetId,
                    weaponAssignments = weaponAssignments,
                ),
            )

            PhaseOutcome.Continue(
                PhaseState.Attack.TargetBrowsing(
                    unitId = state.unitId,
                    attackPhase = state.attackPhase,
                    torsoFacing = state.torsoFacing,
                    arc = state.arc,
                    validTargetIds = state.validTargetIds,
                    targets = state.targets,
                    selectedTargetIndex = 0,
                    weaponAssignments = weaponAssignments,
                    primaryTargetId = primaryTargetId,
                    prompt = targetBrowsingPrompt(state.targets.size),
                ),
            )
        }

        is InputAction.MoveCursor -> {
            val dir = action.direction
            val attacker = gameState.unitById(state.unitId) ?: return PhaseOutcome.Continue(state)
            when {
                dir == HexDirection.NE || dir == HexDirection.SE ->
                    PhaseOutcome.Continue(twistTorso(state, attacker, clockwise = true, gameState))
                dir == HexDirection.NW || dir == HexDirection.SW ->
                    PhaseOutcome.Continue(twistTorso(state, attacker, clockwise = false, gameState))
                else -> PhaseOutcome.Continue(state)
            }
        }

        else -> PhaseOutcome.Continue(state)
    }

    private fun handleTargetBrowsing(
        action: InputAction,
        state: PhaseState.Attack.TargetBrowsing,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is InputAction.Cancel -> {
            val status = if (state.weaponAssignments.isNotEmpty()) DeclarationStatus.WEAPONS_ASSIGNED else DeclarationStatus.PENDING
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

        is InputAction.Confirm -> {
            val isNoAttack = state.targets.isEmpty() || state.selectedTargetIndex >= state.targets.size
            if (isNoAttack) {
                currentImpulse?.declarations?.put(
                    state.unitId,
                    UnitDeclaration(
                        unitId = state.unitId,
                        torsoFacing = state.torsoFacing,
                        status = DeclarationStatus.NO_ATTACK,
                    ),
                )
                PhaseOutcome.Cancelled
            } else {
                val currentTarget = state.targets[state.selectedTargetIndex]
                val attacker = gameState.unitById(state.unitId)!!
                val primaryTargetId = state.primaryTargetId ?: currentTarget.unitId

                val assignedToOthers = state.weaponAssignments.entries
                    .filter { (k, _) -> k != currentTarget.unitId }
                    .flatMap { (_, v) -> v }
                    .toSet()
                val firstAvailable = currentTarget.eligibleWeapons
                    .indexOfFirst { w -> w.weaponIndex !in assignedToOthers }
                    .coerceAtLeast(0)

                PhaseOutcome.Continue(
                    PhaseState.Attack.WeaponAssignment(
                        unitId = state.unitId,
                        attackPhase = state.attackPhase,
                        torsoFacing = state.torsoFacing,
                        arc = state.arc,
                        validTargetIds = state.validTargetIds,
                        targets = state.targets,
                        selectedTargetIndex = state.selectedTargetIndex,
                        selectedWeaponIndex = firstAvailable,
                        weaponAssignments = state.weaponAssignments,
                        primaryTargetId = primaryTargetId,
                        prompt = weaponAssignmentPrompt(attacker.weapons.size),
                    ),
                )
            }
        }

        is InputAction.CycleUnit -> {
            // Tab cycles targets, wrapping through "No Attack" at index targets.size
            val total = state.targets.size + 1  // +1 for "No Attack"
            val newIndex = if (total <= 1) 0 else (state.selectedTargetIndex + 1) % total
            PhaseOutcome.Continue(state.copy(selectedTargetIndex = newIndex))
        }

        is InputAction.ClickHex -> {
            val targetUnit = gameState.unitAt(cursor)
            if (targetUnit != null && targetUnit.id in state.validTargetIds) {
                val idx = state.targets.indexOfFirst { it.unitId == targetUnit.id }
                if (idx >= 0) PhaseOutcome.Continue(state.copy(selectedTargetIndex = idx))
                else PhaseOutcome.Continue(state)
            } else {
                PhaseOutcome.Continue(state)
            }
        }

        is InputAction.MoveCursor -> PhaseOutcome.Continue(state)  // cursor moved externally

        else -> PhaseOutcome.Continue(state)
    }

    private fun handleWeaponAssignment(
        action: InputAction,
        state: PhaseState.Attack.WeaponAssignment,
    ): PhaseOutcome = when (action) {
        is InputAction.Cancel -> {
            PhaseOutcome.Continue(
                PhaseState.Attack.TargetBrowsing(
                    unitId = state.unitId,
                    attackPhase = state.attackPhase,
                    torsoFacing = state.torsoFacing,
                    arc = state.arc,
                    validTargetIds = state.validTargetIds,
                    targets = state.targets,
                    selectedTargetIndex = state.selectedTargetIndex,
                    weaponAssignments = state.weaponAssignments,
                    primaryTargetId = state.primaryTargetId,
                    prompt = targetBrowsingPrompt(state.targets.size),
                ),
            )
        }

        is InputAction.Confirm -> {
            val currentTarget = state.targets[state.selectedTargetIndex]
            val weapons = currentTarget.eligibleWeapons
            if (weapons.isEmpty()) return PhaseOutcome.Continue(state)

            val weapon = weapons[state.selectedWeaponIndex]
            val targetId = currentTarget.unitId
            val currentAssigned = state.weaponAssignments[targetId] ?: emptySet()
            val assignedElsewhere = state.weaponAssignments.any { (otherId, indices) ->
                otherId != targetId && weapon.weaponIndex in indices
            }

            val newAssigned = when {
                weapon.weaponIndex in currentAssigned -> currentAssigned - weapon.weaponIndex
                assignedElsewhere -> currentAssigned  // can't assign
                else -> currentAssigned + weapon.weaponIndex
            }
            val newAssignments = state.weaponAssignments + (targetId to newAssigned)

            // Update declaration immediately
            val hasAny = newAssignments.values.any { it.isNotEmpty() }
            val status = if (hasAny) DeclarationStatus.WEAPONS_ASSIGNED else DeclarationStatus.PENDING
            currentImpulse?.declarations?.put(
                state.unitId,
                UnitDeclaration(
                    unitId = state.unitId,
                    torsoFacing = state.torsoFacing,
                    status = status,
                    primaryTargetId = state.primaryTargetId,
                    weaponAssignments = newAssignments,
                ),
            )

            PhaseOutcome.Continue(state.copy(weaponAssignments = newAssignments))
        }

        is InputAction.MoveCursor -> {
            val dir = action.direction
            if (dir != HexDirection.N && dir != HexDirection.S) return PhaseOutcome.Continue(state)

            val currentTarget = state.targets[state.selectedTargetIndex]
            val weapons = currentTarget.eligibleWeapons
            if (weapons.size <= 1) return PhaseOutcome.Continue(state)

            val assignedToOthers = state.weaponAssignments.entries
                .filter { (k, _) -> k != currentTarget.unitId }
                .flatMap { (_, v) -> v }
                .toSet()
            val navigable = weapons.indices.filter { i -> weapons[i].weaponIndex !in assignedToOthers }
            if (navigable.isEmpty()) return PhaseOutcome.Continue(state)

            val currentPos = navigable.indexOf(state.selectedWeaponIndex).let { if (it < 0) 0 else it }
            val delta = if (dir == HexDirection.S) 1 else -1
            val newPos = (currentPos + delta + navigable.size) % navigable.size
            PhaseOutcome.Continue(state.copy(selectedWeaponIndex = navigable[newPos]))
        }

        else -> PhaseOutcome.Continue(state)
    }

    private fun twistTorso(
        state: PhaseState.Attack.TorsoFacing,
        attacker: CombatUnit,
        clockwise: Boolean,
        gameState: GameState,
    ): PhaseState.Attack.TorsoFacing {
        val legFacing = attacker.facing
        val newTorso = if (clockwise) state.torsoFacing.rotateClockwise() else state.torsoFacing.rotateCounterClockwise()
        if (legFacing.turnCostTo(newTorso) > 1) return state

        val newArc = FiringArc.forwardArc(attacker.position, newTorso, gameState.map)
        val newTargetIds = findValidTargets(attacker, newArc, gameState)
        val newTargets = buildTargetInfoList(attacker, newTargetIds, gameState)
        return state.copy(
            torsoFacing = newTorso,
            arc = newArc,
            validTargetIds = newTargetIds,
            targets = newTargets,
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
            val weapons = attacker.weapons.mapIndexedNotNull { index, weapon ->
                if (weapon.destroyed) return@mapIndexedNotNull null
                if (weapon.ammo?.let { it <= 0 } == true) return@mapIndexedNotNull null
                if (distance > weapon.longRange) return@mapIndexedNotNull null

                val rangeModifier = when {
                    distance <= weapon.shortRange -> 0
                    distance <= weapon.mediumRange -> 2
                    distance <= weapon.longRange -> 4
                    else -> return@mapIndexedNotNull null
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
                )
            }
            if (weapons.isEmpty()) return@mapNotNull null
            TargetInfo(unitId = targetId, unitName = target.name, eligibleWeapons = weapons)
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

    private fun targetBrowsingPrompt(targetCount: Int): String =
        if (targetCount == 0) "No targets — Enter: No Attack | Esc: back"
        else "Tab: cycle targets | Enter: select | Esc: back"

    private fun weaponAssignmentPrompt(weaponCount: Int): String =
        "↑/↓: navigate | Enter: toggle | Esc: back to targets"

    private fun heatPenaltyModifier(actor: CombatUnit): Int {
        val excessHeat = actor.currentHeat - actor.heatSinkCapacity
        return if (excessHeat <= 0) 0 else ceil(excessHeat / 3.0).toInt()
    }

    private companion object {
        const val TORSO_FACING_PROMPT = "←/→ twist torso | Enter: confirm | Esc: cancel"

        val TWO_D6_PROBABILITY: Map<Int, Int> = mapOf(
            2 to 100, 3 to 97, 4 to 92, 5 to 83, 6 to 72,
            7 to 58, 8 to 42, 9 to 28, 10 to 17, 11 to 8, 12 to 3,
        )
    }
}
