package battletech.tui.view

import battletech.tactical.attack.LocationDamage
import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MechLocation
import battletech.tactical.model.PlayerId
import battletech.tactical.session.AmmoExploded
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.GameEvent
import battletech.tactical.session.HeatDissipated
import battletech.tactical.session.InitiativeRolled
import battletech.tactical.session.MatchEnded
import battletech.tactical.session.PhaseChanged
import battletech.tactical.session.PhysicalAttacksResolved
import battletech.tactical.session.PilotHit
import battletech.tactical.session.PilotKnockedUnconscious
import battletech.tactical.session.PilotRecoveredConsciousness
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnEnded
import battletech.tactical.session.UnitDestroyed
import battletech.tactical.session.UnitFell
import battletech.tactical.session.UnitMoved
import battletech.tactical.session.UnitRestarted
import battletech.tactical.session.UnitShutdown
import battletech.tactical.session.UnitStoodUp
import battletech.tactical.unit.ActuatorType
import battletech.tactical.unit.CriticalSlotContent
import battletech.tactical.unit.DestructionReason
import battletech.tactical.unit.UnitId
import battletech.tui.hex.ammoExplosionIcon
import battletech.tui.hex.criticalHitIcon
import battletech.tui.hex.diceIcon
import battletech.tui.hex.locationDestroyedIcon
import battletech.tui.hex.movementModeIcon
import battletech.tui.hex.torsoArrowIcon

internal object GameLogFormatter {

    fun format(event: GameEvent, state: GameState): String? = when (event) {
        is PhaseChanged -> null
        is InitiativeRolled -> {
            val p1 = event.initiative.rolls[PlayerId.PLAYER_1]!!
            val p2 = event.initiative.rolls[PlayerId.PLAYER_2]!!
            val first = playerLabel(event.initiative.loser)
            "Initiative: P1 ${diceIcon(p1.d1)}+${diceIcon(p1.d2)}=${p1.total}, P2 ${diceIcon(p2.d1)}+${diceIcon(p2.d2)}=${p2.total} — $first moves first"
        }

        is UnitMoved -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            "$name (${event.mpSpent} MP) ${hexLabel(event.from)}→${hexLabel(event.to)}"
        }

        is AttacksResolved -> {
            val fired = event.results.size
            val hits = event.results.count { it.hit }
            val damage = event.results.sumOf { it.damageApplied }
            val summary = "Attacks: $fired fired, $hits hit, $damage damage"
            val destroyed = destroyedClause(event.results.map { it.targetId to it.damage }, state)
            if (destroyed == null) summary else "$summary — $destroyed"
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

        is PhysicalAttacksResolved -> {
            val made = event.results.size
            val hits = event.results.count { it.hit }
            val damage = event.results.sumOf { it.damageApplied }
            val summary = "Physical attacks: $made made, $hits hit, $damage damage"
            val destroyed = destroyedClause(event.results.map { it.targetId to it.damage }, state)
            if (destroyed == null) summary else "$summary — $destroyed"
        }

        is UnitFell -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            "$name fell — ${event.fall.damage} damage"
        }

