package battletech.tui

import battletech.tactical.action.PlayerId
import battletech.tactical.action.TurnPhase
import battletech.tactical.event.AttacksResolved
import battletech.tactical.event.GameEvent
import battletech.tactical.event.HeatDissipated
import battletech.tactical.event.InitiativeRolled
import battletech.tactical.event.PhaseChanged
import battletech.tactical.event.TurnEnded
import battletech.tactical.model.GameStateFactory
import battletech.tactical.model.HexCoordinates
import battletech.tactical.session.BattleSession
import battletech.tactical.session.TurnState
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.mapToTuiPhase
import battletech.tui.input.InputMapper
import battletech.tui.screen.ScreenBuffer
import battletech.tui.screen.ScreenRenderer
import battletech.tui.view.BoardView
import battletech.tui.view.SidebarView
import battletech.tui.view.StatusBarView
import battletech.tui.view.TargetsView
import battletech.tui.view.Viewport
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.enterRawMode
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal

public class TuiApp {

    public fun run() {
        val terminal = Terminal()
        val renderer = ScreenRenderer(terminal)

        val session = BattleSession(
            initialGameState = GameStateFactory().sampleGameState(),
            initialTurnState = TurnState.NULL,
        )

        // Domain events flow through the subscription seam. In hot-seat
        // play we subscribe the active player's listener to a flash queue;
        // the other player's subscription is wired but inert so the
        // multi-subscriber path is exercised at runtime. A future remote
        // client uses the exact same seam.
        val capturedEvents = mutableListOf<GameEvent>()
        session.subscribe(PlayerId.PLAYER_1) { capturedEvents += it }
        session.subscribe(PlayerId.PLAYER_2) { /* second hot-seat view: presently no-op */ }

        // Kickstart cascades INITIATIVE → MOVEMENT; the subscription
        // captures the cascade events.
        session.advance()
        var appState = AppState(
            session = session,
            phase = mapToTuiPhase(session.currentPhase),
            cursor = HexCoordinates(0, 0),
            pendingFlashes = drainFlashes(capturedEvents),
        )

        renderer.clear()

        try {
            terminal.enterRawMode(mouseTracking = MouseTracking.Normal).use { rawMode ->
                while (true) {
                    val currentSize = currentSize(terminal)

                    // Drain queued flashes one frame at a time so each
                    // cascade event lands as a discrete on-screen message.
                    if (appState.pendingFlashes.isNotEmpty()) {
                        val head = appState.pendingFlashes.first()
                        appState = appState.copy(pendingFlashes = appState.pendingFlashes.drop(1))
                        renderFrame(currentSize, renderer, appState, head)
                        val event = rawMode.readEvent()
                        if (event is KeyboardEvent && InputMapper.isQuit(event)) break
                        continue
                    }

                    renderFrame(currentSize, renderer, appState)

                    val event = rawMode.readEvent()
                    if (event is KeyboardEvent && InputMapper.isQuit(event)) break

                    val transition = appState.phase.handle(event, appState) ?: continue

                    // Append domain flashes (from subscription) and the
                    // UI-internal flash (from Transition) to the queue,
                    // in that order — domain first so phase rejections
                    // ("Not your unit") show last.
                    val newFlashes = drainFlashes(capturedEvents) +
                        listOfNotNull(transition.flash)
                    appState = transition.app.copy(
                        pendingFlashes = transition.app.pendingFlashes + newFlashes,
                    )
                }
            }
        } finally {
            renderer.cleanup()
        }
    }

    private fun drainFlashes(events: MutableList<GameEvent>): List<FlashMessage> {
        val flashes = events.mapNotNull(::eventToFlash)
        events.clear()
        return flashes
    }

    private fun currentSize(terminal: Terminal): Size {
        val size = terminal.updateSize()
        check(size.width > 0) { "Terminal width must be positive, got: $size" }
        check(size.height > 0) { "Terminal height must be positive, got: $size" }
        return Size(size.width, size.height)
    }

