package battletech.tactical.session

import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.model.TurnPhase
import battletech.tactical.movement.MovementMode

public object GameLogFormatter {

    public fun format(event: GameEvent, state: GameState, turn: TurnState): String = when (event) {
        is PhaseChanged -> "Phase: ${phaseName(event.to)}"
        is InitiativeRolled -> {
            val p1 = event.initiative.rolls[PlayerId.PLAYER_1]
            val p2 = event.initiative.rolls[PlayerId.PLAYER_2]
            val first = playerLabel(event.initiative.loser)
            "Initiative: P1 rolled $p1, P2 rolled $p2 — $first moves first"
        }
        is UnitMoved -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            val verb = movementVerb(event.mode)
            "$name $verb ${hexLabel(event.from)}→${hexLabel(event.to)} (${event.mpSpent} MP)"
        }
        is AttacksResolved -> {
            val fired = event.results.size
            val hits = event.results.count { it.hit }
            val damage = event.results.sumOf { it.damageApplied }
            "Attacks: $fired fired, $hits hit, $damage damage"
        }
        is AttackDeclarationsRecorded -> {
            val noun = if (event.count == 1) "attack" else "attacks"
            "${playerLabel(event.player)} declared ${event.count} $noun"
        }
        is TorsoFacingsApplied -> {
            if (event.facings.isEmpty()) {
                "Torso facings: no changes"
            } else {
                val parts = event.facings.entries.joinToString(", ") { (unitId, dir) ->
                    val name = state.unitById(unitId)?.name ?: unitId.value
                    "$name→$dir"
                }
                "Torso facings: $parts"
            }
        }
        is HeatDissipated -> {
            val parts = event.heatBefore
                .filterValues { it > 0 }
                .map { (unitId, before) ->
                    val name = state.unitById(unitId)?.name ?: unitId.value
                    "$name $before→${event.heatAfter[unitId] ?: 0}"
                }
            if (parts.isEmpty()) "Heat: no heat to dissipate"
            else "Heat: ${parts.joinToString(", ")}"
        }
        is TurnEnded -> "Turn ${event.turnNumber} complete"
    }

    private fun playerLabel(player: PlayerId): String = when (player) {
        PlayerId.PLAYER_1 -> "P1"
        PlayerId.PLAYER_2 -> "P2"
    }

    private fun movementVerb(mode: MovementMode): String = when (mode) {
        MovementMode.WALK -> "walked"
        MovementMode.RUN -> "ran"
        MovementMode.JUMP -> "jumped"
    }

    private fun hexLabel(coord: HexCoordinates): String =
        "%02d%02d".format(coord.col + 1, coord.row + 1)

    private fun phaseName(phase: TurnPhase): String = when (phase) {
        TurnPhase.INITIATIVE -> "Initiative"
        TurnPhase.MOVEMENT -> "Movement"
        TurnPhase.WEAPON_ATTACK -> "Weapon Attack"
        TurnPhase.PHYSICAL_ATTACK -> "Physical Attack"
        TurnPhase.HEAT -> "Heat"
        TurnPhase.END -> "End"
    }
}
