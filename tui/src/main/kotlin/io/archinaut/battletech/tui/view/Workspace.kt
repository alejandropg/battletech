package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.animation.PanelPlacement
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.battletech.tui.game.GamePanelId
import io.archinaut.battletech.tui.game.PanelVisibility
import io.archinaut.battletech.tui.input.Keybindings
import io.archinaut.tenter.animation.AnimationPlayback
import io.archinaut.tenter.terminal.Frame
import io.archinaut.tenter.widget.FlashMessage
import io.archinaut.tenter.widget.panel.PanelHit
import io.archinaut.tenter.widget.panel.PanelSet

/**
 * Owns the game screen's [PanelSet] — the board plus every side panel — for one
 * [io.archinaut.battletech.tui.TuiApp] run: the controller side (focus, scroll, hit-testing) of the
 * screen whose drawing is [GameScreen]. `runLoop` constructs one [Workspace] and calls [render]
 * every frame.
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
     * Draws one frame of [GameScreen] over the whole [frame], with this workspace's panels as its
     * state — see [GameScreen] for what goes where.
     */
    fun render(
        frame: Frame,
        appState: AppState,
        flash: FlashMessage?,
        animations: List<AnimationPlayback.VisibleFrame<PanelPlacement>> = emptyList(),
    ) {
        frame.renderStatefulWidget(GameScreen(appState, keys, flash, animations), frame.area, panels)
    }
}