    private fun renderFrame(
        size: Size,
        renderer: ScreenRenderer,
        appState: AppState,
        flash: FlashMessage? = null,
    ) {
        val sidebarWidth = 28
        val statusBarHeight = 7

        val attackRender = appState.phase.attackRender(appState.gameState)
        val hasTargets = attackRender?.targets?.isNotEmpty() == true
        val targetsWidth = if (hasTargets) 28 else 0
        val boardWidth = size.width - sidebarWidth - targetsWidth
        val boardHeight = size.height - statusBarHeight

        val buffer = ScreenBuffer(size.width, size.height)
        val viewport = Viewport(0, 0, boardWidth - 4, boardHeight - 4)

        val renderData = appState.phase.render(appState.gameState)
        val selectedUnit = appState.phase.selectedUnit(appState)
        val pathDestination = appState.phase.pathDestination()
        val movementMode = appState.phase.movementMode()

        val boardView = BoardView(
            appState.gameState,
            viewport,
            cursorPosition = appState.cursor,
            hexHighlights = renderData.hexHighlights,
            reachableFacings = renderData.reachableFacings,
            facingSelectionFacings = renderData.facingSelection?.facings,
            pathDestination = pathDestination,
            movementMode = movementMode,
            torsoFacings = renderData.torsoFacings,
            validTargetPositions = renderData.validTargetPositions,
        )
        boardView.render(buffer, 0, 0, boardWidth, boardHeight)

        if (attackRender != null && hasTargets) {
            val targetsView = TargetsView(
                targets = attackRender.targets,
                weaponAssignments = attackRender.weaponAssignments,
                primaryTargetId = attackRender.primaryTargetId,
                cursorTargetIndex = attackRender.cursorTargetIndex,
                cursorWeaponIndex = attackRender.cursorWeaponIndex,
            )
            targetsView.render(buffer, boardWidth, 0, targetsWidth, boardHeight)
        }

        val sidebarView = SidebarView(unit = selectedUnit)
        sidebarView.render(buffer, boardWidth + targetsWidth, 0, sidebarWidth, boardHeight)

        val prompt = flash?.text ?: appState.phase.prompt(appState)
        val activePlayerInfo = appState.phase.activePlayerLabel(appState)
        val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
        statusBarView.render(buffer, 0, boardHeight, size.width, statusBarHeight)

        renderer.render(buffer)
    }

    private companion object {
        private fun eventToFlash(event: GameEvent): FlashMessage? = when (event) {
            is InitiativeRolled -> {
                val p1 = event.initiative.rolls[PlayerId.PLAYER_1]!!
                val p2 = event.initiative.rolls[PlayerId.PLAYER_2]!!
                val loserName = if (event.initiative.loser == PlayerId.PLAYER_1) "P1" else "P2"
                FlashMessage("Initiative: P1 rolled $p1, P2 rolled $p2 — $loserName moves first")
            }
            is HeatDissipated -> {
                val details = event.heatBefore
                    .filterValues { it > 0 }
                    .map { (id, before) -> "${id.value}: $before→${event.heatAfter[id] ?: 0}" }
                    .joinToString(", ")
                    .ifEmpty { "No heat to dissipate" }
                FlashMessage("Heat: $details")
            }
            is TurnEnded -> FlashMessage("Turn complete")
            is AttacksResolved -> {
                val hits = event.results.count { r -> r.hit }
                val damage = event.results.sumOf { r -> r.damageApplied }
                FlashMessage("Attacks resolved: ${event.results.size} attacks, $hits hits, $damage damage")
            }
            is PhaseChanged -> when (event.to) {
                TurnPhase.WEAPON_ATTACK -> FlashMessage("Weapon Attack Phase")
                TurnPhase.PHYSICAL_ATTACK -> FlashMessage("Physical Attack Phase")
                else -> null
            }
            else -> null
        }
    }
}
