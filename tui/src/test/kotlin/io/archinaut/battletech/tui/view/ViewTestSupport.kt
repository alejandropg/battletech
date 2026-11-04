package io.archinaut.battletech.tui.view

import io.archinaut.tenter.widget.Title
import io.archinaut.battletech.tui.animation.PanelPlacement
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.tenter.animation.AnimationPlayback
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Padding
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.terminal.CompletedFrame
import io.archinaut.tenter.terminal.Frame
import io.archinaut.tenter.terminal.Terminal
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.FlashMessage
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.Scrollbar
import io.archinaut.tenter.widget.StatefulWidget
import io.archinaut.tenter.widget.Viewport
import io.archinaut.tenter.widget.ViewportState
import io.archinaut.tenter.widget.Widget
import io.archinaut.tenter.widget.block
import io.archinaut.tenter.widget.panel.Panel
import io.archinaut.tenter.widget.panel.PanelId
import io.archinaut.tenter.widget.panel.PanelSet
import io.archinaut.tenter.widget.panel.Panels

/** Draws [widget] in a fresh [width]x[height] frame and returns it for assertions. */
public fun render(widget: Widget, width: Int, height: Int): CompletedFrame =
    drawFrame(width, height) { it.renderWidget(widget, it.area) }

private object TestPanel : PanelId

/**
 * [content] in a [Viewport] with the chrome a panel gives it — a titled border with horizontal
 * padding, a top spacer row that scrolls away with the content, and a vertical scrollbar on the
 * right border — for tests that drive the [ViewportState] directly.
 */
public fun panelViewport(content: MeasurableWidget, title: String, badge: String?): StatefulWidget<ViewportState> =
    object : StatefulWidget<ViewportState> {
        override fun render(canvas: Canvas, state: ViewportState) {
            val frame = Block.bordered(title = Title(title, badge = badge), padding = Padding.horizontal(1))
            frame.render(canvas)
            val inner = frame.inner(canvas.area)
            Viewport(content.block(Block(padding = Padding(top = 1)))).render(canvas.region(inner), state)
            state.settled?.let { Scrollbar.vertical().render(canvas.region(Rect(canvas.width - 1, inner.y, 1, inner.height)), it) }
        }
    }

/** Draws [viewport] with [state] in a fresh [width]x[height] frame and returns it for assertions. */
public fun render(viewport: StatefulWidget<ViewportState>, state: ViewportState, width: Int, height: Int): CompletedFrame =
    drawFrame(width, height) { it.renderStatefulWidget(viewport, it.area, state) }

/** Renders [content] inside the real panel chrome — the pixel-parity regression guard. */
public fun renderInPanel(
    content: MeasurableWidget,
    badge: Char = '0',
    title: String = "T",
    width: Int = 28,
    height: Int = 30,
    scrollOffset: Int = 0,
): CompletedFrame {
    val panels = PanelSet<TestPanel, Unit>(
        listOf(Panel(TestPanel, Title(title, badge = badge.toString()), normal = { content })),
    )
    panels.scroll(TestPanel, 0, scrollOffset)
    return drawFrame(width, height) { it.renderStatefulWidget(Panels(Unit, mapOf(TestPanel to it.area)), it.area, panels) }
}

/**
 * Draws one [width]x[height] frame with [draw] through a headless [Terminal], and returns what it
 * drew. Each call is a fresh terminal, so frames never diff against each other.
 */
public fun drawFrame(width: Int, height: Int, draw: (Frame) -> Unit): CompletedFrame =
    Terminal.headless(width, height).withScreen { it.draw(draw) }

/** Draws one [width]x[height] frame of [appState] through this workspace and returns what it drew. */
internal fun Workspace.draw(
    appState: AppState,
    width: Int,
    height: Int,
    flash: FlashMessage?,
    animations: List<AnimationPlayback.VisibleFrame<PanelPlacement>> = emptyList(),
): CompletedFrame = drawFrame(width, height) { render(it, appState, flash, animations) }
