package battletech.tui.view

import battletech.tactical.model.MatchOutcome
import battletech.tactical.model.PlayerId
import battletech.tui.game.AppState
import battletech.tui.game.GamePanelId
import battletech.tui.game.PanelVisibility
import tenter.input.KeyGlyph
import tenter.view.FlashMessage
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ScreenBuffer
import tenter.screen.ChromeRole
import tenter.view.Bordered
import tenter.view.ScrollOffset
import tenter.view.View

private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)

/**
 * Owns the [GamePanelSet] — the board plus every side panel — and the status bar/game-over
 * overlay for one [battletech.tui.TuiApp] run: `runLoop` constructs one [Workspace] and calls
 * [render] every frame.
 *
 * Panel VISIBILITY (does a side panel exist this frame) is never stored here — see
 * [PanelVisibility] — only what the user chose to remember about a panel that DOES exist (state,
 * scroll, focus) lives on [panels] itself, see [tenter.panel.Panel]'s and [tenter.panel.PanelSet]'s
 * KDoc. [panels] is built fresh per [Workspace] (never a global singleton), so one test's panel
 * state can never leak into another's.
 */
internal class Workspace {
    private val panels: GamePanelSet = Panels.build()

    /** The panel currently receiving keyboard focus — border/title/thumb render green for it. */
    val focused: GamePanelId get() = panels.focused

    /**
     * The board panel's settled scroll offset. The one piece of panel state a non-rendering reader
     * also needs — [battletech.tui.game.phase.mapIdleInput]'s click-to-hex mapping — so callers
     * mirror it into [AppState.boardScroll] after every [render]. That mirror is one-way: the board
     * [tenter.panel.Panel] owns this offset exactly as a side panel owns its own, and nothing ever
     * writes it back through [AppState] — see [AppState.boardScroll]'s KDoc.
     */
    val boardOffset: ScrollOffset get() = panels.offsetOf(GamePanelId.BOARD)

    /** Focuses [id], demoting whatever side panel was maximized — see [tenter.panel.PanelSet.focus]. */
    fun focus(id: GamePanelId) = panels.focus(id)

    /** Cycles the focused panel's state (`+`/`-`) — see [tenter.panel.PanelSet.cycleFocusedState]. */
    fun cycleFocusedState(delta: Int) = panels.cycleFocusedState(delta)

    /** Scrolls the focused panel by one content row — keyboard `↑`/`↓`. */
    fun scrollFocused(dx: Int, dy: Int) = panels.scrollFocused(dx, dy)

    /** Scrolls the focused panel by one viewport height — keyboard `PageUp`/`PageDown`. */
    fun pageFocused(direction: Int) = panels.pageFocused(direction)

    /** Mouse path: scrolls panel [id] by [delta] vertical rows, regardless of focus. */
    fun scrollPanel(id: GamePanelId, delta: Int) = panels.scroll(id, 0, delta)

    /** The [GamePanelId] of the SIDE panel at screen ([x], [y]), or `null` — board or status bar. */
    fun panelAt(x: Int, y: Int): GamePanelId? = panels.panelIdAt(x, y)

    /** Manual board pan — `hjkl`/ctrl+arrows, bound globally regardless of focus. */
    fun panBoard(dx: Int, dy: Int) = panels.scroll(GamePanelId.BOARD, dx, dy)

    /** One-shot: the board recenters on its reveal target (the cursor) at the next render. */
    fun recenterBoard() = panels.requestRecenter(GamePanelId.BOARD)

    /**
     * Composes and draws one frame into a fresh [width]x[height] buffer: the board, every visible
     * side panel, the status bar, and — once the match has ended — a game-over banner over
     * whichever panel currently occupies the content region. Every panel absorbs its own settled
     * scroll and reveal for the next call — see [tenter.panel.Panel.render] — so nothing
     * round-trips back through [AppState] except [boardOffset].
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
        forgetReveal: Boolean = false,
    ): ScreenBuffer {
        val visible = PanelVisibility.visiblePanels(appState)

        val buffer = ScreenBuffer(width, height)
        val screen = Canvas.of(buffer)
        val inputs = PanelInputs(appState)

        val layout = panels.render(screen, inputs, visible, reservedTop = STATUS_BAR_HEIGHT, forgetReveal = forgetReveal)

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
        statusBarView.draw(screen.region(0, 0, width, STATUS_BAR_HEIGHT))

        if (matchEnded != null) {
            val overlayRegion = layout.main?.let { screen.region(it.x, it.y, it.width, it.height) }
                ?: screen.region(layout.contentX, layout.contentY, layout.contentWidth, layout.contentHeight)
            renderGameOverBanner(overlayRegion, matchEnded.outcome)
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
        borderColor = ChromeRole.ACCENT,
        titleColor = ChromeRole.ACCENT,
        content = BannerLine(winnerLine, column = mx - 1, row = 2),
    ).draw(banner)
}

/** [text] at a fixed local ([column], [row]) — the banner's win/draw line, inside [Bordered]'s border inset. */
private class BannerLine(private val text: String, private val column: Int, private val row: Int) : View {
    override fun draw(canvas: Canvas) {
        canvas.writeString(column, row, text, TEXT_PRIMARY_STYLE)
    }
}

private fun playerName(player: PlayerId): String = when (player) {
    PlayerId.PLAYER_1 -> "P1"
    PlayerId.PLAYER_2 -> "P2"
}
