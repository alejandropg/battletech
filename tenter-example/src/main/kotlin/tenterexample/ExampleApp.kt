package tenterexample

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import tenter.input.MouseInput
import tenter.input.ScrollAction
import tenter.panel.Panel
import tenter.panel.PanelLayout
import tenter.panel.PanelSet
import tenter.panel.PanelState
import tenter.screen.Canvas
import tenter.screen.ScreenBuffer
import tenter.view.ContentView
import tenter.view.HelpView
import tenter.view.Stack
import tenter.view.contentView

internal enum class ExampleLayoutMode {
    MAIN_AND_SIDES,
    UNIFORM,
}

/** Small application-owned controller showing how a consumer dispatches toolkit observations. */
internal class ExampleApp(
    private val layoutMode: ExampleLayoutMode,
) {
    internal val panels: PanelSet<ExamplePanelId, ExampleState> = createPanelSet(layoutMode)
    internal var state: ExampleState = ExampleState()
        private set
    internal var running: Boolean = true
        private set
    internal var lastLayout: PanelLayout<ExamplePanelId>? = null
        private set

    internal fun render(width: Int, height: Int): ScreenBuffer {
        val buffer = ScreenBuffer(width.coerceAtLeast(1), height.coerceAtLeast(1))
        lastLayout = panels.render(
            canvas = Canvas.of(buffer),
            inputs = state,
            visible = VISIBLE,
            reservedTop = 0,
        )
        return buffer
    }

    internal fun handle(event: InputEvent) {
        when (event) {
            is KeyboardEvent -> handleKey(event)
            is MouseEvent -> handleMouse(event)
        }
    }

    internal fun handleKey(event: KeyboardEvent) {
        when (val action = ExampleKeyMap.map.resolve(listOf(ExampleContext.LIST), event)) {
            ExampleAction.MOVE_UP -> moveSelection(-1)
            ExampleAction.MOVE_DOWN -> moveSelection(1)
            ExampleAction.TOGGLE -> toggleSelected()
            ExampleAction.FOCUS_HELP -> panels.focus(ExamplePanelId.HELP)
            ExampleAction.CYCLE_PANEL -> {
                panels.focus(ExamplePanelId.HELP)
                panels.cycleFocusedState(1)
            }
            ExampleAction.QUIT -> running = false
            is ScrollAction.Lines -> panels.scrollFocused(0, action.delta)
            is ScrollAction.Pages -> panels.pageFocused(action.delta)
            null -> Unit
        }
    }

    internal fun handleMouse(event: MouseEvent) {
        val hit = panels.hitTest(event.x, event.y)
        val scrollDelta = MouseInput.scrollDelta(event)
        if (scrollDelta != null) {
            hit?.let { panels.scroll(it.id, 0, scrollDelta) }
            return
        }
        if (!event.left || hit?.id != ExamplePanelId.ROWS) return
        val contentPoint = hit.contentPoint ?: return
        if (contentPoint.y !in 0 until EXAMPLE_ROW_COUNT) return

        state = state.copy(
            selectedRow = contentPoint.y,
            checkedRows = state.checkedRows.toggle(contentPoint.y),
        )
        panels.focus(ExamplePanelId.ROWS)
    }

    internal fun cycleHelp(): PanelState? {
        panels.focus(ExamplePanelId.HELP)
        panels.cycleFocusedState(1)
        return panels.stateOf(ExamplePanelId.HELP)
    }

    private fun moveSelection(delta: Int) {
        state = state.copy(selectedRow = (state.selectedRow + delta).coerceIn(0, EXAMPLE_ROW_COUNT - 1))
        panels.focus(ExamplePanelId.ROWS)
    }

    private fun toggleSelected() {
        state = state.copy(checkedRows = state.checkedRows.toggle(state.selectedRow))
        panels.focus(ExamplePanelId.ROWS)
    }

    private fun createPanelSet(mode: ExampleLayoutMode): PanelSet<ExamplePanelId, ExampleState> {
        val rows = Panel<ExamplePanelId, ExampleState>(
            id = ExamplePanelId.ROWS,
            title = "CATALOG",
            badge = "R",
            normal = { input -> Panel.Presentation.allocated(ExampleListView(EXAMPLE_ROW_COUNT, input)) },
        )
        val help = Panel<ExamplePanelId, ExampleState>(
            id = ExamplePanelId.HELP,
            title = "HELP",
            badge = "H",
            normal = { input -> Panel.Presentation.fixedWidth(helpContent(input), width = HELP_WIDTH) },
            minimized = { input -> Panel.Presentation.fixedWidth(helpContent(input), width = Panel.MINIMIZED_WIDTH) },
            maximized = { input -> Panel.Presentation.allocated(helpContent(input)) },
        )
        return when (mode) {
            ExampleLayoutMode.MAIN_AND_SIDES -> PanelSet.mainAndSides(rows, listOf(help))
            ExampleLayoutMode.UNIFORM -> PanelSet.uniform(
                listOf(rows, help), reservedColumns = 2, fixedWidthPanels = setOf(ExamplePanelId.HELP),
            )
        }
    }

    private fun helpContent(input: ExampleState): ContentView = Stack(
        listOf(
            HelpView(listOf(ExampleKeyMap.map.hints(ExampleContext.LIST))),
            contentView { cursor ->
                cursor.newLine()
                cursor.writeLine("Selected: row ${input.selectedRow}")
                cursor.writeLine("Checked: ${input.checkedRows.size}")
            },
        ),
    )

    private companion object {
        private const val HELP_WIDTH: Int = 36
        private val VISIBLE: Set<ExamplePanelId> = setOf(ExamplePanelId.ROWS, ExamplePanelId.HELP)

        private fun Set<Int>.toggle(value: Int): Set<Int> = if (value in this) this - value else this + value
    }
}
