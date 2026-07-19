package battletech.tui.view

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.LocationDamage
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.MechLocation
import battletech.tactical.model.PlayerId
import battletech.tactical.query.PlayerGameState
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
import battletech.tui.hex.attackOutcomeIcon
import battletech.tui.hex.criticalHitIcon
import battletech.tui.hex.destroyedIcon
import battletech.tui.hex.locationDestroyedIcon
import battletech.tui.hex.movementModeIcon
import battletech.tui.hex.pilotConsciousIcon
import battletech.tui.hex.pilotDeadIcon
import battletech.tui.hex.pilotUnconsciousIcon
import battletech.tui.hex.pilotWoundedIcon
import battletech.tui.hex.sessionNoticeIcon
import battletech.tui.hex.targetIcon
import battletech.tui.hex.torsoArrowIcon
import battletech.tui.hex.undisclosedCriticalHitIcon
import battletech.tui.hex.unitFellIcon
import battletech.tui.hex.unitStoodUpIcon

internal object GameLogFormatter {

    data class LogLine(val icon: String?, val text: String)

    /** Renders an event as one or more log lines, each with its own icon (e.g. one per twisted unit). */
    fun lines(event: GameEvent, state: PlayerGameState): List<LogLine> = when (event) {
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
            val hits = event.results.filterIsInstance<AttackResult.Hit>()
            val damage = hits.sumOf { it.damageApplied }
            val summary = "Attacks: $fired fired, ${hits.size} hit, $damage damage"
            val destroyed = destroyedClause(hits.map { it.targetId to it.damage }, state)
            val text = if (destroyed == null) summary else "$summary — $destroyed"
            val icon = if (hits.any { r -> r.damage.any { it.destroyed } }) locationDestroyedIcon() else null
            val lines = mutableListOf(LogLine(icon, text))
            // For every hit, append a detail line showing the location(s) struck (and missile count for clusters).
            hits.forEach { result -> lines.add(hitDetailLine(result)) }
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
            val hits = event.results.filterIsInstance<PhysicalAttackResult.Hit>()
            val damage = hits.sumOf { it.damageApplied }
            val summary = "Physical attacks: $made made, ${hits.size} hit, $damage damage"
            val destroyed = destroyedClause(hits.map { it.targetId to it.damage }, state)
            val text = if (destroyed == null) summary else "$summary — $destroyed"
            val icon = if (hits.any { r -> r.damage.any { it.destroyed } }) locationDestroyedIcon() else null
            val lines = mutableListOf(LogLine(icon, text))
            hits.forEach { result -> lines.add(physicalDetailLine(result)) }
            lines
        }
        is UnitFell -> {
            val name = event.unitId.value
            listOf(LogLine(unitFellIcon(), "$name fell — ${event.fall.damage} damage"))
        }
        is UnitStoodUp -> {
            val name = event.unitId.value
            listOf(LogLine(unitStoodUpIcon(), if (event.stoodUp) "$name stood up" else "$name failed to stand"))
        }
        is UnitShutdown -> {
            val name = event.unitId.value
            val text = when (event) {
                is UnitShutdown.Automatic -> "$name auto-shut down (heat ≥ 30)"
                is UnitShutdown.AvoidFailed -> "$name shut down from heat"
                is UnitShutdown.Undisclosed -> "$name shut down"
            }
            listOf(LogLine(null, text))
        }
        is UnitRestarted -> {
            val name = event.unitId.value
            listOf(LogLine(null, "$name restarted"))
        }
        is AmmoExploded -> {
            val name = event.unitId.value
            val text = when (event) {
                is AmmoExploded.Detailed -> "$name ammo explosion: ${event.ammoType.name} (${event.damage} damage)"
                is AmmoExploded.Undisclosed -> "$name ammo explosion (${event.damage} damage)"
            }
            listOf(LogLine(ammoExplosionIcon(), text))
        }
        is UnitDestroyed -> {
            val name = event.unitId.value
            listOf(LogLine(destroyedIcon(), "$name destroyed (${destructionReasonLabel(event.reason)})"))
        }
        is MatchEnded -> {
            val text = when (val outcome = event.outcome) {
                is MatchOutcome.Draw -> "Match over — draw"
                is MatchOutcome.Victory -> "Match over — ${playerLabel(outcome.winner)} wins!"
            }
            listOf(LogLine(null, text))
        }
        is CriticalHit -> {
            val name = event.unitId.value
            when (event) {
                is CriticalHit.Detailed -> {
                    val component = criticalSlotContentLabel(event.content, event.unitId, state)
                    listOf(LogLine(criticalHitIcon(event.content), "$name critical hit: $component in ${locationLabel(event.location)}"))
                }
                is CriticalHit.Undisclosed -> listOf(LogLine(undisclosedCriticalHitIcon(), "$name takes a critical hit"))
            }
        }
        is PilotHit -> {
            val name = event.unitId.value
            when (event) {
                // The 6th hit kills the pilot outright — call it out with its own line and
                // the pilot-dead skull, rather than folding it into the generic wounded text.
                is PilotHit.Fatal -> listOf(LogLine(pilotDeadIcon(), "$name pilot killed"))
                is PilotHit.Checked ->
                    listOf(LogLine(pilotWoundedIcon(), "$name pilot wounded (${event.pilotHits} hit${if (event.pilotHits == 1) "" else "s"} total)"))
                is PilotHit.Undisclosed -> listOf(LogLine(pilotWoundedIcon(), "$name pilot wounded"))
            }
        }
        is PilotKnockedUnconscious -> {
            val name = event.unitId.value
            listOf(LogLine(pilotUnconsciousIcon(), "$name pilot knocked unconscious"))
        }
        // Both leaves render identically: the recovery roll was never printed, so redacting
        // it is wire-only and costs no rendering fidelity (see GameEvent.redactFor's KDoc).
        is PilotRecoveredConsciousness -> {
            val name = event.unitId.value
            listOf(LogLine(pilotConsciousIcon(), "$name pilot regained consciousness"))
        }
        is SessionNotice -> listOf(LogLine(sessionNoticeIcon(), event.text))
    }

    /**
     * Returns a per-hit detail line for a weapon attack, e.g.
     * "LRM 20: 16 missiles (16 dmg) → Center Torso (5 dmg), Right Torso (5 dmg), Left Arm (5 dmg), Right Arm (1 dmg)"
     * for a cluster weapon, or "Medium Laser → Center Torso (5 dmg)" for a single-shot weapon.
     */
    private fun hitDetailLine(result: AttackResult.Hit): LogLine {
        val total = result.locationHits.sumOf { it.damage }
        if (result is AttackResult.ClusterHit) {
            val groupParts = result.locationHits.joinToString(", ") { "${locationLabel(it.location)} (${it.damage} dmg)" }
            return LogLine(attackOutcomeIcon(hit = true), "${result.weaponName}: ${result.missilesHit} missiles ($total dmg) → $groupParts")
        }
        val hit = result.locationHits.first()
        return LogLine(attackOutcomeIcon(hit = true), "${result.weaponName} → ${locationLabel(hit.location)} (${hit.damage} dmg)")
    }

    /** Returns a detail line for a physical attack hit, e.g. "Punch → Right Torso (8 dmg)". */
    private fun physicalDetailLine(result: PhysicalAttackResult.Hit): LogLine =
        LogLine(attackOutcomeIcon(hit = true), "${result.attackName} → ${locationLabel(result.hitLocation)} (${result.damageApplied} dmg)")

    private fun torsoFacingLines(event: TorsoFacingsApplied, state: PlayerGameState): List<LogLine> {
        if (event.facings.isEmpty()) return listOf(LogLine(null, "Torso facings: no changes"))
        return event.facings.entries.map { (unitId, dir) ->
            val name = unitId.value
            LogLine(torsoArrowIcon(dir).first, "$name torso → $dir")
        }
    }

    private fun attackDeclarationLines(event: AttackDeclarationsRecorded, state: PlayerGameState): List<LogLine> =
        event.declarations.groupBy { it.attackerId }.entries.map { (attackerId, decls) ->
            val attacker = state.units.byId(attackerId)
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

    private fun criticalSlotContentLabel(content: CriticalSlotContent, unitId: UnitId, state: PlayerGameState): String =
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
                state.units.byId(unitId).weapons.find { it.mountId == content.weaponId }?.name ?: "weapon"
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
        state: PlayerGameState,
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
