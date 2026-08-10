package battletech.tui.view

import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.PlayerId
import battletech.tui.game.AppState
import battletech.tui.game.FlashMessage
import battletech.tui.game.PanelId
import battletech.tui.game.PanelVisibility
import battletech.tui.input.KeyGlyph
import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

private val WHITE_STYLE = Cell.Style(Color.WHITE)

/**
 * What one [composeFrame] call settled on. [board] and each panel's [ScrollState.focus] are fed
 * back into the next [composeFrame] call as each scrollable's `previousFocus`: [ScrollableView]
 * auto-follows only when the focus has actually moved, which is what lets a manual pan or
 * wheel-scroll survive subsequent renders.
 */
internal data class Frame(
    val layout: FrameLayout,
    val board: ScrollState,
    val panels: Map<PanelId, ScrollState>,
)

/**
 * Composes and draws one frame into a fresh [width]x[height] buffer: the board, every visible
 * side panel, the status bar, and — once the match has ended — a game-over banner over the board.
 * Pure with respect to the terminal: callers still own sending the returned [ScreenBuffer] to a
 * [battletech.tui.screen.ScreenRenderer] and folding the returned [Frame]'s settled scroll
 * positions back into [AppState].
 *
 * [previous] carries the prior frame's focus rects forward, which is what makes auto-follow fire
 * on focus movement only rather than on every render. Pass `null` to make everything re-follow —
 * used for the very first frame, and on resize, where the viewport changes but content-space
 * focus does not, and a shrink could otherwise strand the cursor off-screen.
 */
internal fun composeFrame(
    appState: AppState,
    width: Int,
    height: Int,
    flash: FlashMessage?,
    previous: Frame?,
    recenterBoard: Boolean = false,
): Pair<ScreenBuffer, Frame> {
    val visible = PanelVisibility.visiblePanels(appState)
    val layout = FrameLayout.compute(
        termWidth = width,
        termHeight = height,
        visiblePanels = visible,
        collapsedPanels = appState.collapsedPanels,
        panels = Panels.ordered,
    )

    val buffer = ScreenBuffer(width, height)
    val screen = Canvas.of(buffer)
    val inputs = PanelInputs(appState)

    val renderData = appState.phase.render(appState)
    val boardContent = BoardView(
        appState.visibleState,
        cursorPosition = appState.cursor,
        hexHighlights = renderData.hexHighlights,
        reachableFacings = renderData.reachableFacings,
        facingSelectionFacings = renderData.facingSelection?.facings,
        pathDestination = appState.phase.pathDestination(),
        movementMode = appState.phase.movementMode(),
        torsoFacings = renderData.torsoFacings,
        validTargetPositions = renderData.validTargetPositions,
        selectedTargetPosition = renderData.selectedTargetPosition,
    )
    val (mapWidth, mapHeight) = BoardView.contentSize(appState.visibleState.map)
    val boardScrollable = ScrollableView(
        title = "TACTICAL MAP",
        badge = null,
        content = boardContent,
        extent = ContentExtent.Fixed(mapWidth, mapHeight),
        offset = appState.boardScroll,
        previousFocus = previous?.board?.focus,
        recenter = recenterBoard,
    )
    val board = screen.region(0, layout.boardY, layout.boardWidth, layout.boardHeight)
    boardScrollable.render(board)

    val panels = mutableMapOf<PanelId, ScrollState>()
    for (slot in layout.slots) {
        val view = slot.pane(
            inputs,
            scrollOffset = appState.panelScrollOffsets[slot.id],
            previousFocus = previous?.panels?.get(slot.id)?.focus,
        )
        view?.render(screen.region(slot.x, layout.boardY, slot.width, layout.boardHeight))
        if (view is ScrollableView) {
            panels[slot.id] = view.scroll
        }
    }

    val matchEnded = appState.matchEnded
    val prompt = when {
        matchEnded != null -> {
            val outcomeText = when (val outcome = matchEnded.outcome) {
                is MatchOutcome.Draw -> "Draw"
                is MatchOutcome.Victory -> "${playerName(outcome.winner)} wins!"
            }
            "Match over — $outcomeText  |  ${KeyGlyph.CTRL}c: quit"
        }
        flash != null -> flash.text
        else -> appState.phase.prompt(appState)
    }
    val activePlayerInfo = if (matchEnded != null) null else appState.phase.activePlayerLabel(appState)
    val statusBarView = StatusBarView(appState.currentPhase, prompt, activePlayerInfo)
    statusBarView.render(screen.region(0, 0, width, FrameLayout.STATUS_BAR_HEIGHT))

    if (matchEnded != null) {
        renderGameOverBanner(board, matchEnded.outcome)
    }

    return buffer to Frame(layout, boardScrollable.scroll, panels)
}

/**
 * Renders a centered overlay box in the board area declaring the match result.
 * Overlays board content — called after the board and panels are drawn so
 * it appears on top.
 */
private fun renderGameOverBanner(board: Canvas, outcome: MatchOutcome) {
    val winnerLine = when (outcome) {
        is MatchOutcome.Draw -> "Draw"
        is MatchOutcome.Victory -> "${playerName(outcome.winner)} wins!"
    }
    val bannerWidth = maxOf(winnerLine.length + 8, 24)
    val bannerHeight = 7
    if (bannerWidth > board.width || bannerHeight > board.height) return
    val banner = board.region(
        (board.width - bannerWidth) / 2, (board.height - bannerHeight) / 2,
        bannerWidth, bannerHeight,
    )
    banner.drawBox(
        title = "MATCH OVER",
        borderColor = Color.BRIGHT_YELLOW,
        titleColor = Color.BRIGHT_YELLOW,
    )
    val mx = (bannerWidth - winnerLine.length) / 2
    banner.writeString(mx, 3, winnerLine, WHITE_STYLE)
}

private fun playerName(player: PlayerId): String = when (player) {
    PlayerId.PLAYER_1 -> "P1"
    PlayerId.PLAYER_2 -> "P2"
}
