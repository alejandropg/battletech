package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.attack.AttackResult
import io.archinaut.battletech.tactical.attack.LocationDamage
import io.archinaut.battletech.tactical.attack.physical.PhysicalAttackResult
import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.MatchOutcome
import io.archinaut.battletech.tactical.model.MechLocation
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.model.content.AssetKind
import io.archinaut.battletech.tactical.query.PlayerGameState
import io.archinaut.battletech.tactical.session.AmmoExploded
import io.archinaut.battletech.tactical.session.AssetConflict
import io.archinaut.battletech.tactical.session.AttackDeclarationsRecorded
import io.archinaut.battletech.tactical.session.AttacksResolved
import io.archinaut.battletech.tactical.session.CriticalHit
import io.archinaut.battletech.tactical.session.GameEvent
import io.archinaut.battletech.tactical.session.HeatDissipated
import io.archinaut.battletech.tactical.session.HostConnectionLost
import io.archinaut.battletech.tactical.session.InitiativeRolled
import io.archinaut.battletech.tactical.session.MapIdentified
import io.archinaut.battletech.tactical.session.MatchEnded
import io.archinaut.battletech.tactical.session.PhaseChanged
import io.archinaut.battletech.tactical.session.PhysicalAttacksResolved
import io.archinaut.battletech.tactical.session.PilotHit
import io.archinaut.battletech.tactical.session.PilotKnockedUnconscious
import io.archinaut.battletech.tactical.session.PilotRecoveredConsciousness
import io.archinaut.battletech.tactical.session.PlayerConnected
import io.archinaut.battletech.tactical.session.PlayerDisconnected
import io.archinaut.battletech.tactical.session.SessionNotice
import io.archinaut.battletech.tactical.session.SessionOpened
import io.archinaut.battletech.tactical.session.TorsoFacingsApplied
import io.archinaut.battletech.tactical.session.TurnEnded
import io.archinaut.battletech.tactical.session.UnitDestroyed
import io.archinaut.battletech.tactical.session.UnitFell
import io.archinaut.battletech.tactical.session.UnitMoved
import io.archinaut.battletech.tactical.session.UnitRestarted
import io.archinaut.battletech.tactical.session.UnitShutdown
import io.archinaut.battletech.tactical.session.UnitStoodUp
import io.archinaut.battletech.tactical.unit.DestructionReason
import io.archinaut.battletech.tactical.unit.UnitId
import io.archinaut.battletech.tui.icon.ammoExplosionIcon
import io.archinaut.battletech.tui.icon.attackOutcomeIcon
import io.archinaut.battletech.tui.icon.attacksResolvedIcon
import io.archinaut.battletech.tui.icon.criticalHitIcon
import io.archinaut.battletech.tui.icon.destroyedIcon
import io.archinaut.battletech.tui.icon.heatChangeIcon
import io.archinaut.battletech.tui.icon.initiativeIcon
import io.archinaut.battletech.tui.icon.locationDestroyedIcon
import io.archinaut.battletech.tui.icon.mapMismatchIcon
import io.archinaut.battletech.tui.icon.mapNoticeIcon
import io.archinaut.battletech.tui.icon.matchEndedIcon
import io.archinaut.battletech.tui.icon.mechModelMismatchIcon
import io.archinaut.battletech.tui.icon.movementModeIcon
import io.archinaut.battletech.tui.icon.physicalAttacksResolvedIcon
import io.archinaut.battletech.tui.icon.pilotConsciousIcon
import io.archinaut.battletech.tui.icon.pilotDeadIcon
import io.archinaut.battletech.tui.icon.pilotUnconsciousIcon
import io.archinaut.battletech.tui.icon.pilotWoundedIcon
import io.archinaut.battletech.tui.icon.sessionNoticeIcon
import io.archinaut.battletech.tui.icon.targetIcon
import io.archinaut.battletech.tui.icon.torsoArrowIcon
import io.archinaut.battletech.tui.icon.undisclosedCriticalHitIcon
import io.archinaut.battletech.tui.icon.unitFellIcon
import io.archinaut.battletech.tui.icon.unitRestartedIcon
import io.archinaut.battletech.tui.icon.unitShutdownIcon
import io.archinaut.battletech.tui.icon.unitStoodUpIcon
import io.archinaut.tenter.screen.Line
import io.archinaut.tenter.screen.Style

internal object GameLogFormatter {

    data class LogLine(val icon: String, val content: Line) {
        constructor(icon: String, text: String) : this(icon, Line(text))

        /** Plain-text projection for un-styled consumers ([io.archinaut.battletech.tui.GameEventPrinter], assertions). */
        val text: String get() = content.spans.joinToString("") { it.text }
    }

