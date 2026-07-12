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
import battletech.tactical.session.SessionNotice
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
import battletech.tui.hex.destroyedIcon
import battletech.tui.hex.locationDestroyedIcon
import battletech.tui.hex.movementModeIcon
import battletech.tui.hex.sessionNoticeIcon
import battletech.tui.hex.targetIcon
import battletech.tui.hex.torsoArrowIcon

internal object GameLogFormatter {

    data class LogLine(val icon: String?, val text: String)

    /** Renders an event as one or more log lines, each with its own icon (e.g. one per twisted unit). */
    fun lines(event: GameEvent, state: GameState): List<LogLine> = when (event) {
        is PhaseChanged -> emptyList()
        is TurnEnded -> emptyList()
        is TorsoFacingsApplied -> torsoFacingLines(event, state)
        is AttackDeclarationsRecorded -> attackDeclarationLines(event, state)
        is InitiativeRolled -> {
            val p1 = event.initiative.rolls[PlayerId.PLAYER_1]!!
            val p2 = event.initiative.rolls[PlayerId.PLAYER_2]!!
            val first = playerLabel(event.initiative.loser)
            listOf(LogLine(null, "Initiative: P1 ${diceRollLabel(p1)}, P2 ${diceRollLabel(p2)} — $first moves first"))
        }
        is UnitMoved -> {
            val name = event.unitId.value
            listOf(LogLine(movementModeIcon(event.mode), "$name (${event.mpSpent} MP) ${hexLabel(event.from)}→${hexLabel(event.to)}"))
        }
        is AttacksResolved -> {
            val fired = event.results.size
            val hits = event.results.count { it.hit }
            val damage = event.results.sumOf { it.damageApplied }
            val summary = "Attacks: $fired fired, $hits hit, $damage damage"
            val destroyed = destroyedClause(event.results.map { it.targetId to it.damage }, state)
            val text = if (destroyed == null) summary else "$summary — $destroyed"
            val icon = if (event.results.any { r -> r.damage.any { it.destroyed } }) locationDestroyedIcon() else null
            val lines = mutableListOf(LogLine(icon, text))
            // For cluster weapons that hit, append a detail line showing missiles and per-group locations.
            event.results.filter { it.hit }.forEach { result ->
                val clusterLine = clusterDetailLine(result)
                if (clusterLine != null) lines.add(clusterLine)
            }
            lines
        }
        is HeatDissipated -> {
            val parts = event.heatBefore
                .filterValues { it > 0 }
                .map { (unitId, before) ->
                    val name = unitId.value
                    "$name $before→${event.heatAfter[unitId] ?: 0}"
                }
            val text = if (parts.isEmpty()) "Heat: no heat to dissipate" else "Heat: ${parts.joinToString(", ")}"
            listOf(LogLine(null, text))
        }
        is PhysicalAttacksResolved -> {
            val made = event.results.size
            val hits = event.results.count { it.hit }
            val damage = event.results.sumOf { it.damageApplied }
            val summary = "Physical attacks: $made made, $hits hit, $damage damage"
            val destroyed = destroyedClause(event.results.map { it.targetId to it.damage }, state)
            val text = if (destroyed == null) summary else "$summary — $destroyed"
            val icon = if (event.results.any { r -> r.damage.any { it.destroyed } }) locationDestroyedIcon() else null
            listOf(LogLine(icon, text))
        }
        is UnitFell -> {
            val name = event.unitId.value
            listOf(LogLine(null, "$name fell — ${event.fall.damage} damage"))
        }
        is UnitStoodUp -> {
            val name = event.unitId.value
            listOf(LogLine(null, if (event.stoodUp) "$name stood up" else "$name failed to stand"))
        }
        is UnitShutdown -> {
            val name = event.unitId.value
            listOf(LogLine(null, if (event.auto) "$name auto-shut down (heat ≥ 30)" else "$name shut down from heat"))
        }
        is UnitRestarted -> {
            val name = event.unitId.value
            listOf(LogLine(null, "$name restarted"))
        }
        is AmmoExploded -> {
            val name = event.unitId.value
            listOf(LogLine(ammoExplosionIcon(), "$name ammo explosion: ${event.ammoType.name} (${event.damage} damage)"))
        }
        is UnitDestroyed -> {
            val name = event.unitId.value
            listOf(LogLine(destroyedIcon(), "$name destroyed (${destructionReasonLabel(event.reason)})"))
        }
        is MatchEnded -> {
            val winner = event.winner
            listOf(LogLine(null, if (winner == null) "Match over — draw" else "Match over — ${playerLabel(winner)} wins!"))
        }
        is CriticalHit -> {
            val name = event.unitId.value
            val component = criticalSlotContentLabel(event.content, event.unitId, state)
            listOf(LogLine(criticalHitIcon(event.content), "$name critical hit: $component in ${locationLabel(event.location)}"))
        }
        is PilotHit -> {
            val name = event.unitId.value
            listOf(LogLine(null, "$name pilot wounded (${event.pilotHits} hit${if (event.pilotHits == 1) "" else "s"} total)"))
        }
        is PilotKnockedUnconscious -> {
            val name = event.unitId.value
            listOf(LogLine(null, "$name pilot knocked unconscious"))
        }
        is PilotRecoveredConsciousness -> {
            val name = event.unitId.value
            listOf(LogLine(null, "$name pilot regained consciousness"))
        }
        is SessionNotice -> listOf(LogLine(sessionNoticeIcon(), event.text))
    }

    /**
     * Returns a detail line for a cluster-weapon hit, e.g.
     * "  LRM 20: 16 missiles → 5 CT, 5 RT, 5 LA, 1 RA"
     *
     * Returns null for non-cluster weapons (missilesHit == null) or when there are no
     * location hits to report (empty locationHits — legacy/test AttackResult objects).
     */
    private fun clusterDetailLine(result: battletech.tactical.attack.AttackResult): LogLine? {
        val missiles = result.missilesHit ?: return null
        if (result.locationHits.isEmpty()) return null
        val groupParts = result.locationHits.joinToString(", ") { "${it.damage} ${locationLabel(it.location)}" }
        return LogLine(null, "  ${result.weaponName}: $missiles missiles → $groupParts")
    }

    private fun torsoFacingLines(event: TorsoFacingsApplied, state: GameState): List<LogLine> {
        if (event.facings.isEmpty()) return listOf(LogLine(null, "Torso facings: no changes"))
        return event.facings.entries.map { (unitId, dir) ->
            val name = unitId.value
            LogLine(torsoArrowIcon(dir).first, "$name torso → $dir")
        }
    }

    private fun attackDeclarationLines(event: AttackDeclarationsRecorded, state: GameState): List<LogLine> =
        event.declarations.groupBy { it.attackerId }.entries.map { (attackerId, decls) ->
            val attacker = state.unitById(attackerId)
            val attackerName = attackerId.value
            val perTarget = decls.groupBy { it.targetId }.entries.joinToString(", ") { (targetId, targetDecls) ->
                val targetName = targetId.value
                val weaponNames = targetDecls.joinToString(", ") { decl ->
                    attacker.weapons.getOrNull(decl.weaponIndex)?.name ?: "weapon#${decl.weaponIndex}"
                }
                "$targetName ($weaponNames)"
            }
            LogLine(targetIcon(), "$attackerName → $perTarget")
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
                state.unitById(unitId).weapons.find { it.mountId == content.weaponId }?.name ?: "weapon"
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
            val name = targetId.value
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
