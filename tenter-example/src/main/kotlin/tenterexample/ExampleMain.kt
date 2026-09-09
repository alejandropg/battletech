package tenterexample

import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.Terminal
import tenter.panel.Panel
import tenter.panel.PanelSet
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer
import tenter.screen.ScreenRenderer
import tenter.terminal.withScreen
import tenter.view.HelpView
import tenter.view.View

private const val ROW_COUNT: Int = 40

public fun runHeadlessSmoke(): SmokeResult {
    val set = panelSet()
    val initialBuffer = ScreenBuffer(width = 80, height = 24)
    set.render(
        canvas = Canvas.of(initialBuffer),
        inputs = Unit,
        visible = setOf(ExamplePanelId.ROWS, ExamplePanelId.HELP),
        reservedTop = 0,
    )

    val initialText = bufferText(initialBuffer)
    check("row 0" in initialText) { "the rendered list did not contain its first generated row" }
    set.scrollFocused(dx = 0, dy = ROW_COUNT)

    val scrolledBuffer = ScreenBuffer(width = 80, height = 24)
    set.render(
        canvas = Canvas.of(scrolledBuffer),
        inputs = Unit,
        visible = setOf(ExamplePanelId.ROWS, ExamplePanelId.HELP),
        reservedTop = 0,
    )

    val screenText = bufferText(scrolledBuffer)
    check("row 39" in screenText) { "the rendered list did not reach its final generated row after scrolling" }
    check("j/k" in screenText) { "the rendered help did not contain the keymap hints" }

    val animation = animationProbe()
    val firstFrame = renderAnimationFrame(animation.firstFrame)
    val secondFrame = renderAnimationFrame(animation.secondFrame)
    check(firstFrame.get(1, 0).char == "A") { "the first animation frame painted the wrong glyph" }
    check(secondFrame.get(1, 0).char == "B") { "the second animation frame painted the wrong glyph" }

    return SmokeResult(
        renderedRows = ROW_COUNT,
        helpContainsMovement = "j/k" in screenText,
        animationCompleted = animation.completedFrameCount == 0,
    )
}

/**
 * The interactive entry point uses the toolkit's scoped screen lifecycle: it renders one frame
 * and always restores the terminal in `finally`. The headless mode used by tests and packaged
 * checks never enters this path or reads raw input.
 */
public fun main(args: Array<String>) {
    if ("--headless" in args) {
        println(runHeadlessSmoke())
        return
    }

    val terminal = Terminal()
    terminal.withScreen(ExamplePalette) { renderer ->
        val size = terminal.updateSize()
        renderer.render(renderFrame(size))
    }
}

private fun panelSet(): PanelSet<ExamplePanelId, Unit> {
    val rows = Panel<ExamplePanelId, Unit>(
        id = ExamplePanelId.ROWS,
        title = "ROWS",
        badge = "R",
        normal = { Panel.Presentation(ExampleListView(ROW_COUNT), width = 0) },
    )
    val help = Panel<ExamplePanelId, Unit>(
        id = ExamplePanelId.HELP,
        title = "HELP",
        badge = "H",
        normal = { Panel.Presentation(HelpView(listOf(ExampleKeyMap.map.hints(ExampleContext.LIST))), width = 28) },
    )
    return PanelSet.mainAndSides(rows, listOf(help))
}

private fun renderFrame(size: Size): ScreenBuffer {
    val buffer = ScreenBuffer(size.width.coerceAtLeast(40), size.height.coerceAtLeast(10))
    panelSet().render(
        canvas = Canvas.of(buffer),
        inputs = Unit,
        visible = setOf(ExamplePanelId.ROWS, ExamplePanelId.HELP),
        reservedTop = 0,
    )
    return buffer
}

private fun renderAnimationFrame(view: View): ScreenBuffer {
    val buffer = ScreenBuffer(3, 1)
    view.draw(Canvas.of(buffer))
    return buffer
}

private fun bufferText(buffer: ScreenBuffer): String =
    (0 until buffer.height).joinToString("\n") { y ->
        (0 until buffer.width).joinToString("") { x -> buffer.get(x, y).char }
    }