    /** Renders an event as one or more log lines, each with its own icon (e.g. one per twisted unit). */
    fun lines(event: GameEvent, state: PlayerGameState): List<LogLine> = when (event) {
        is PhaseChanged -> emptyList()
        is TurnEnded -> emptyList()
        is TorsoFacingsApplied -> torsoFacingLines(event, state)
        is AttackDeclarationsRecorded -> attackDeclarationLines(event, state)
        is InitiativeRolled -> {
            val p1 = event.initiative.rolls[PlayerId.PLAYER_1]!!
            val p2 = event.initiative.rolls[PlayerId.PLAYER_2]!!
            listOf(
                LogLine(
                    initiativeIcon(),
                    Line("Initiative: ") +
                        playerName(PlayerId.PLAYER_1) + Line(" ${diceRollLabel(p1)}, ") +
                        playerName(PlayerId.PLAYER_2) + Line(" ${diceRollLabel(p2)} — ") +
                        playerName(event.initiative.loser) + Line(" moves first"),
                ),
            )
        }
        is UnitMoved -> listOf(
            LogLine(
                movementModeIcon(event.mode),
                unitName(event.unitId, state) + Line(" (${event.mpSpent} MP) ${hexLabel(event.from)}→${hexLabel(event.to)}"),
            ),
        )
        is AttacksResolved -> {
            val fired = event.results.size
            val hits = event.results.filterIsInstance<AttackResult.Hit>()
            val damage = hits.sumOf { it.damageApplied }
            val destroyed = destroyedClause(hits.map { it.targetId to it.damage }, state)
            val summary = Line("Attacks: $fired fired, ${hits.size} hit, $damage damage")
            val content = if (destroyed == null) summary else summary + Line(" — ") + destroyed
            val icon = if (hits.any { r -> r.damage.any { it.destroyed } }) locationDestroyedIcon() else attacksResolvedIcon()
            val lines = mutableListOf(LogLine(icon, content))
            // For every hit, append a detail line showing the location(s) struck (and missile count for clusters).
            hits.forEach { result -> lines.add(hitDetailLine(result, state)) }
            lines
        }
        is HeatDissipated -> {
            val parts = event.heatBefore
                .filterValues { it > 0 }
                .map { (unitId, before) ->
                    unitName(unitId, state) + Line(" $before→${event.heatAfter[unitId] ?: 0}")
                }
            val content = if (parts.isEmpty()) Line("Heat: no heat to dissipate") else Line("Heat: ") + parts.joinLines(", ")
            val before = event.heatBefore.values.sum()
            val after = event.heatAfter.values.sum()
            listOf(LogLine(heatChangeIcon(wentUp = after > before), content))
        }
        is PhysicalAttacksResolved -> {
            val made = event.results.size
            val hits = event.results.filterIsInstance<PhysicalAttackResult.Hit>()
            val damage = hits.sumOf { it.damageApplied }
            val destroyed = destroyedClause(hits.map { it.targetId to it.damage }, state)
            val summary = Line("Physical attacks: $made made, ${hits.size} hit, $damage damage")
            val content = if (destroyed == null) summary else summary + Line(" — ") + destroyed
            val icon = if (hits.any { r -> r.damage.any { it.destroyed } }) locationDestroyedIcon() else physicalAttacksResolvedIcon()
            val lines = mutableListOf(LogLine(icon, content))
            hits.forEach { result -> lines.add(physicalDetailLine(result, state)) }
            lines
        }
        is UnitFell -> listOf(
            LogLine(unitFellIcon(), unitName(event.unitId, state) + Line(" fell — ${event.fall.damage} damage")),
        )
        is UnitStoodUp -> listOf(
            LogLine(
                unitStoodUpIcon(),
                unitName(event.unitId, state) + Line(if (event.stoodUp) " stood up" else " failed to stand"),
            ),
        )
        is UnitShutdown -> {
            val suffix = when (event) {
                is UnitShutdown.Automatic -> " auto-shut down (heat ≥ 30)"
                is UnitShutdown.AvoidFailed -> " shut down from heat"
                is UnitShutdown.Undisclosed -> " shut down"
            }
            listOf(LogLine(unitShutdownIcon(), unitName(event.unitId, state) + Line(suffix)))
        }
        is UnitRestarted -> listOf(
            LogLine(unitRestartedIcon(), unitName(event.unitId, state) + Line(" restarted")),
        )
        is AmmoExploded -> {
            val suffix = when (event) {
                is AmmoExploded.Detailed -> " ammo explosion: ${event.ammoType.name} (${event.damage} damage)"
                is AmmoExploded.Undisclosed -> " ammo explosion (${event.damage} damage)"
            }
            listOf(LogLine(ammoExplosionIcon(), unitName(event.unitId, state) + Line(suffix)))
        }
        is UnitDestroyed -> listOf(
            LogLine(
                destroyedIcon(),
                unitName(event.unitId, state) + Line(" destroyed (${destructionReasonLabel(event.reason)})"),
            ),
        )
        is MatchEnded -> when (val outcome = event.outcome) {
            is MatchOutcome.Draw -> listOf(LogLine(matchEndedIcon(), "Match over — draw"))
            is MatchOutcome.Victory -> listOf(
                LogLine(
                    matchEndedIcon(),
                    Line("Match over — ") + playerName(outcome.winner) + Line(" wins!"),
                ),
            )
        }
        is CriticalHit -> when (event) {
            is CriticalHit.Detailed -> {
                val component = MechLabels.criticalSlotContent(event.content) { state.units.byId(event.unitId).weapons }
                listOf(
                    LogLine(
                        criticalHitIcon(event.content),
                        unitName(event.unitId, state) + Line(" critical hit: $component in ${MechLabels.location(event.location)}"),
                    ),
                )
            }
            is CriticalHit.Undisclosed -> listOf(
                LogLine(undisclosedCriticalHitIcon(), unitName(event.unitId, state) + Line(" takes a critical hit")),
            )
        }
        is PilotHit -> when (event) {
            // The 6th hit kills the pilot outright — call it out with its own line and
            // the pilot-dead skull, rather than folding it into the generic wounded text.
            is PilotHit.Fatal -> listOf(
                LogLine(pilotDeadIcon(), unitName(event.unitId, state) + Line(" pilot killed")),
            )
            is PilotHit.Checked -> listOf(
                LogLine(
                    pilotWoundedIcon(),
                    unitName(event.unitId, state) + Line(" pilot wounded (${event.pilotHits} hit${if (event.pilotHits == 1) "" else "s"} total)"),
                ),
            )
            is PilotHit.Undisclosed -> listOf(
                LogLine(pilotWoundedIcon(), unitName(event.unitId, state) + Line(" pilot wounded")),
            )
        }
        is PilotKnockedUnconscious -> listOf(
            LogLine(pilotUnconsciousIcon(), unitName(event.unitId, state) + Line(" pilot knocked unconscious")),
        )
        // Both leaves render identically: the recovery roll was never printed, so redacting
        // it is wire-only and costs no rendering fidelity (see GameEvent.redactFor's KDoc).
        is PilotRecoveredConsciousness -> listOf(
            LogLine(pilotConsciousIcon(), unitName(event.unitId, state) + Line(" pilot regained consciousness")),
        )
        is SessionNotice -> listOf(LogLine(sessionNoticeIcon(), event.text))
        is PlayerConnected -> listOf(
            LogLine(sessionNoticeIcon(), playerName(event.player) + Line(" connected")),
        )
        is PlayerDisconnected -> listOf(
            LogLine(sessionNoticeIcon(), playerName(event.player) + Line(" disconnected — waiting for rejoin…")),
        )
        is SessionOpened -> listOf(LogLine(sessionNoticeIcon(), "Session ID: ${event.sessionId}"))
        is HostConnectionLost ->
            listOf(LogLine(sessionNoticeIcon(), "Disconnected from host — restart with 'battletech-tui join <host> --session <id>' to rejoin"))
        is MapIdentified -> listOf(LogLine(mapNoticeIcon(), "Map: ${event.name}"))
        is AssetConflict -> {
            val icon = if (event.kind == AssetKind.MAP) mapMismatchIcon() else mechModelMismatchIcon()
            val noun = if (event.kind == AssetKind.MAP) "map" else "mech model"
            listOf(
                LogLine(
                    icon,
                    playerName(event.player) + Line("'s local $noun '${event.id}' differs from the registered one — using the registered $noun"),
                ),
            )
        }
    }

