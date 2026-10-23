package io.archinaut.battletech.tui.view

import io.archinaut.battletech.tui.animation.PanelPlacement
import io.archinaut.battletech.tui.game.AppState
import io.archinaut.tenter.animation.AnimationPlayback
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.screen.Padding
import io.archinaut.tenter.screen.Rect
import io.archinaut.tenter.terminal.CompletedFrame
import io.archinaut.tenter.terminal.Frame
import io.archinaut.tenter.terminal.Terminal
import io.archinaut.tenter.widget.Block
import io.archinaut.tenter.widget.FlashMessage
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.Viewport
import io.archinaut.tenter.widget.ViewportState
import io.archinaut.tenter.widget.Widget
import io.archinaut.tenter.widget.block
import io.archinaut.tenter.widget.panel.Panel
import io.archinaut.tenter.widget.panel.PanelId
import io.archinaut.tenter.widget.panel.PanelSet
import io.archinaut.tenter.widget.panel.Panels

/** Renders [widget] into a fresh [width]x[height] canvas and returns it for assertions. */
public fun render(widget: Widget, width: Int, height: Int): Canvas =
    Canvas.offscreen(width, height).also(widget::render)

private object TestPanel : PanelId

/**
 * [content] in a [Viewport] with the chrome a panel gives it — a titled border with horizontal
 * padding, and a top spacer row that scrolls away with the content — for tests that drive the
 * [ViewportState] directly.
 */
public fun panelViewport(content: MeasurableWidget, title: String, badge: String?): Viewport = Viewport(
    content.block(Block(padding = Padding(top = 1))),
    block = Block.bordered(title = title, badge = badge, padding = Padding.horizontal(1)),
)

/** Renders [viewport] with [state] into a fresh [width]x[height] canvas and returns it for assertions. */
public fun render(viewport: Viewport, state: ViewportState, width: Int, height: Int): Canvas =
    Canvas.offscreen(width, height).also { viewport.render(it, state) }

/** Renders [content] inside the real panel chrome — the pixel-parity regression guard. */
public fun renderInPanel(
    content: MeasurableWidget,
    badge: Char = '0',
    title: String = "T",
    width: Int = 28,
    height: Int = 30,
    scrollOffset: Int = 0,
): Canvas {
    val panels = PanelSet<TestPanel, Unit>(listOf(Panel(TestPanel, title, badge.toString(), normal = { content })))
    panels.scroll(TestPanel, 0, scrollOffset)
    val canvas = Canvas.offscreen(width, height)
    Panels(Unit, mapOf(TestPanel to Rect(0, 0, width, height))).render(canvas, panels)
    return canvas
}

/**
 * Draws one [width]x[height] frame with [draw] through a headless [Terminal], and returns what it
 * drew. Each call is a fresh terminal, so frames never diff against each other.
 */
public fun drawFrame(width: Int, height: Int, draw: (Frame) -> Unit): CompletedFrame =
    Terminal.headless(width, height).withScreen { it.draw(draw) }

/** Row [y], columns [x] until [x] + [width], right-trimmed. */
public fun Canvas.line(y: Int, x: Int = 0, width: Int = this.width - x): String = line(::get, y, x, width)

/** The whole canvas as newline-separated rows — for `contains` assertions. */
public fun Canvas.text(): String = (0 until height).joinToString("\n") { line(it) }

/** Row [y], columns [x] until [x] + [width], right-trimmed. */
public fun CompletedFrame.line(y: Int, x: Int = 0, width: Int = this.width - x): String = line(::get, y, x, width)

/** The whole frame as newline-separated rows — for `contains` assertions. */
public fun CompletedFrame.text(): String = (0 until height).joinToString("\n") { line(it) }

private fun line(get: (Int, Int) -> Cell, y: Int, x: Int, width: Int): String =
    (x until x + width).joinToString("") { get(it, y).symbol }.trimEnd()

/** Draws one [width]x[height] frame of [appState] through this workspace and returns what it drew. */
internal fun Workspace.draw(
    appState: AppState,
    width: Int,
    height: Int,
    flash: FlashMessage?,
    animations: List<AnimationPlayback.VisibleFrame<PanelPlacement>> = emptyList(),
): CompletedFrame = drawFrame(width, height) { render(it, appState, flash, animations) }
