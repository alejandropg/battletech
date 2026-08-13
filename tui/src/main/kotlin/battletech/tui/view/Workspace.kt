package battletech.tui.view

import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.PlayerId
import battletech.tui.game.AppState
import battletech.tui.game.GamePanelId
import battletech.tui.game.PanelVisibility
import tenter.input.KeyGlyph
import tenter.view.FlashMessage
import tenter.panel.PanelLayout
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.RevealRect
import tenter.screen.ScreenBuffer
import tenter.screen.UiRole
import tenter.view.Bordered
import tenter.view.ContentExtent
import tenter.view.ScrollOffset
import tenter.view.View
import tenter.view.scrollingPanel

private val TEXT_PRIMARY_STYLE = Cell.Style(UiRole.TEXT_PRIMARY)

/**
 * Owns every side panel and the tactical board's scroll bookkeeping for one
 * [battletech.tui.TuiApp] run: `runLoop` constructs one [Workspace] and calls [render] every
 * frame.
 *
 * Panel VISIBILITY (does it exist this frame) is never stored here — see [PanelVisibility] — only
 * what the user chose to remember about a panel that DOES exist (collapsed, scroll) lives on that
 * panel itself, see [tenter.panel.Panel]'s KDoc. [panels] is built fresh per [Workspace] (never a
 * global singleton), so one test's collapsed/scrolled panel can never leak into another's.
 */
internal class Workspace {
    private val panels: List<GamePanel> = Panels.build()
    private val byId: Map<GamePanelId, GamePanel> = panels.associateBy { it.id }
    private var boardReveal: RevealRect? = null
    private var layout: GamePanelLayout = PanelLayout.compute(width = 0, height = 0, reservedTop = STATUS_BAR_HEIGHT, visible = emptyList())

    /**
     * The board's own settled scroll offset. The one piece of [Workspace] state a non-rendering
     * reader also needs — [battletech.tui.game.phase.mapIdleInput]'s click-to-hex mapping — so,
     * unlike a side panel's scroll, callers fold it back into [AppState.boardScroll] after every
     * [render]; see that field's KDoc for why it alone round-trips through [AppState].
     */
    var boardOffset: ScrollOffset = ScrollOffset.ZERO
        private set

    /** Toggles [id]'s collapsed-vs-expanded display preference. HELP has no collapsed state of its own — see [AppState.helpOpen] instead. */
    fun toggleCollapsed(id: GamePanelId) {
        byId[id]?.toggleCollapsed()
    }

    /** Scrolls panel [id] by [delta], if it exists. */
    fun scrollPanel(id: GamePanelId, delta: Int) {
        byId[id]?.scrollBy(delta)
    }

    /** The [GamePanelId] of the expanded panel at screen ([x], [y]), or `null` — board, status bar, or a collapsed stub. */
    fun panelAt(x: Int, y: Int): GamePanelId? = layout.slotAt(x, y)?.panel?.id

    /**
     * Composes and draws one frame into a fresh [width]x[height] buffer: the board, every visible
     * side panel, the status bar, and — once the match has ended — a game-over banner over the
     * board. Every panel (and the board) absorbs its own settled scroll and reveal for the next
     * call — see [tenter.panel.Panel.render] — so nothing round-trips back through [AppState]
     * except [boardOffset].
     *
     * [forgetReveal] is a one-shot override for the resize case: the viewport just changed size, so
     * this render should treat every content reveal as freshly arrived (auto-follow into view)
     * rather than compare it against what was last settled — see [tenter.panel.Panel.render]'s
     * KDoc. The settled reveal this render still becomes the baseline for the next call.
     */
    fun render(
        appState: AppState,
        width: Int,
        height: Int,
        flash: FlashMessage?,
        recenterBoard: Boolean = false,
        forgetReveal: Boolean = false,
    ): ScreenBuffer {
        val visible = PanelVisibility.visiblePanels(appState)
        layout = PanelLayout.compute(width, height, reservedTop = STATUS_BAR_HEIGHT, visible = panels.filter { it.id in visible })

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
        val boardBordered = scrollingPanel(
            title = "TACTICAL MAP",
            badge = null,
            content = boardContent,
            extent = ContentExtent.Fixed(mapWidth, mapHeight),
            offset = appState.boardScroll,
            previousReveal = if (forgetReveal) null else boardReveal,
            recenter = recenterBoard,
        )
        val board = screen.region(layout.contentX, layout.contentY, layout.contentWidth, layout.contentHeight)
        boardBordered.render(board)
        boardOffset = boardBordered.scroll.offset
        boardReveal = boardBordered.scroll.revealed

        for (slot in layout.slots) {
            slot.panel.render(
                screen.region(slot.x, layout.contentY, slot.panel.width, layout.contentHeight),
                inputs,
                forgetReveal = forgetReveal,
            )
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
        statusBarView.render(screen.region(0, 0, width, STATUS_BAR_HEIGHT))

        if (matchEnded != null) {
            renderGameOverBanner(board, matchEnded.outcome)
        }

        return buffer
    }

    internal companion object {
        /** Rows consumed by the status bar above the board and panels. */
        const val STATUS_BAR_HEIGHT: Int = 4
    }
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
    val mx = (bannerWidth - winnerLine.length) / 2
    Bordered(
        title = "MATCH OVER",
        borderColor = UiRole.ACCENT,
        titleColor = UiRole.ACCENT,
        content = BannerLine(winnerLine, column = mx - 1, row = 2),
    ).render(banner)
}

/** [text] at a fixed local ([column], [row]) — the banner's win/draw line, inside [Bordered]'s border inset. */
private class BannerLine(private val text: String, private val column: Int, private val row: Int) : View {
    override fun render(canvas: Canvas) {
        canvas.writeString(column, row, text, TEXT_PRIMARY_STYLE)
    }
}

private fun playerName(player: PlayerId): String = when (player) {
    PlayerId.PLAYER_1 -> "P1"
    PlayerId.PLAYER_2 -> "P2"
}