    /**
     * Returns a per-hit detail line for a weapon attack, e.g.
     * "LRM 20: 16 missiles (16 dmg) → Center Torso (5 dmg), Right Torso (5 dmg), Left Arm (5 dmg), Right Arm (1 dmg)"
     * for a cluster weapon, or "Medium Laser → Center Torso (5 dmg)" for a single-shot weapon.
     */
    private fun hitDetailLine(result: AttackResult.Hit, state: PlayerGameState): LogLine {
        val total = result.locationHits.sumOf { it.damage }
        val weapon = playerColored(result.weaponName, result.attackerId, state)
        if (result is AttackResult.ClusterHit) {
            val groupParts = result.locationHits.joinToString(", ") { "${locationLabel(it.location)} (${it.damage} dmg)" }
            return LogLine(
                attackOutcomeIcon(hit = true),
                weapon + Line(": ${result.missilesHit} missiles ($total dmg) → $groupParts"),
            )
        }
        val hit = result.locationHits.first()
        return LogLine(
            attackOutcomeIcon(hit = true),
            weapon + Line(" → ${locationLabel(hit.location)} (${hit.damage} dmg)"),
        )
    }

    /** Returns a detail line for a physical attack hit, e.g. "Punch → Right Torso (8 dmg)". */
    private fun physicalDetailLine(result: PhysicalAttackResult.Hit, state: PlayerGameState): LogLine =
        LogLine(
            attackOutcomeIcon(hit = true),
            playerColored(result.attackName, result.attackerId, state) + Line(" → ${locationLabel(result.hitLocation)} (${result.damageApplied} dmg)"),
        )

