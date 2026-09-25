package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.model.MatchOutcome
import io.archinaut.battletech.tui.animation.ANIMATION_BORDER
import io.archinaut.battletech.tui.animation.PanelPlacement
import io.archinaut.battletech.tui.animation.panelSize
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.game.PanelVisibility
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.animation.AnimationPlayback
import io.archinaut.tenter.input.KeyGlyph
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.panel.PanelHit
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.screen.ScreenBuffer
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.view.Bordered
import io.archinaut.tenter.view.FlashMessage
import io.archinaut.tenter.view.View

private val TEXT_PRIMARY_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)

/**
 * Owns the [GamePanelSet] — the board plus every side panel — and the status bar/game-over
 * overlay for one [io.archinaut.battletech.tui.TuiApp] run: `runLoop` constructs one [Workspace] and calls
 * [render] every frame.
 *
 * Panel VISIBILITY (does a side panel exist this frame) is never stored here — see
 * [PanelVisibility] — only what the user chose to remember about a panel that DOES exist (state,
 * scroll, focus) lives on [panels] itself, see [io.archinaut.tenter.panel.Panel]'s and [io.archinaut.tenter.panel.PanelSet]'s
 * KDoc. [panels] is built fresh per [Workspace] (never a global singleton), so one test's panel
 * state can never leak into another's.
 */
internal class Workspace(private val keys: Keybindings) {
    private val panels: GamePanelSet = Panels.build(keys)

    /** The panel currently receiving keyboard focus — border/title/thumb render green for it. */
    val focused: GamePanelId get() = panels.focused

    /** Focuses [id], demoting whatever side panel was maximized — see [io.archinaut.tenter.panel.PanelSet.focus]. */
    fun focus(id: GamePanelId) = panels.focus(id)

    /** Focuses [id], or cycles it forward when it is already focused. */
    internal fun focusOrCycle(id: GamePanelId) = panels.focusOrCycle(id)

    /** Cycles the focused panel's state (`+`/`-`) — see [io.archinaut.tenter.panel.PanelSet.cycleFocusedState]. */
    fun cycleFocusedState(delta: Int) = panels.cycleFocusedState(delta)

    /** Scrolls the focused panel by one content row — keyboard `↑`/`↓`. */
    fun scrollFocused(dx: Int, dy: Int) = panels.scrollFocused(dx, dy)

    /** Scrolls the focused panel by one viewport height — keyboard `PageUp`/`PageDown`. */
    fun pageFocused(direction: Int) = panels.pageFocused(direction)

    /** Mouse path: scrolls panel [id] by [delta] vertical rows, regardless of focus. */
    fun scrollPanel(id: GamePanelId, delta: Int) = panels.scroll(id, 0, delta)

    /** The [GamePanelId] of the panel at ([x], [y]), including the board, or null. */
    fun panelAt(x: Int, y: Int): GamePanelId? = panels.panelAt(x, y)

    /** Resolves a completed-frame hit without reproducing border, padding, or scroll arithmetic. */
    fun hitTest(x: Int, y: Int): PanelHit<GamePanelId>? = panels.hitTest(x, y)

    /** Manual board pan — `hjkl`/ctrl+arrows, bound globally regardless of focus. */
    fun panBoard(dx: Int, dy: Int) = panels.scroll(GamePanelId.BOARD, dx, dy)

    /** One-shot: the board recenters on its reveal target (the cursor) at the next render. */
    fun recenterBoard() = panels.requestRecenter(GamePanelId.BOARD)

