package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.Title
import io.archinaut.tenter.widget.Paragraph
import io.archinaut.tenter.screen.Size
import com.github.ajalt.mordant.rendering.TextAlign
import io.archinaut.battletech.tactical.model.MatchOutcome
import io.archinaut.battletech.tui.animation.ANIMATION_BORDER
import io.archinaut.battletech.tui.animation.PanelPlacement
import io.archinaut.battletech.tui.animation.panelSize
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.game.PanelVisibility
import io.archinaut.battletech.tui.input.KeyGlyph
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.animation.AnimationPlayback
import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Constraint
import io.archinaut.tenter.screen.Layout
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.Clear
import io.archinaut.tenter.widget.FlashMessage
import io.archinaut.tenter.widget.StatefulWidget
import io.archinaut.tenter.widget.Widget
import io.archinaut.tenter.widget.panel.PanelSet
import io.archinaut.tenter.widget.panel.Panels

private val TEXT_PRIMARY_STYLE = Style(StandardRole.TEXT_PRIMARY)

/**
 * The whole game screen for one frame: the status bar on top, the board and every visible side
 * panel below it (placed by [GamePanels.areas]), and — once the match has ended — a game-over
 * banner over whichever panel occupies the board's place, with any weapon-fire [animations] over
 * everything. Its state is the [Workspace]'s [PanelSet]; every panel absorbs its own settled
 * scroll and reveal for the next frame, so nothing round-trips back through [AppState].
 *
 * Render it over the whole frame: [Panels] places panels by frame coordinates, and this widget
 * hands it areas split from its own canvas.
 */
internal class GameScreen(
    private val appState: AppState,
    private val keys: Keybindings,
    private val flash: FlashMessage?,
    private val animations: List<AnimationPlayback.VisibleFrame<PanelPlacement>> = emptyList(),
) : StatefulWidget<PanelSet<GamePanelId, PanelInputs>> {

    override fun render(canvas: Canvas, state: PanelSet<GamePanelId, PanelInputs>) {
        val (statusArea, body) = Layout.vertical(
            Constraint.Length(STATUS_BAR_HEIGHT),
            Constraint.Fill(1),
        ).split(canvas.area)

        val areas = GamePanels.areas(state, PanelVisibility.visiblePanels(appState), body)
        Panels(PanelInputs(appState, keys), areas).render(canvas.region(body), state)
        statusBar().render(canvas.region(statusArea))

        appState.matchEnded?.let { ended ->
            // A maximized side panel replaces the board, so the banner goes over the whole body.
            GameOverBanner(ended.outcome).render(canvas.region(areas[GamePanelId.BOARD] ?: body))
        }

        // Drawn last, over everything (including the game-over banner) and against the WHOLE
        // screen rather than the board — unlike the banner, a maximized side panel must not
        // shift or shrink where these sit. The list is bottom-first, so where a small screen forced
        // panels to overlap, a later (more recently appeared) one covers an earlier one.
        animations.forEach { AnimationOverlay(it).render(canvas) }
    }

    private fun statusBar(): StatusBarView {
        val matchEnded = appState.matchEnded
        if (matchEnded != null) {
            return StatusBarView(appState.currentPhase, "Match over — ${outcomeText(matchEnded.outcome)}  |  ${KeyGlyph.CTRL}c: quit")
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

private fun outcomeText(outcome: MatchOutcome): String = when (outcome) {
    is MatchOutcome.Draw -> "Draw"
    is MatchOutcome.Victory -> "${playerLabel(outcome.winner)} wins!"
}

/**
 * A centered overlay box declaring the match result, drawn over the board area after the board and
 * panels so it appears on top; it clears its box first, so the board doesn't show through. Draws
 * nothing when the area is too small for it.
 */
private class GameOverBanner(private val outcome: MatchOutcome) : Widget {
    override fun render(canvas: Canvas) {
        val winnerLine = outcomeText(outcome)
        val bannerWidth = maxOf(CellWidth.of(winnerLine) + 8, 24)
        val bannerHeight = 7
        if (bannerWidth > canvas.width || bannerHeight > canvas.height) return
        val banner = canvas.region(canvas.area.aligned(Size(bannerWidth, bannerHeight)))
        val block = Block.bordered(
            title = Title("MATCH OVER", align = TextAlign.CENTER),
            borderColor = StandardRole.ACCENT,
            titleColor = StandardRole.ACCENT,
        )
        Clear.render(banner)
        block.render(banner)
        val inside = block.inner(banner)
        Paragraph(winnerLine, TEXT_PRIMARY_STYLE, align = TextAlign.CENTER)
            .render(inside.region(Rect(0, WINNER_ROW, inside.width, 1)))
    }

    private companion object {
        /** The winner line's row inside the block, below a blank one. */
        const val WINNER_ROW: Int = 2
    }
}

/**
 * One bordered overlay box at [animation]'s own placement within the screen canvas it renders
 * into, playing its current frame — a plain border ([ANIMATION_BORDER], not the themed
 * [StandardRole.PANEL_BORDER] a [Block] would otherwise default to) with no title, badge, or hint
 * text anywhere in it (unlike [GameOverBanner]), so the animation itself is the only thing drawn
 * inside. The border's BACKGROUND is the one cell in this whole panel still themed: [Clear] resets
 * the box's cells to the default background, so the board doesn't show through, and [Block] paints
 * only the border's foreground over it.
 *
 * Draws nothing if this animation's bordered size doesn't fit the canvas — the caller
 * (`io.archinaut.battletech.tui.loop.runLoop`) is expected to have already refused to start a
 * volley that wouldn't fit, so this is a second, independent guard against a resize shrinking the
 * screen out from under a volley already playing. Volley construction is all-or-nothing; this
 * guard protects an individual panel if a resize races a render.
 */
internal class AnimationOverlay(private val animation: AnimationPlayback.VisibleFrame<PanelPlacement>) : Widget {
    override fun render(canvas: Canvas) {
        val panelSize = animation.size.panelSize
        if (panelSize.width > canvas.width || panelSize.height > canvas.height) return
        // AnimationLayout already places every panel on-screen, so this clamp is a second,
        // independent guard against a resize racing a render — exactly the role the fit check
        // above plays. The canvas clips the rectangle if a resize still races this path.
        val panel = canvas.region(
            Rect(
                animation.value.x.coerceIn(0, canvas.width - panelSize.width),
                animation.value.y.coerceIn(0, canvas.height - panelSize.height),
                panelSize.width,
                panelSize.height,
            ),
        )
        val block = Block.bordered(borderColor = ANIMATION_BORDER)
        Clear.render(panel)
        block.render(panel)
        animation.content.render(block.inner(panel))
    }
}
