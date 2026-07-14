package battletech.tactical.movement

import battletech.tactical.model.MovementMode

import battletech.tactical.heat.HeatScale
import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.destroyedLegCount
import java.util.PriorityQueue
import kotlin.math.ceil

public class ReachabilityCalculator(
    private val gameMap: GameMap,
    private val units: List<CombatUnit>,
) {

    public fun calculate(actor: CombatUnit, mode: MovementMode): ReachabilityMap {
        // A destroyed leg prevents running and jumping; walking is halved.
        val hasDestroyedLeg = actor.destroyedLegCount() > 0

        // Heat slows walking (and the running derived from it); jump jets are
        // unaffected. See HeatScale.movementPenalty / docs/rules/heat.md.
        val penalty = HeatScale.movementPenalty(actor.currentHeat)
        // With a destroyed leg the base walking MP is halved (floor) before the heat
        // penalty is applied, per the standard "hobble" rule.
        val baseWalk = if (hasDestroyedLeg) actor.walkingMP / 2 else actor.walkingMP
        val effectiveWalk = (baseWalk - penalty).coerceAtLeast(0)
        val maxMP = when (mode) {
            MovementMode.WALK -> effectiveWalk
            // Cannot run or jump with a destroyed leg — maxMP 0 yields an empty map.
            MovementMode.RUN -> if (hasDestroyedLeg) 0
                else if (penalty == 0) actor.runningMP
                else ceil(effectiveWalk * 1.5).toInt()
            MovementMode.JUMP -> if (hasDestroyedLeg) 0 else actor.jumpMP
        }

        val destinations = if (mode == MovementMode.JUMP) {
            calculateJump(actor, maxMP)
        } else {
            calculateGroundMovement(actor, maxMP)
        }

        return ReachabilityMap(mode = mode, maxMP = maxMP, destinations = destinations)
    }

    private fun calculateGroundMovement(actor: CombatUnit, maxMP: Int): List<ReachableHex> {
        val enemyPositions = units.filter { it.id != actor.id }.map { it.position }.toSet()
        val friendlyPositions = units.filter { it.id != actor.id }.map { it.position }.toSet()

        val best = mutableMapOf<MovementStep, Pair<Int, List<MovementStep>>>()
        val queue = PriorityQueue<Node>(compareBy { it.cost })

        val startState = MovementStep(actor.position, actor.facing)
        queue.add(Node(state = startState, cost = 0, path = emptyList()))

        while (queue.isNotEmpty()) {
            val node = queue.poll()
            if (node.state in best) continue
            best[node.state] = node.cost to node.path

            for ((nextState, moveCost, nextPath) in transitions(node, enemyPositions)) {
                val totalCost = node.cost + moveCost
                if (totalCost > maxMP) continue
                if (nextState in best) continue
                queue.add(Node(state = nextState, cost = totalCost, path = nextPath))
            }
        }

        val occupiedPositions = enemyPositions + friendlyPositions
        return best
            .filter { (state, _) -> state != startState }
            .filter { (state, _) -> state.position !in occupiedPositions }
            .map { (state, costAndPath) ->
                ReachableHex(
                    position = state.position,
                    facing = state.facing,
                    mpSpent = costAndPath.first,
                    path = costAndPath.second,
                )
            }
    }

    private fun transitions(
        node: Node,
        enemyPositions: Set<HexCoordinates>,
    ): List<Transition> {
        val transitions = mutableListOf<Transition>()
        val (position, facing) = node.state

        // Turn clockwise
        val cwFacing = facing.rotateClockwise()
        transitions.add(
            Transition(
                state = MovementStep(position, cwFacing),
                cost = 1,
                path = node.path + MovementStep(position, cwFacing),
            )
        )

        // Turn counter-clockwise
        val ccwFacing = facing.rotateCounterClockwise()
        transitions.add(
            Transition(
                state = MovementStep(position, ccwFacing),
                cost = 1,
                path = node.path + MovementStep(position, ccwFacing),
            )
        )

        // Move forward
        val nextPosition = position.neighbor(facing)
        if (nextPosition !in enemyPositions && nextPosition in gameMap.hexes) {
            val fromHex = gameMap.hexes.getValue(position)
            val toHex = gameMap.hexes.getValue(nextPosition)
            val moveCost = MovementCost.enterHexCost(fromHex, toHex)
            transitions.add(
                Transition(
                    state = MovementStep(nextPosition, facing),
                    cost = moveCost,
                    path = node.path + MovementStep(nextPosition, facing),
                )
            )
        }

        return transitions
    }

    private fun calculateJump(actor: CombatUnit, maxMP: Int): List<ReachableHex> {
        val occupiedPositions = units.map { it.position }.toSet()

        return gameMap.hexes.keys
            .filter { hex -> hex != actor.position }
            .filter { hex -> hex !in occupiedPositions }
            .filter { hex -> actor.position.distanceTo(hex) in 1..maxMP }
            .flatMap { hex ->
                val distance = actor.position.distanceTo(hex)
                HexDirection.entries.map { facing ->
                    ReachableHex(
                        position = hex,
                        facing = facing,
                        mpSpent = distance,
                        path = listOf(MovementStep(hex, facing)),
                    )
                }
            }
    }

    private data class Node(
        val state: MovementStep,
        val cost: Int,
        val path: List<MovementStep>,
    )

    private data class Transition(
        val state: MovementStep,
        val cost: Int,
        val path: List<MovementStep>,
    )
}
