package battletech.tui.view

import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.GameEvent
import battletech.tactical.session.HeatDissipated
import battletech.tactical.session.InitiativeRolled
import battletech.tactical.session.PhaseChanged
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnEnded
import battletech.tactical.session.UnitMoved
import battletech.tui.hex.diceIcon
import battletech.tui.hex.movementModeIcon

internal object GameLogFormatter {

    fun format(event: GameEvent, state: GameState): String? = when (event) {
        is PhaseChanged -> null
        is InitiativeRolled -> {
            val p1 = event.initiative.rolls[PlayerId.PLAYER_1]!!
            val p2 = event.initiative.rolls[PlayerId.PLAYER_2]!!
            val first = playerLabel(event.initiative.loser)
            "Initiative: P1 ${diceIcon(p1.d1)}${diceIcon(p1.d2)} ${p1.total}, P2 ${diceIcon(p2.d1)}${diceIcon(p2.d2)} ${p2.total} — $first moves first"
        }

        is UnitMoved -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            val icon = movementModeIcon(event.mode)
            "$name $icon (${event.mpSpent} MP) ${hexLabel(event.from)}→${hexLabel(event.to)}"
        }

        is AttacksResolved -> {
            val fired = event.results.size
            val hits = event.results.count { it.hit }
            val damage = event.results.sumOf { it.damageApplied }
            "Attacks: $fired fired, $hits hit, $damage damage"
        }

        is AttackDeclarationsRecorded -> {
            val grouped = event.declarations.groupBy { it.attackerId to it.targetId }
            val pairs = grouped.entries.joinToString(", ") { (key, decls) ->
                val (attackerId, targetId) = key
                val attacker = state.unitById(attackerId)
                val attackerName = attacker?.name ?: attackerId.value
                val targetName = state.unitById(targetId)?.name ?: targetId.value
                val weaponNames = decls.joinToString(", ") { decl ->
                    attacker?.weapons?.getOrNull(decl.weaponIndex)?.name ?: "weapon#${decl.weaponIndex}"
                }
                "$attackerName→$targetName ($weaponNames)"
            }
            "${playerLabel(event.player)} declared: $pairs"
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

    private fun hexLabel(coord: HexCoordinates): String =
        "%02d%02d".format(coord.col + 1, coord.row + 1)
}