    /**
     * Composes and draws one frame into a fresh [width]x[height] buffer: the board, every visible
     * side panel, the status bar, and — once the match has ended — a game-over banner over
     * whichever panel currently occupies the content region. Every panel absorbs its own settled
     * scroll and reveal for the next call — see [io.archinaut.tenter.panel.Panel.render] — so nothing
     * round-trips back through [AppState].
     *
     */
    fun render(
        appState: AppState,
        width: Int,
        height: Int,
        flash: FlashMessage?,
        animations: List<AnimationPlayback.Frame<PanelPlacement>> = emptyList(),
    ): ScreenBuffer {
        val visible = PanelVisibility.visiblePanels(appState)

        val buffer = ScreenBuffer(width, height)
        val screen = Canvas.of(buffer)
        val inputs = PanelInputs(appState, keys)

        val layout = panels.render(screen, inputs, visible, reservedTop = STATUS_BAR_HEIGHT)

        val matchEnded = appState.matchEnded
        val statusBarView = if (matchEnded != null) {
            val outcomeText = when (val outcome = matchEnded.outcome) {
                is MatchOutcome.Draw -> "Draw"
                is MatchOutcome.Victory -> "${playerLabel(outcome.winner)} wins!"
            }
            StatusBarView(appState.currentPhase, "Match over — $outcomeText  |  ${KeyGlyph.CTRL}c: quit")
        } else {
            val status = appState.phase.status(appState)
            val actionUnit = status.actionUnitId
                ?.takeIf { flash == null }
                ?.let { appState.state.units.byId(it) }
            StatusBarView(
                phase = appState.currentPhase,
                prompt = flash?.text ?: status.prompt,
                activePlayer = status.activePlayer,
                actionUnit = actionUnit,
            )
        }
        statusBarView.draw(screen.region(0, 0, width, STATUS_BAR_HEIGHT))

        if (matchEnded != null) {
            renderGameOverBanner(screen.region(layout.main?.outer ?: layout.content), matchEnded.outcome)
        }

        // Drawn last, over everything (including the game-over banner) and against the WHOLE
        // screen rather than layout.main — unlike the banner, a maximized side panel must not
        // shift or shrink where these sit. The list is bottom-first, so where a small screen forced
        // panels to overlap, a later (more recently appeared) one covers an earlier one.
        animations.forEach { renderAnimationPanel(screen, it) }

        return buffer
    }

    internal companion object {
        /** Rows consumed by the status bar above the board and panels. */
        const val STATUS_BAR_HEIGHT: Int = 3
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
        is MatchOutcome.Victory -> "${playerLabel(outcome.winner)} wins!"
    }
    val bannerWidth = maxOf(CellWidth.of(winnerLine) + 8, 24)
    val bannerHeight = 7
    if (bannerWidth > board.width || bannerHeight > board.height) return
    val banner = board.region(
        (board.width - bannerWidth) / 2, (board.height - bannerHeight) / 2,
        bannerWidth, bannerHeight,
    )
    val mx = (bannerWidth - CellWidth.of(winnerLine)) / 2
    Bordered(
        title = "MATCH OVER",
        borderColor = ChromeRole.ACCENT,
        titleColor = ChromeRole.ACCENT,
        content = BannerLine(winnerLine, column = mx - 1, row = 2),
    ).draw(banner)
}

/**
 * Renders one bordered overlay box at [animation]'s own placement, playing its current frame — a
 * plain border ([ANIMATION_BORDER], not the themed [ChromeRole.PANEL_BORDER]
 * [Bordered] would otherwise default to) with no title, badge, or hint text anywhere in it (unlike
 * [renderGameOverBanner]), so the animation itself is the only thing drawn inside. The border's
 * BACKGROUND is the one cell in this whole panel still themed — [Bordered] always paints its frame
 * with [ChromeRole.DEFAULT] and takes no background override; making even that
 * hardcoded would mean not using [Bordered] at all, which isn't worth it for one ring of cells.
 *
 * No-ops if this animation's bordered size doesn't fit [screen] —
 * the caller (`io.archinaut.battletech.tui.loop.runLoop`) is expected to have already refused to start a volley
 * that wouldn't fit, so this is a second, independent guard against a resize shrinking the screen
 * out from under a volley already playing. Volley construction is all-or-nothing; this guard
 * protects an individual panel if a resize races a render.
 */
private fun renderAnimationPanel(
    screen: Canvas,
    animation: AnimationPlayback.Frame<PanelPlacement>,
) {
    val panelSize = animation.size.panelSize
    val panelWidth = panelSize.width
    val panelHeight = panelSize.height
    if (panelWidth > screen.width || panelHeight > screen.height) return
    // AnimationLayout already places every panel on-screen, so this clamp is a second,
    // independent guard against a resize racing a render — exactly the role the fit check above
    // plays. Canvas.region intersects the requested rectangle if a resize still races this path.
    val region = screen.region(
        animation.value.x.coerceIn(0, screen.width - panelWidth),
        animation.value.y.coerceIn(0, screen.height - panelHeight),
        panelWidth, panelHeight,
    )
    Bordered(content = animation.content, borderColor = ANIMATION_BORDER).draw(region)
}

/** [text] at a fixed local ([column], [row]) — the banner's win/draw line, inside [Bordered]'s border inset. */
private class BannerLine(private val text: String, private val column: Int, private val row: Int) : View {
    override fun draw(canvas: Canvas) {
        canvas.writeString(column, row, text, TEXT_PRIMARY_STYLE)
    }
}
