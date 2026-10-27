package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tactical.model.MatchOutcome
import io.archinaut.battletech.tui.animation.ANIMATION_BORDER
import io.archinaut.battletech.tui.animation.PanelPlacement
import io.archinaut.battletech.tui.animation.panelSize
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.game.PanelVisibility
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.battletech.tui.input.KeyGlyph
import io.archinaut.tenter.animation.AnimationPlayback
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.screen.Layout
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.terminal.Frame
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.Clear
import io.archinaut.tenter.widget.FlashMessage
import io.archinaut.tenter.widget.Widget
import io.archinaut.tenter.widget.panel.PanelHit
import io.archinaut.tenter.widget.panel.PanelSet
import io.archinaut.tenter.widget.panel.Panels

private val TEXT_PRIMARY_STYLE = Style(StandardRole.TEXT_PRIMARY)

/**
 * Owns the game screen's [PanelSet] — the board plus every side panel — and the status bar/game-over
 * overlay for one [io.archinaut.battletech.tui.TuiApp] run: `runLoop` constructs one [Workspace] and calls
 * [render] every frame.
 *
 * Panel VISIBILITY (does a side panel exist this frame) is never stored here — see
 * [PanelVisibility] — only what the user chose to remember about a panel that DOES exist (state,
 * scroll, focus) lives on [panels] itself, see [io.archinaut.tenter.widget.panel.Panel]'s and [io.archinaut.tenter.widget.panel.PanelSet]'s
 * KDoc. [panels] is built fresh per [Workspace] (never a global singleton), so one test's panel
 * state can never leak into another's.
 */
internal class Workspace(private val keys: Keybindings) {
    private val panels: PanelSet<GamePanelId, PanelInputs> = GamePanels.build(keys)

    /** The panel currently receiving keyboard focus — border/title/thumb render green for it. */
    val focused: GamePanelId get() = panels.focused

    /** Focuses [id], demoting whatever side panel was maximized — see [io.archinaut.tenter.widget.panel.PanelSet.focus]. */
    fun focus(id: GamePanelId) = panels.focus(id)

    /** Focuses [id], or cycles it forward when it is already focused — a panel's number key. */
    internal fun focusOrCycle(id: GamePanelId) {
        if (id == panels.focused) panels.cycleFocusedMode(1) else panels.focus(id)
    }

    /** Cycles the focused panel's state (`+`/`-`) — see [io.archinaut.tenter.widget.panel.PanelSet.cycleFocusedMode]. */
    fun cycleFocusedMode(delta: Int) = panels.cycleFocusedMode(delta)

    /** Scrolls the focused panel by one content row — keyboard `↑`/`↓`. */
    fun scrollFocused(dx: Int, dy: Int) = panels.scroll(panels.focused, dx, dy)

    /** Scrolls the focused panel by one viewport height — keyboard `PageUp`/`PageDown`. */
    fun pageFocused(direction: Int) = panels.page(panels.focused, direction)

    /** Mouse path: scrolls panel [id] by [delta] vertical rows, regardless of focus. */
    fun scrollPanel(id: GamePanelId, delta: Int) = panels.scroll(id, 0, delta)

    /** Resolves a completed-frame hit without reproducing border, padding, or scroll arithmetic. */
    fun hitTest(x: Int, y: Int): PanelHit<GamePanelId>? = panels.hitTest(x, y)

    /** Manual board pan — `hjkl`/ctrl+arrows, bound globally regardless of focus. */
    fun panBoard(dx: Int, dy: Int) = panels.scroll(GamePanelId.BOARD, dx, dy)

    /** One-shot: the board recenters on its reveal target (the cursor) at the next render. */
    fun recenterBoard() = panels.requestRecenter(GamePanelId.BOARD)

    /**
     * Composes and draws one frame: the status bar on top, the board and every visible side panel
     * below it (placed by [GamePanels.areas]), and — once the match has ended — a game-over banner
     * over whichever panel occupies the board's place. Every panel absorbs its own settled scroll
     * and reveal for the next call — see [io.archinaut.tenter.widget.panel.Panels] — so nothing
     * round-trips back through [AppState].
     */
    fun render(
        frame: Frame,
        appState: AppState,
        flash: FlashMessage?,
        animations: List<AnimationPlayback.VisibleFrame<PanelPlacement>> = emptyList(),
    ) {
        val (statusArea, body) = Layout.vertical(
            Constraint.Length(STATUS_BAR_HEIGHT),
            Constraint.Fill(1),
        ).split(frame.area)

        val areas = GamePanels.areas(panels, PanelVisibility.visiblePanels(appState), body)
        frame.renderStatefulWidget(Panels(PanelInputs(appState, keys), areas), body, panels)
        frame.renderWidget(statusBar(appState, flash), statusArea)

        appState.matchEnded?.let { ended ->
            // A maximized side panel replaces the board, so the banner goes over the whole body.
            frame.renderWidget(GameOverBanner(ended.outcome), areas[GamePanelId.BOARD] ?: body)
        }

        // Drawn last, over everything (including the game-over banner) and against the WHOLE
        // screen rather than the board — unlike the banner, a maximized side panel must not
        // shift or shrink where these sit. The list is bottom-first, so where a small screen forced
        // panels to overlap, a later (more recently appeared) one covers an earlier one.
        animations.forEach { renderAnimationPanel(frame, it) }
    }

