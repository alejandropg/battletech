package tenterexample

import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.input.MouseEvent
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.takeWhile
import tenter.panel.PanelState
import tenter.screen.Canvas
import tenter.palette.DefaultRolePalette
import tenter.screen.ScreenBuffer
import tenter.screen.ScreenRenderer
import tenter.screen.Insets
import tenter.view.Bordered
import tenter.view.Padded
import tenter.view.contentView
import tenter.terminal.TerminalEvent
import tenter.terminal.inputEvents
import tenter.terminal.resizeEvents
import tenter.terminal.withScreen

/**
 * Runs the separate-consumer smoke scenario without raw input or terminal lifecycle escapes.
 * It exercises long prepared content, composition, settled hit-testing, both layout factories,
 * widget glyphs, keymap dispatch, scrolling, reveal following, panel states, and animation.
 */
public fun runHeadlessSmoke(): SmokeResult {
    val framed = Bordered.prepared(Padded.prepared(Insets.all(1), contentView { cursor ->
        cursor.writeLine("OK")
        cursor.markRevealAt(0)
    })).layout(8)
    val framedCanvas = Canvas.offscreen(framed.width, framed.height)
    framed.draw(framedCanvas)
    check(framed.height == 5 && framedCanvas.get(2, 2).char == "O")
    check(framedCanvas.revealRect()?.y == 2) { "prepared decorators lost the child's reveal" }

    val app = ExampleApp(ExampleLayoutMode.MAIN_AND_SIDES)
    var buffer = app.render(width = 80, height = 12)
    val initialText = bufferText(buffer)
    check("row 0" in initialText) { "the rendered list did not contain its first generated row" }
    check("PgUp/PgDn" in initialText) { "the rendered help did not contain the keymap hints" }

    val initialLayout = checkNotNull(app.lastLayout)
    val wheelTarget = checkNotNull(initialLayout.main).content
    repeat(EXAMPLE_ROW_COUNT) {
        app.handle(MouseEvent(x = wheelTarget.x + 1, y = wheelTarget.y + 1, wheelDown = true))
    }
    buffer = app.render(width = 80, height = 12)
    val scrolledText = bufferText(buffer)
    check("row 599" in scrolledText) { "wheel scrolling did not reach the final generated row" }

    val scrolledLayout = checkNotNull(app.lastLayout)
    val clickTarget = checkNotNull(scrolledLayout.main).content
    val clickX = clickTarget.x + 1
    val clickY = clickTarget.y + 1
    val hit = checkNotNull(app.panels.hitTest(clickX, clickY))
    val clickedRow = checkNotNull(hit.contentPoint).y
    app.handle(MouseEvent(x = clickX, y = clickY, left = true))
    buffer = app.render(width = 44, height = 8)
    check(app.state.selectedRow == clickedRow) { "the click selected the wrong generated row" }
    check(app.state.selectedRow in app.state.checkedRows) { "the click did not toggle the selected row" }
    check(buffer.width == 44 && buffer.height == 8) { "the resized frame did not use the new dimensions" }

    check(app.cycleHelp() == PanelState.MAXIMIZED) { "the help panel did not maximize" }
    check(app.cycleHelp() == PanelState.MINIMIZED) { "the help panel did not minimize" }
    check(app.cycleHelp() == PanelState.NORMAL) { "the help panel did not restore its normal state" }

    val uniform = ExampleApp(ExampleLayoutMode.UNIFORM)
    uniform.render(width = 100, height = 12)
    val uniformLayout = checkNotNull(uniform.lastLayout)
    check(uniformLayout.main == null && uniformLayout.sides.size == 2) {
        "the uniform layout did not place both fresh panel instances"
    }

    val animation = animationProbe()
    val firstFrame = renderAnimationFrame(animation.firstFrame)
    val secondFrame = renderAnimationFrame(animation.secondFrame)
    check(firstFrame.get(1, 0).char == "A") { "the first animation frame painted the wrong glyph" }
    check(secondFrame.get(1, 0).char == "B") { "the second animation frame painted the wrong glyph" }

    return SmokeResult(
        renderedRows = EXAMPLE_ROW_COUNT,
        helpContainsMovement = "PgUp/PgDn" in initialText,
        animationCompleted = animation.completedFrameCount == 0,
    )
}

/** Interactive entry point; run the installed distribution's launcher from a real terminal. */
public fun main(args: Array<String>) {
    if ("--hello" in args) {
        tenterexample.hello.main()
        return
    }
    if ("--headless" in args) {
        println(runHeadlessSmoke())
        return
    }

    val terminal = Terminal()
    terminal.withScreen(DefaultRolePalette) { renderer ->
        runInteractive(terminal, renderer)
    }
}

private fun runInteractive(terminal: Terminal, renderer: ScreenRenderer) {
    val app = ExampleApp(ExampleLayoutMode.MAIN_AND_SIDES)
    var size = terminal.updateSize()
    renderer.render(app.render(size.width, size.height))

    runBlocking {
        merge(
            terminal.inputEvents(MouseTracking.Normal) { event ->
                ExampleKeyMap.map.resolve(listOf(ExampleContext.LIST), event) == ExampleAction.QUIT
            },
            terminal.resizeEvents(),
        ).takeWhile { it !is TerminalEvent.Quit }.collect { event ->
            when (event) {
                is TerminalEvent.Input -> app.handle(event.event)
                is TerminalEvent.Resized -> size = event.size
                TerminalEvent.Quit -> Unit
            }
            if (app.running) renderer.render(app.render(size.width, size.height))
        }
    }
}

private fun renderAnimationFrame(view: tenter.view.View): ScreenBuffer {
    val buffer = ScreenBuffer(3, 1)
    view.draw(Canvas.of(buffer))
    return buffer
}

private fun bufferText(buffer: ScreenBuffer): String =
    (0 until buffer.height).joinToString("\n") { y ->
        (0 until buffer.width).joinToString("") { x -> buffer.get(x, y).char }
    }
