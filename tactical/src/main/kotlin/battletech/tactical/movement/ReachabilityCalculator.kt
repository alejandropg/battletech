package battletech.tactical.movement

import battletech.tactical.action.Unit
import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MovementMode
import java.util.PriorityQueue

public class ReachabilityCalculator(
    private val gameMap: GameMap,
    private val units: List<Unit>,
) {

    public fun calculate(actor: Unit, mode: MovementMode, startFacing: HexDirection): ReachabilityMap {
        val maxMP = when (mode) {
            MovementMode.WALK -> actor.walkingMP
            MovementMode.RUN -> actor.runningMP
            MovementMode.JUMP -> actor.jumpMP
        }

        val destinations = if (mode == MovementMode.JUMP) {
            calculateJump(actor, maxMP)
        } else {
            calculateGroundMovement(actor, startFacing, maxMP)
        }

        return ReachabilityMap(mode = mode, maxMP = maxMP, destinations = destinations)
    }

    private fun calculateGroundMovement(
        actor: Unit,
        startFacing: HexDirection,
        maxMP: Int,
    ): List<ReachableHex> {
        val enemyPositions = units.filter { it.id != actor.id }.map { it.position }.toSet()
        val friendlyPositions = units.filter { it.id != actor.id }.map { it.position }.toSet()

        val best = mutableMapOf<MovementState, Pair<Int, List<MovementStep>>>()
        val queue = PriorityQueue<Node>(compareBy { it.cost })

        val startState = MovementState(actor.position, startFacing)
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
            .filter { (state, _) -> state.position != actor.position }
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
                state = MovementState(position, cwFacing),
                cost = 1,
                path = node.path + MovementStep(position, cwFacing),
            )
        )

        // Turn counter-clockwise
        val ccwFacing = facing.rotateCounterClockwise()
        transitions.add(
            Transition(
                state = MovementState(position, ccwFacing),
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
                    state = MovementState(nextPosition, facing),
                    cost = moveCost,
                    path = node.path + MovementStep(nextPosition, facing),
                )
            )
        }

        return transitions
    }

    private fun calculateJump(actor: Unit, maxMP: Int): List<ReachableHex> {
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
        val state: MovementState,
        val cost: Int,
        val path: List<MovementStep>,
    )

    private data class Transition(
        val state: MovementState,
        val cost: Int,
        val path: List<MovementStep>,
    )
}