    private fun torsoFacingLines(event: TorsoFacingsApplied, state: PlayerGameState): List<LogLine> =
        event.facings.entries.map { (unitId, dir) ->
            LogLine(torsoArrowIcon(dir).first, unitName(unitId, state) + Line(" torso → $dir"))
        }

    private fun attackDeclarationLines(event: AttackDeclarationsRecorded, state: PlayerGameState): List<LogLine> =
        event.declarations.groupBy { it.attackerId }.entries.map { (attackerId, decls) ->
            val attacker = state.units.byId(attackerId)
            val perTarget = decls.groupBy { it.targetId }.map { (targetId, targetDecls) ->
                val weaponNames = targetDecls.joinToString(", ") { decl ->
                    attacker.weapons.getOrNull(decl.weaponIndex)?.name ?: "weapon#${decl.weaponIndex}"
                }
                unitName(targetId, state) + Line(" ($weaponNames)")
            }
            LogLine(
                targetIcon(),
                unitName(attackerId, state) + Line(" → ") + perTarget.joinLines(", "),
            )
        }

    private fun destructionReasonLabel(reason: DestructionReason): String = when (reason) {
        DestructionReason.HEAD_DESTROYED -> "head destroyed"
        DestructionReason.CENTER_TORSO_DESTROYED -> "center torso destroyed"
        DestructionReason.BOTH_LEGS_DESTROYED -> "both legs destroyed"
        DestructionReason.ENGINE_DESTROYED -> "engine destroyed"
        DestructionReason.PILOT_DEAD -> "pilot dead"
    }

    /** A unit's name in its owner's board color. */
    private fun unitName(unitId: UnitId, state: PlayerGameState): Line =
        playerColored(unitId.value, unitId, state)

    /** [text] in the color of the player owning [unitId] — the default style if [unitId] isn't in the roster. */
    private fun playerColored(text: String, unitId: UnitId, state: PlayerGameState): Line =
        owner(unitId, state)
            ?.let { Line(text, Style(playerColor(it))) }
            ?: Line(text)

    /** A seat label ("P1"/"P2") in that seat's board color. */
    private fun playerName(player: PlayerId): Line =
        Line(playerLabel(player), Style(playerColor(player)))

    /**
     * Deliberately not [PlayerGameState.units]' `byId` — that throws `UnknownUnitException` on
     * an id it doesn't hold, and a log line naming a unit is not worth crashing a render over.
     * Destroyed units do stay in the roster, so the miss is not a normal case; an uncolored
     * name is the right degradation for the abnormal one.
     */
    private fun owner(unitId: UnitId, state: PlayerGameState): PlayerId? =
        state.units.all.firstOrNull { it.id == unitId }?.owner

    private fun hexLabel(coord: HexCoordinates): String =
        "%02d%02d".format(coord.col + 1, coord.row + 1)

    private fun destroyedClause(targetsAndDamage: List<Pair<UnitId, List<LocationDamage>>>, state: PlayerGameState): Line? {
        val parts = targetsAndDamage.flatMap { (targetId, steps) ->
            steps.filter { it.destroyed }.map { step ->
                unitName(targetId, state) + Line(" ${locationLabel(step.location)}")
            }
        }
        if (parts.isEmpty()) return null
        return parts.joinLines(", ") + Line(" destroyed")
    }

    private fun locationLabel(location: MechLocation): String = MechLabels.location(location)

    /** [joinToString] for styled fragments; the separator is unstyled. */
    private fun List<Line>.joinLines(separator: String): Line =
        reduce { joined, part -> joined + Line(separator) + part }
}
