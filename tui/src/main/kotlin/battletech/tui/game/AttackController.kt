package battletech.tui.game

import battletech.tactical.action.ActionQueryService
import battletech.tactical.action.CombatUnit
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
    private val pendingDeclarations: MutableList<AttackDeclaration> = mutableListOf()

    public fun enter(unit: CombatUnit, phase: TurnPhase, gameState: GameState): PhaseState.Attack {
        val torsoFacing = unit.facing
        val arc = FiringArc.forwardArc(unit.position, torsoFacing, gameState.map)
        val validTargetIds = findValidTargets(unit, arc, gameState)

        val browsing = PhaseState.Attack.Browsing(
            unitId = unit.id,
            attackPhase = phase,
            torsoFacing = torsoFacing,
            arc = arc,
            validTargetIds = validTargetIds,
            targets = emptyList(),
            prompt = browsingPrompt(unit.name, validTargetIds),
        )
        val targets = buildTargetInfoList(unit, browsing, gameState)
        return browsing.copy(targets = targets)
    }

    public fun handle(
        action: InputAction,
        state: PhaseState.Attack,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (state) {
        is PhaseState.Attack.Browsing -> handleBrowsing(action, state, cursor, gameState)
        is PhaseState.Attack.AssigningWeapons -> handleAssigningWeapons(action, state, gameState)
    }

    public fun collectDeclarations(): List<AttackDeclaration> = pendingDeclarations.toList()

    public fun clearDeclarations() {
        pendingDeclarations.clear()
    }

    private fun handleBrowsing(
        action: InputAction,
        state: PhaseState.Attack.Browsing,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is InputAction.Cancel -> PhaseOutcome.Cancelled

        is InputAction.Confirm -> {
            if (state.validTargetIds.isEmpty()) {
                PhaseOutcome.Complete(gameState)
            } else {
                // Try to select target at cursor
                val targetUnit = gameState.unitAt(cursor)
                if (targetUnit != null && targetUnit.id in state.validTargetIds) {
                    val attacker = gameState.unitById(state.unitId)!!
                    PhaseOutcome.Continue(enterAssigningWeapons(attacker, targetUnit, state, gameState))
                } else {
                    PhaseOutcome.Continue(state)
                }
            }
        }

        is InputAction.ClickHex -> {
            val targetUnit = gameState.unitAt(cursor)
            if (targetUnit != null && targetUnit.id in state.validTargetIds) {
                val attacker = gameState.unitById(state.unitId)!!
                PhaseOutcome.Continue(enterAssigningWeapons(attacker, targetUnit, state, gameState))
            } else {
                PhaseOutcome.Continue(state)
            }
        }

        is InputAction.MoveCursor -> {
            val dir = action.direction
            if (dir == HexDirection.NE || dir == HexDirection.SE) {
                // Right arrow → twist clockwise
                val newState = twistTorso(state, clockwise = true, gameState)
                PhaseOutcome.Continue(newState)
            } else if (dir == HexDirection.NW || dir == HexDirection.SW) {
                // Left arrow → twist counterclockwise
                val newState = twistTorso(state, clockwise = false, gameState)
                PhaseOutcome.Continue(newState)
            } else {
                PhaseOutcome.Continue(state)
            }
        }

        else -> PhaseOutcome.Continue(state)
    }

    private fun handleAssigningWeapons(
        action: InputAction,
        state: PhaseState.Attack.AssigningWeapons,
        gameState: GameState,
    ): PhaseOutcome = when (action) {
        is InputAction.Cancel -> {
            PhaseOutcome.Continue(
                PhaseState.Attack.Browsing(
                    unitId = state.unitId,
                    attackPhase = state.attackPhase,
                    torsoFacing = state.torsoFacing,
                    arc = state.arc,
                    validTargetIds = state.validTargetIds,
                    targets = state.targets,
                    prompt = browsingPrompt(null, state.validTargetIds),
                ),
            )
        }

        is InputAction.MoveCursor -> {
            val dir = action.direction
            if (dir == HexDirection.N) {
                // Navigate up in target list
                val newIndex = (state.selectedTargetIndex - 1).coerceAtLeast(0)
                PhaseOutcome.Continue(state.copy(selectedTargetIndex = newIndex))
            } else if (dir == HexDirection.S) {
                // Navigate down in target list
                val newIndex = (state.selectedTargetIndex + 1).coerceAtMost(state.targets.size - 1)
                PhaseOutcome.Continue(state.copy(selectedTargetIndex = newIndex))
            } else {
                PhaseOutcome.Continue(state)
            }
        }

        is InputAction.SelectAction -> {
            // Toggle weapon by number key (1-based)
            val weaponIdx = action.index - 1
            val currentTarget = state.targets[state.selectedTargetIndex]
            if (weaponIdx in currentTarget.eligibleWeapons.indices) {
                val weapon = currentTarget.eligibleWeapons[weaponIdx]
                val targetId = currentTarget.unitId
                val currentAssigned = state.weaponAssignments[targetId] ?: emptySet()
                val newAssigned = if (weapon.weaponIndex in currentAssigned) {
                    currentAssigned - weapon.weaponIndex
                } else {
                    // Check weapon isn't assigned to another target
                    val assignedElsewhere = state.weaponAssignments.any { (otherId, indices) ->
                        otherId != targetId && weapon.weaponIndex in indices
                    }
                    if (assignedElsewhere) currentAssigned else currentAssigned + weapon.weaponIndex
                }
                val newAssignments = state.weaponAssignments + (targetId to newAssigned)
                PhaseOutcome.Continue(state.copy(weaponAssignments = newAssignments))
            } else {
                PhaseOutcome.Continue(state)
            }
        }

        is InputAction.CycleUnit -> {
            // Tab cycles between targets
            if (state.targets.size > 1) {
                val newIndex = (state.selectedTargetIndex + 1) % state.targets.size
                PhaseOutcome.Continue(state.copy(selectedTargetIndex = newIndex))
            } else {
                PhaseOutcome.Continue(state)
            }
        }

        is InputAction.Confirm -> {
            // Finalize declarations
            val hasAnyAssignment = state.weaponAssignments.values.any { it.isNotEmpty() }
            if (hasAnyAssignment) {
                for ((targetId, weaponIndices) in state.weaponAssignments) {
                    for (weaponIndex in weaponIndices) {
                        pendingDeclarations.add(
                            AttackDeclaration(
                                attackerId = state.unitId,
                                targetId = targetId,
                                weaponIndex = weaponIndex,
                                isPrimary = targetId == state.primaryTargetId,
                            ),
                        )
                    }
                }
            }
            PhaseOutcome.Complete(gameState)
        }

        else -> PhaseOutcome.Continue(state)
    }

    private fun twistTorso(
        state: PhaseState.Attack.Browsing,
        clockwise: Boolean,
        gameState: GameState,
    ): PhaseState.Attack.Browsing {
        val attacker = gameState.unitById(state.unitId) ?: return state
        val legFacing = attacker.facing
        val newTorso = if (clockwise) {
            state.torsoFacing.rotateClockwise()
        } else {
            state.torsoFacing.rotateCounterClockwise()
        }
        // Only allow ±1 from leg facing
        if (legFacing.turnCostTo(newTorso) > 1) return state

        val newArc = FiringArc.forwardArc(attacker.position, newTorso, gameState.map)
        val newTargetIds = findValidTargets(attacker, newArc, gameState)

        val newState = state.copy(
            torsoFacing = newTorso,
            arc = newArc,
            validTargetIds = newTargetIds,
            targets = emptyList(),
            prompt = browsingPrompt(attacker.name, newTargetIds),
        )
        val targets = buildTargetInfoList(attacker, newState, gameState)
        return newState.copy(targets = targets)
    }

    private fun enterAssigningWeapons(
        attacker: CombatUnit,
        primaryTarget: CombatUnit,
        state: PhaseState.Attack.Browsing,
        gameState: GameState,
    ): PhaseState.Attack.AssigningWeapons {
        val targets = buildTargetInfoList(attacker, state, gameState)
        val selectedIndex = targets.indexOfFirst { it.unitId == primaryTarget.id }.coerceAtLeast(0)

        return PhaseState.Attack.AssigningWeapons(
            unitId = state.unitId,
            attackPhase = state.attackPhase,
            torsoFacing = state.torsoFacing,
            arc = state.arc,
            validTargetIds = state.validTargetIds,
            targets = targets,
            selectedTargetIndex = selectedIndex,
            weaponAssignments = emptyMap(),
            primaryTargetId = primaryTarget.id,
            prompt = "Assign weapons (1-${attacker.weapons.size}), Tab=next target, Enter=confirm",
        )
    }

    private fun buildTargetInfoList(
        attacker: CombatUnit,
        state: PhaseState.Attack.Browsing,
        gameState: GameState,
    ): List<TargetInfo> {
        return state.validTargetIds.mapNotNull { targetId ->
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
                val rangeBand = when {
                    distance <= weapon.shortRange -> "short"
                    distance <= weapon.mediumRange -> { modifiers.add("+2 med"); "medium" }
                    else -> { modifiers.add("+4 long"); "long" }
                }
                if (heatPenalty > 0) modifiers.add("+$heatPenalty heat")

                val targetNumber = attacker.gunnerySkill + rangeModifier + heatPenalty
                val chance = TWO_D6_PROBABILITY[targetNumber] ?: 0

                WeaponTargetInfo(
                    weaponIndex = index,
                    weaponName = weapon.name,
                    successChance = chance,
                    damage = weapon.damage,
                    modifiers = modifiers,
                )
            }
            if (weapons.isEmpty()) return@mapNotNull null
            TargetInfo(
                unitId = targetId,
                unitName = target.name,
                eligibleWeapons = weapons,
            )
        }
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

    private fun browsingPrompt(unitName: String?, validTargetIds: Set<UnitId>): String {
        return if (validTargetIds.isEmpty()) {
            "No attacks available. Press Enter to skip."
        } else {
            val name = unitName ?: "unit"
            "Select target for $name (${validTargetIds.size} targets)"
        }
    }

    private fun heatPenaltyModifier(actor: CombatUnit): Int {
        val excessHeat = actor.currentHeat - actor.heatSinkCapacity
        return if (excessHeat <= 0) 0 else ceil(excessHeat / 3.0).toInt()
    }

    private companion object {
        val TWO_D6_PROBABILITY: Map<Int, Int> = mapOf(
            2 to 100, 3 to 97, 4 to 92, 5 to 83, 6 to 72,
            7 to 58, 8 to 42, 9 to 28, 10 to 17, 11 to 8, 12 to 3,
        )
    }
}
