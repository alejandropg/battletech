package battletech.tui.view

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.LocationDamage
import battletech.tactical.attack.physical.PhysicalAttackResult
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.MechLocation
import battletech.tactical.model.PlayerId
import battletech.tactical.model.content.AssetKind
import battletech.tactical.query.PlayerGameState
import battletech.tactical.session.AmmoExploded
import battletech.tactical.session.AssetConflict
import battletech.tactical.session.AttackDeclarationsRecorded
import battletech.tactical.session.AttacksResolved
import battletech.tactical.session.CriticalHit
import battletech.tactical.session.GameEvent
import battletech.tactical.session.HeatDissipated
import battletech.tactical.session.HostConnectionLost
import battletech.tactical.session.InitiativeRolled
import battletech.tactical.session.MapIdentified
import battletech.tactical.session.MatchEnded
import battletech.tactical.session.PhaseChanged
import battletech.tactical.session.PhysicalAttacksResolved
import battletech.tactical.session.PilotHit
import battletech.tactical.session.PilotKnockedUnconscious
import battletech.tactical.session.PilotRecoveredConsciousness
import battletech.tactical.session.PlayerConnected
import battletech.tactical.session.PlayerDisconnected
import battletech.tactical.session.SessionNotice
import battletech.tactical.session.SessionOpened
import battletech.tactical.session.TorsoFacingsApplied
import battletech.tactical.session.TurnEnded
import battletech.tactical.session.UnitDestroyed
import battletech.tactical.session.UnitFell
import battletech.tactical.session.UnitMoved
import battletech.tactical.session.UnitRestarted
import battletech.tactical.session.UnitShutdown
import battletech.tactical.session.UnitStoodUp
import battletech.tactical.unit.DestructionReason
import battletech.tactical.unit.UnitId
import battletech.tui.icon.ammoExplosionIcon
import battletech.tui.icon.attackOutcomeIcon
import battletech.tui.icon.attacksResolvedIcon
import battletech.tui.icon.criticalHitIcon
import battletech.tui.icon.destroyedIcon
import battletech.tui.icon.heatChangeIcon
import battletech.tui.icon.initiativeIcon
import battletech.tui.icon.locationDestroyedIcon
import battletech.tui.icon.mapMismatchIcon
import battletech.tui.icon.mapNoticeIcon
import battletech.tui.icon.matchEndedIcon
import battletech.tui.icon.mechModelMismatchIcon
import battletech.tui.icon.movementModeIcon
import battletech.tui.icon.physicalAttacksResolvedIcon
import battletech.tui.icon.pilotConsciousIcon
import battletech.tui.icon.pilotDeadIcon
import battletech.tui.icon.pilotUnconsciousIcon
import battletech.tui.icon.pilotWoundedIcon
import battletech.tui.icon.sessionNoticeIcon
import battletech.tui.icon.targetIcon
import battletech.tui.icon.torsoArrowIcon
import battletech.tui.icon.undisclosedCriticalHitIcon
import battletech.tui.icon.unitFellIcon
import battletech.tui.icon.unitRestartedIcon
import battletech.tui.icon.unitShutdownIcon
import battletech.tui.icon.unitStoodUpIcon
import tenter.screen.StyledText
import tenter.screen.joinStyled
import tenter.screen.styled

internal object GameLogFormatter {

    data class LogLine(val icon: String, val content: StyledText) {
        constructor(icon: String, text: String) : this(icon, StyledText.of(text))