        is UnitStoodUp -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            if (event.stoodUp) "$name stood up" else "$name failed to stand"
        }

        is UnitShutdown -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            if (event.auto) "$name auto-shut down (heat ≥ 30)" else "$name shut down from heat"
        }

        is UnitRestarted -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            "$name restarted"
        }

        is AmmoExploded -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            "$name ammo explosion: ${event.ammoType.name} (${event.damage} damage)"
        }

        is TurnEnded -> null

        is UnitDestroyed -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            "$name destroyed (${destructionReasonLabel(event.reason)})"
        }

        is MatchEnded -> {
            val winner = event.winner
            if (winner == null) "Match over — draw" else "Match over — ${playerLabel(winner)} wins!"
        }

        is CriticalHit -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            val component = criticalSlotContentLabel(event.content, event.unitId, state)
            "$name critical hit: $component in ${locationLabel(event.location)}"
        }

        is PilotHit -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            "$name pilot wounded (${event.pilotHits} hit${if (event.pilotHits == 1) "" else "s"} total)"
        }

        is PilotKnockedUnconscious -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            "$name pilot knocked unconscious"
        }

        is PilotRecoveredConsciousness -> {
            val name = state.unitById(event.unitId)?.name ?: event.unitId.value
            "$name pilot regained consciousness"
        }
    }

    /** Leading glyph for a log line, or null when the situation has no dedicated marker. */
    fun iconFor(event: GameEvent): String? = when (event) {
        is UnitMoved -> movementModeIcon(event.mode)
        is CriticalHit -> criticalHitIcon(event.content)
        is AmmoExploded -> ammoExplosionIcon()
        is AttacksResolved ->
            if (event.results.any { r -> r.damage.any { it.destroyed } }) locationDestroyedIcon() else null
        is PhysicalAttacksResolved ->
            if (event.results.any { r -> r.damage.any { it.destroyed } }) locationDestroyedIcon() else null
        is TorsoFacingsApplied -> event.facings.values.singleOrNull()?.let { torsoArrowIcon(it).first }
        else -> null
    }

    private fun destructionReasonLabel(reason: DestructionReason): String = when (reason) {
        DestructionReason.HEAD_DESTROYED -> "head destroyed"
        DestructionReason.CENTER_TORSO_DESTROYED -> "center torso destroyed"
        DestructionReason.BOTH_LEGS_DESTROYED -> "both legs destroyed"
        DestructionReason.ENGINE_DESTROYED -> "engine destroyed"
        DestructionReason.PILOT_DEAD -> "pilot dead"
    }

    private fun criticalSlotContentLabel(content: CriticalSlotContent, unitId: UnitId, state: GameState): String =
        when (content) {
            is CriticalSlotContent.Empty -> "empty slot"
            is CriticalSlotContent.Engine -> "Engine"
            is CriticalSlotContent.Gyro -> "Gyro"
            is CriticalSlotContent.Sensors -> "Sensors"
            is CriticalSlotContent.LifeSupport -> "Life Support"
            is CriticalSlotContent.Cockpit -> "Cockpit"
            is CriticalSlotContent.HeatSink -> "Heat Sink"
            is CriticalSlotContent.JumpJet -> "Jump Jet"
            is CriticalSlotContent.Actuator -> actuatorLabel(content.type)
            is CriticalSlotContent.WeaponMount -> {
                state.unitById(unitId)?.weapons?.find { it.mountId == content.weaponId }?.name ?: "weapon"
            }
            is CriticalSlotContent.AmmoBin -> "${content.type} ammo"
        }

    private fun actuatorLabel(type: ActuatorType): String = when (type) {
        ActuatorType.SHOULDER -> "Shoulder actuator"
        ActuatorType.UPPER_ARM -> "Upper arm actuator"
        ActuatorType.LOWER_ARM -> "Lower arm actuator"
        ActuatorType.HAND -> "Hand actuator"
        ActuatorType.HIP -> "Hip actuator"
        ActuatorType.UPPER_LEG -> "Upper leg actuator"
        ActuatorType.LOWER_LEG -> "Lower leg actuator"
        ActuatorType.FOOT -> "Foot actuator"
    }

    private fun playerLabel(player: PlayerId): String = when (player) {
        PlayerId.PLAYER_1 -> "P1"
        PlayerId.PLAYER_2 -> "P2"
    }

    private fun hexLabel(coord: HexCoordinates): String =
        "%02d%02d".format(coord.col + 1, coord.row + 1)

    private fun destroyedClause(
        targetsAndDamage: List<Pair<UnitId, List<LocationDamage>>>,
        state: GameState,
    ): String? {
        val parts = targetsAndDamage.flatMap { (targetId, steps) ->
            val name = state.unitById(targetId)?.name ?: targetId.value
            steps.filter { it.destroyed }.map { "$name ${locationLabel(it.location)}" }
        }
        if (parts.isEmpty()) return null
        return "${parts.joinToString(", ")} destroyed"
    }

    private fun locationLabel(location: MechLocation): String = when (location) {
        MechLocation.HEAD -> "Head"
        MechLocation.CENTER_TORSO -> "Center Torso"
        MechLocation.LEFT_TORSO -> "Left Torso"
        MechLocation.RIGHT_TORSO -> "Right Torso"
        MechLocation.LEFT_ARM -> "Left Arm"
        MechLocation.RIGHT_ARM -> "Right Arm"
        MechLocation.LEFT_LEG -> "Left Leg"
        MechLocation.RIGHT_LEG -> "Right Leg"
    }
}
