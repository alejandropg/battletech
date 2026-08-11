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
import battletech.tui.screen.FocusRect
import battletech.tui.screen.ScreenBuffer

private val TEXT_PRIMARY_STYLE = Cell.Style(Color.TEXT_PRIMARY)

/**
 * Owns every side panel and the tactical board's scroll bookkeeping for one
 * [battletech.tui.TuiApp] run: `runLoop` constructs one [Workspace] and calls [render] every
 * frame.
 *
 * Panel VISIBILITY (does it exist this frame) is never stored here — see [PanelVisibility] — only
 * what the user chose to remember about a panel that DOES exist (collapsed, scroll) lives on that
 * panel itself, see [Panel]'s KDoc. [panels] is built fresh per [Workspace] (never a global
 * singleton), so one test's collapsed/scrolled panel can never leak into another's.
 */
internal class Workspace {
    private val panels: List<Panel> = Panels.build()
    private val byId: Map<PanelId, Panel> = panels.associateBy { it.id }
    private var boardFocus: FocusRect? = null
    private var layout: Layout = Layout(boardWidth = 0, boardHeight = 0, boardY = STATUS_BAR_HEIGHT, slots = emptyList())

    /**
     * The board's own settled scroll offset. The one piece of [Workspace] state a non-rendering
     * reader also needs — [battletech.tui.game.phase.mapIdleInput]'s click-to-hex mapping — so,
     * unlike a side panel's scroll, callers fold it back into [AppState.boardScroll] after every
     * [render]; see that field's KDoc for why it alone round-trips through [AppState].
     */
    var boardOffset: ScrollOffset = ScrollOffset.ZERO
        private set

    /** Toggles [id]'s collapsed-vs-expanded display preference. HELP has no collapsed state of its own — see [AppState.helpOpen] instead. */
    fun toggleCollapsed(id: PanelId) {
        byId[id]?.toggleCollapsed()
    }

    /** Scrolls panel [id] by [delta], if it exists. */
    fun scrollPanel(id: PanelId, delta: Int) {
        byId[id]?.scrollBy(delta)
    }

    /** The [PanelId] of the expanded panel at screen ([x], [y]), or `null` — board, status bar, or a collapsed stub. */
    fun panelAt(x: Int, y: Int): PanelId? = layout.slotAt(x, y)?.panel?.id

    /**
     * Composes and draws one frame into a fresh [width]x[height] buffer: the board, every visible
     * side panel, the status bar, and — once the match has ended — a game-over banner over the
     * board. Every panel (and the board) absorbs its own settled scroll and focus for the next
     * call — see [Panel.render] — so nothing round-trips back through [AppState] except
     * [boardOffset].
     *
     * [forgetFocus] is a one-shot override for the resize case: the viewport just changed size, so
     * this render should treat every content focus as freshly arrived (auto-follow into view)
     * rather than compare it against what was last settled — see [Panel.render]'s KDoc. The
     * settled focus this render still becomes the baseline for the next call.
     */
    fun render(
        appState: AppState,
        width: Int,
        height: Int,
        flash: FlashMessage?,
        recenterBoard: Boolean = false,
        forgetFocus: Boolean = false,
    ): ScreenBuffer {
        val visible = PanelVisibility.visiblePanels(appState)
        layout = Layout.compute(width, height, visible, panels)

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
            previousFocus = if (forgetFocus) null else boardFocus,
            recenter = recenterBoard,
        )
        val board = screen.region(0, layout.boardY, layout.boardWidth, layout.boardHeight)
        boardBordered.render(board)
        boardOffset = boardBordered.scroll.offset
        boardFocus = boardBordered.scroll.focus

        for (slot in layout.slots) {
            slot.panel.render(
                screen.region(slot.x, layout.boardY, slot.panel.width, layout.boardHeight),
                inputs,
                forgetFocus = forgetFocus,
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

    /** One panel's placement this frame: which [panel], and its left column [x] (its width is `panel.width`). */
    private data class Slot(val panel: Panel, val x: Int)

    /** The board rect and every visible panel's placement this frame — recomputed by [render] each call. */
    private data class Layout(val boardWidth: Int, val boardHeight: Int, val boardY: Int, val slots: List<Slot>) {
        /**
         * The expanded (non-collapsed) [Slot] at screen column [x], row [y], or `null` if none
         * matches. Only rows `boardY until boardY + boardHeight` are considered; clicks on the
         * status bar, board area, or a collapsed stub return null.
         */
        fun slotAt(x: Int, y: Int): Slot? {
            if (y < boardY || y >= boardY + boardHeight) return null
            return slots.firstOrNull { slot -> !slot.panel.collapsed && x >= slot.x && x < slot.x + slot.panel.width }
        }

        companion object {
            fun compute(termWidth: Int, termHeight: Int, visible: Set<PanelId>, panels: List<Panel>): Layout {
                val visiblePanels = panels.filter { it.id in visible }
                val totalPanelWidth = visiblePanels.sumOf { it.width }
                val boardWidth = termWidth - totalPanelWidth
                val boardHeight = termHeight - STATUS_BAR_HEIGHT

                val slots = buildList {
                    var nextX = boardWidth
                    for (panel in visiblePanels) {
                        add(Slot(panel, nextX))
                        nextX += panel.width
                    }
                }
                return Layout(boardWidth, boardHeight, STATUS_BAR_HEIGHT, slots)
            }
        }
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
        borderColor = Color.ACCENT,
        titleColor = Color.ACCENT,
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