        /** Plain-text projection for un-styled consumers ([battletech.tui.GameEventPrinter], assertions). */
        val text: String get() = content.plain
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
                    styled {
                        append("Initiative: ")
                        append(playerName(PlayerId.PLAYER_1))
                        append(" ${diceRollLabel(p1)}, ")
                        append(playerName(PlayerId.PLAYER_2))
                        append(" ${diceRollLabel(p2)} — ")
                        append(playerName(event.initiative.loser))
                        append(" moves first")
                    },
                ),
            )
        }
        is UnitMoved -> listOf(
            LogLine(
                movementModeIcon(event.mode),
                styled {
                    append(unitName(event.unitId, state))
                    append(" (${event.mpSpent} MP) ${hexLabel(event.from)}→${hexLabel(event.to)}")
                },
            ),
        )
        is AttacksResolved -> {
            val fired = event.results.size
            val hits = event.results.filterIsInstance<AttackResult.Hit>()
            val damage = hits.sumOf { it.damageApplied }
            val destroyed = destroyedClause(hits.map { it.targetId to it.damage }, state)
            val content = styled {
                append("Attacks: $fired fired, ${hits.size} hit, $damage damage")
                if (destroyed != null) {
                    append(" — ")
                    append(destroyed)
                }
            }
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
                    styled {
                        append(unitName(unitId, state))
                        append(" $before→${event.heatAfter[unitId] ?: 0}")
                    }
                }
            val content = if (parts.isEmpty()) StyledText.of("Heat: no heat to dissipate") else styled {
                append("Heat: ")
                append(parts.joinStyled(", "))
            }
            val before = event.heatBefore.values.sum()
            val after = event.heatAfter.values.sum()
            listOf(LogLine(heatChangeIcon(wentUp = after > before), content))
        }
        is PhysicalAttacksResolved -> {
            val made = event.results.size
            val hits = event.results.filterIsInstance<PhysicalAttackResult.Hit>()
            val damage = hits.sumOf { it.damageApplied }
            val destroyed = destroyedClause(hits.map { it.targetId to it.damage }, state)
            val content = styled {
                append("Physical attacks: $made made, ${hits.size} hit, $damage damage")
                if (destroyed != null) {
                    append(" — ")
                    append(destroyed)
                }
            }
            val icon = if (hits.any { r -> r.damage.any { it.destroyed } }) locationDestroyedIcon() else physicalAttacksResolvedIcon()
            val lines = mutableListOf(LogLine(icon, content))
            hits.forEach { result -> lines.add(physicalDetailLine(result, state)) }
            lines
        }
        is UnitFell -> listOf(
            LogLine(unitFellIcon(), styled { append(unitName(event.unitId, state)); append(" fell — ${event.fall.damage} damage") }),
        )
        is UnitStoodUp -> listOf(
            LogLine(
                unitStoodUpIcon(),
                styled {
                    append(unitName(event.unitId, state))
                    append(if (event.stoodUp) " stood up" else " failed to stand")
                },
            ),
        )
        is UnitShutdown -> {
            val suffix = when (event) {
                is UnitShutdown.Automatic -> " auto-shut down (heat ≥ 30)"
                is UnitShutdown.AvoidFailed -> " shut down from heat"
                is UnitShutdown.Undisclosed -> " shut down"
            }
            listOf(LogLine(unitShutdownIcon(), styled { append(unitName(event.unitId, state)); append(suffix) }))
        }
        is UnitRestarted -> listOf(
            LogLine(unitRestartedIcon(), styled { append(unitName(event.unitId, state)); append(" restarted") }),
        )
        is AmmoExploded -> {
            val suffix = when (event) {
                is AmmoExploded.Detailed -> " ammo explosion: ${event.ammoType.name} (${event.damage} damage)"
                is AmmoExploded.Undisclosed -> " ammo explosion (${event.damage} damage)"
            }
            listOf(LogLine(ammoExplosionIcon(), styled { append(unitName(event.unitId, state)); append(suffix) }))
        }
        is UnitDestroyed -> listOf(
            LogLine(
                destroyedIcon(),
                styled {
                    append(unitName(event.unitId, state))
                    append(" destroyed (${destructionReasonLabel(event.reason)})")
                },
            ),
        )
        is MatchEnded -> when (val outcome = event.outcome) {
            is MatchOutcome.Draw -> listOf(LogLine(matchEndedIcon(), "Match over — draw"))
            is MatchOutcome.Victory -> listOf(
                LogLine(
                    matchEndedIcon(),
                    styled { append("Match over — "); append(playerName(outcome.winner)); append(" wins!") },
                ),
            )
        }
        is CriticalHit -> when (event) {
            is CriticalHit.Detailed -> {
                val component = MechLabels.criticalSlotContent(event.content) { state.units.byId(event.unitId).weapons }
                listOf(
                    LogLine(
                        criticalHitIcon(event.content),
                        styled {
                            append(unitName(event.unitId, state))
                            append(" critical hit: $component in ${MechLabels.location(event.location)}")
                        },
                    ),
                )
            }
            is CriticalHit.Undisclosed -> listOf(
                LogLine(undisclosedCriticalHitIcon(), styled { append(unitName(event.unitId, state)); append(" takes a critical hit") }),
            )
        }
        is PilotHit -> when (event) {
            // The 6th hit kills the pilot outright — call it out with its own line and
            // the pilot-dead skull, rather than folding it into the generic wounded text.
            is PilotHit.Fatal -> listOf(
                LogLine(pilotDeadIcon(), styled { append(unitName(event.unitId, state)); append(" pilot killed") }),
            )
            is PilotHit.Checked -> listOf(
                LogLine(
                    pilotWoundedIcon(),
                    styled {
                        append(unitName(event.unitId, state))
                        append(" pilot wounded (${event.pilotHits} hit${if (event.pilotHits == 1) "" else "s"} total)")
                    },
                ),
            )
            is PilotHit.Undisclosed -> listOf(
                LogLine(pilotWoundedIcon(), styled { append(unitName(event.unitId, state)); append(" pilot wounded") }),
            )
        }
        is PilotKnockedUnconscious -> listOf(
            LogLine(pilotUnconsciousIcon(), styled { append(unitName(event.unitId, state)); append(" pilot knocked unconscious") }),
        )
        // Both leaves render identically: the recovery roll was never printed, so redacting
        // it is wire-only and costs no rendering fidelity (see GameEvent.redactFor's KDoc).
        is PilotRecoveredConsciousness -> listOf(
            LogLine(pilotConsciousIcon(), styled { append(unitName(event.unitId, state)); append(" pilot regained consciousness") }),
        )
        is SessionNotice -> listOf(LogLine(sessionNoticeIcon(), event.text))
        is PlayerConnected -> listOf(
            LogLine(sessionNoticeIcon(), styled { append(playerName(event.player)); append(" connected") }),
        )
        is PlayerDisconnected -> listOf(
            LogLine(sessionNoticeIcon(), styled { append(playerName(event.player)); append(" disconnected — waiting for rejoin…") }),
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
                    styled {
                        append(playerName(event.player))
                        append("'s local $noun '${event.id}' differs from the registered one — using the registered $noun")
                    },
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
                styled { append(weapon); append(": ${result.missilesHit} missiles ($total dmg) → $groupParts") },
            )
        }
        val hit = result.locationHits.first()
        return LogLine(
            attackOutcomeIcon(hit = true),
            styled { append(weapon); append(" → ${locationLabel(hit.location)} (${hit.damage} dmg)") },
        )
    }

    /** Returns a detail line for a physical attack hit, e.g. "Punch → Right Torso (8 dmg)". */
    private fun physicalDetailLine(result: PhysicalAttackResult.Hit, state: PlayerGameState): LogLine =
        LogLine(
            attackOutcomeIcon(hit = true),
            styled {
                append(playerColored(result.attackName, result.attackerId, state))
                append(" → ${locationLabel(result.hitLocation)} (${result.damageApplied} dmg)")
            },
        )

    private fun torsoFacingLines(event: TorsoFacingsApplied, state: PlayerGameState): List<LogLine> =
        event.facings.entries.map { (unitId, dir) ->
            LogLine(torsoArrowIcon(dir).first, styled { append(unitName(unitId, state)); append(" torso → $dir") })
        }

    private fun attackDeclarationLines(event: AttackDeclarationsRecorded, state: PlayerGameState): List<LogLine> =
        event.declarations.groupBy { it.attackerId }.entries.map { (attackerId, decls) ->
            val attacker = state.units.byId(attackerId)
            val perTarget = decls.groupBy { it.targetId }.map { (targetId, targetDecls) ->
                val weaponNames = targetDecls.joinToString(", ") { decl ->
                    attacker.weapons.getOrNull(decl.weaponIndex)?.name ?: "weapon#${decl.weaponIndex}"
                }
                styled { append(unitName(targetId, state)); append(" ($weaponNames)") }
            }
            LogLine(
                targetIcon(),
                styled {
                    append(unitName(attackerId, state))
                    append(" → ")
                    append(perTarget.joinStyled(", "))
                },
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
    private fun unitName(unitId: UnitId, state: PlayerGameState): StyledText =
        playerColored(unitId.value, unitId, state)

    /** [text] in the color of the player owning [unitId] — the default style if [unitId] isn't in the roster. */
    private fun playerColored(text: String, unitId: UnitId, state: PlayerGameState): StyledText =
        owner(unitId, state)
            ?.let { StyledText.of(text, playerColor(it)) }
            ?: StyledText.of(text)

    /** A seat label ("P1"/"P2") in that seat's board color. */
    private fun playerName(player: PlayerId): StyledText =
        StyledText.of(playerLabel(player), playerColor(player))

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

    private fun destroyedClause(targetsAndDamage: List<Pair<UnitId, List<LocationDamage>>>, state: PlayerGameState): StyledText? {
        val parts = targetsAndDamage.flatMap { (targetId, steps) ->
            steps.filter { it.destroyed }.map { step ->
                styled { append(unitName(targetId, state)); append(" ${locationLabel(step.location)}") }
            }
        }
        if (parts.isEmpty()) return null
        return styled { append(parts.joinStyled(", ")); append(" destroyed") }
    }

    private fun locationLabel(location: MechLocation): String = MechLabels.location(location)
}
