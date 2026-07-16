package battletech.tactical.movement

import battletech.tactical.model.GameState
import battletech.tactical.model.MovementMode
import battletech.tactical.session.CommandRejection
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.cannotStandFromGyroDamage
import battletech.tactical.unit.destroyedLegCount

/**
 * Server-authoritative movement legality: which modes a unit may attempt this
 * phase, whether a specific move/stand-up is rule-legal before spending any
 * MP, and the canonical destination for an attempted move. This is the single
 * source of truth shared by [MovementPhaseHandler] (which enforces it) and
 * [battletech.tactical.query.DefaultPlayerView] (which previews it) so read
 * and apply can never drift.
 */
public object MovementRules {

    /**
     * Modes [unit] may attempt this phase, in WALK/RUN/JUMP order. A unit
     * that is shutdown, destroyed, or piloted by an unconscious pilot has no
     * available modes; otherwise a mode is offered iff the unit has a
     * positive MP allowance for it (prone-ness and leg damage are legality
     * concerns for a specific attempt, see [moveRejection], not availability).
     */
    public fun availableModes(unit: CombatUnit): List<MovementMode> {
        if (unit.isShutdown || unit.isDestroyed || !unit.isPilotConscious) return emptyList()
        return buildList {
            if (unit.walkingMP > 0) add(MovementMode.WALK)
            if (unit.runningMP > 0) add(MovementMode.RUN)
            if (unit.jumpMP > 0) add(MovementMode.JUMP)
        }
    }

    /**
     * Rule-level rejection for [unit] attempting to move in [mode], independent
     * of destination (prone units can't move at all; a destroyed leg forbids
     * running and jumping, hobbling the unit to a halved walk). Returns null
     * if the attempt is rule-legal.
     */
    public fun moveRejection(unit: CombatUnit, mode: MovementMode): CommandRejection? = when {
        unit.isProne -> CommandRejection.UnitProne(unit.id)
        mode == MovementMode.JUMP && unit.destroyedLegCount() > 0 -> CommandRejection.LegDestroyed(unit.id)
        mode == MovementMode.RUN && unit.destroyedLegCount() > 0 -> CommandRejection.LegDestroyed(unit.id)
        else -> null
    }

    /** Rule-level rejection for [unit] attempting to stand up. Null if legal. */
    public fun standUpRejection(unit: CombatUnit): CommandRejection? = when {
        !unit.isProne -> CommandRejection.UnitNotProne(unit.id)
        unit.cannotStandFromGyroDamage() -> CommandRejection.GyroDestroyed(unit.id)
        else -> null
    }

    /**
     * The canonical, zero-cost [ReachableHex] representing [unit] staying put:
     * same position/facing, no MP spent, empty path. Used both as the
     * "stationary" shortcut in [authoritativeDestination] (skips the Dijkstra
     * entirely) and by callers that already know a move is stationary and
     * need the canonical hex without re-deriving it.
     */
    public fun stationaryHex(unit: CombatUnit): ReachableHex =
        ReachableHex(position = unit.position, facing = unit.facing, mpSpent = 0, path = emptyList())

    /**
     * Server-authoritative destination check: never trusts [requested]'s `mpSpent`
     * or `path`. A stationary request (same position/facing, 0 MP) is always
     * legal and short-circuits to [stationaryHex] without running the Dijkstra.
     * Otherwise recomputes reachability for [unit] in [mode] and requires
     * [requested] to match a reachable hex exactly (position, facing, mpSpent,
     * and path) — any mismatch (including a merely-plausible but tampered path)
     * is rejected as [CommandRejection.DestinationUnreachable].
     *
     * This is the one place [ReachabilityCalculator]'s Dijkstra runs for a given
     * move attempt; callers must not re-run it (see [MovementPhaseHandler.applyMove]).
     */
    public fun authoritativeDestination(
        unit: CombatUnit,
        mode: MovementMode,
        requested: ReachableHex,
        state: GameState,
    ): AuthoritativeDestination {
        if (requested.position == unit.position && requested.facing == unit.facing && requested.mpSpent == 0) {
            return AuthoritativeDestination.Legal(stationaryHex(unit))
        }
        val serverHex = ReachabilityCalculator(state.map, state.units)
            .calculate(unit, mode)
            .destinations
            .firstOrNull { it.position == requested.position && it.facing == requested.facing }
        return when {
            serverHex == null -> AuthoritativeDestination.Illegal(
                CommandRejection.DestinationUnreachable(unit.id, requested.position),
            )
            serverHex != requested -> AuthoritativeDestination.Illegal(
                CommandRejection.DestinationUnreachable(unit.id, requested.position),
            )
            else -> AuthoritativeDestination.Legal(serverHex)
        }
    }
}

/** Outcome of [MovementRules.authoritativeDestination]. */
public sealed interface AuthoritativeDestination {
    public data class Legal(public val hex: ReachableHex) : AuthoritativeDestination
    public data class Illegal(public val rejection: CommandRejection) : AuthoritativeDestination
}