    private fun statusBar(appState: AppState, flash: FlashMessage?): StatusBarView {
        val matchEnded = appState.matchEnded
        if (matchEnded != null) {
            val outcomeText = when (val outcome = matchEnded.outcome) {
                is MatchOutcome.Draw -> "Draw"
                is MatchOutcome.Victory -> "${playerLabel(outcome.winner)} wins!"
            }
            return StatusBarView(appState.currentPhase, "Match over — $outcomeText  |  ${KeyGlyph.CTRL}c: quit")
        }
        val status = appState.phase.status(appState)
        val actionUnit = status.actionUnitId
            ?.takeIf { flash == null }
            ?.let { appState.state.units.byId(it) }
        return StatusBarView(
            phase = appState.currentPhase,
            prompt = flash?.text ?: status.prompt,
            activePlayer = status.activePlayer,
            actionUnit = actionUnit,
        )
    }

    internal companion object {
        /** Rows consumed by the status bar above the board and panels. */
        const val STATUS_BAR_HEIGHT: Int = 3
    }
}

/**
 * A centered overlay box declaring the match result, drawn over the board area after the board and
 * panels so it appears on top; it clears its box first, so the board doesn't show through. Draws
 * nothing when the area is too small for it.
 */
private class GameOverBanner(private val outcome: MatchOutcome) : Widget {
    override fun render(canvas: Canvas) {
        val winnerLine = when (outcome) {
            is MatchOutcome.Draw -> "Draw"
            is MatchOutcome.Victory -> "${playerLabel(outcome.winner)} wins!"
        }
        val bannerWidth = maxOf(CellWidth.of(winnerLine) + 8, 24)
        val bannerHeight = 7
        if (bannerWidth > canvas.width || bannerHeight > canvas.height) return
        val banner = canvas.region(
            Rect((canvas.width - bannerWidth) / 2, (canvas.height - bannerHeight) / 2, bannerWidth, bannerHeight),
        )
        val block = Block.bordered(title = "MATCH OVER", borderColor = StandardRole.ACCENT, titleColor = StandardRole.ACCENT)
        Clear.render(banner)
        block.render(banner)
        // Row 2 of the block's inside, centered on the whole banner.
        val column = (bannerWidth - CellWidth.of(winnerLine)) / 2 - 1
        block.inner(banner).setString(column, 2, winnerLine, TEXT_PRIMARY_STYLE)
    }
}

/**
 * Renders one bordered overlay box at [animation]'s own placement, playing its current frame — a
 * plain border ([ANIMATION_BORDER], not the themed [StandardRole.PANEL_BORDER] a [Block] would
 * otherwise default to) with no title, badge, or hint text anywhere in it (unlike
 * [GameOverBanner]), so the animation itself is the only thing drawn inside. The border's
 * BACKGROUND is the one cell in this whole panel still themed: [Clear] resets the frame's cells
 * to the default background, so the board doesn't show through, and [Block] paints only the
 * border's foreground over it.
 *
 * No-ops if this animation's bordered size doesn't fit [frame] —
 * the caller (`io.archinaut.battletech.tui.loop.runLoop`) is expected to have already refused to start a volley
 * that wouldn't fit, so this is a second, independent guard against a resize shrinking the screen
 * out from under a volley already playing. Volley construction is all-or-nothing; this guard
 * protects an individual panel if a resize races a render.
 */
private fun renderAnimationPanel(
    frame: Frame,
    animation: AnimationPlayback.VisibleFrame<PanelPlacement>,
) {
    val screen = frame.area
    val panelSize = animation.size.panelSize
    if (panelSize.width > screen.width || panelSize.height > screen.height) return
    // AnimationLayout already places every panel on-screen, so this clamp is a second,
    // independent guard against a resize racing a render — exactly the role the fit check above
    // plays. The frame clips the rectangle if a resize still races this path.
    val area = Rect(
        animation.value.x.coerceIn(0, screen.width - panelSize.width),
        animation.value.y.coerceIn(0, screen.height - panelSize.height),
        panelSize.width,
        panelSize.height,
    )
    val block = Block.bordered(borderColor = ANIMATION_BORDER)
    frame.renderWidget(Clear, area)
    frame.renderWidget(block, area)
    frame.renderWidget(animation.content, block.inner(area))
}
