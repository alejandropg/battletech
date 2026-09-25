package tenterexample.hello

import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import tenter.screen.Canvas
import tenter.palette.DefaultRolePalette
import tenter.screen.ScreenBuffer
import tenter.terminal.TerminalEvent
import tenter.terminal.inputEvents
import tenter.terminal.withScreen
import tenter.view.contentView

public fun main() {
    val terminal = Terminal()
    runHello(terminal, terminal.inputEvents(MouseTracking.Normal) { it.key == "q" })
}

/** The application owns the loop; tests can supply input without acquiring a real terminal. */
public fun runHello(terminal: Terminal, events: Flow<TerminalEvent>) {
    val greeting = contentView { cursor -> cursor.writeLine("Hello from Tenter — press q to quit") }
    terminal.withScreen(DefaultRolePalette) { renderer ->
        val size = terminal.updateSize()
        val buffer = ScreenBuffer(size.width, size.height)
        greeting.draw(Canvas.of(buffer))
        renderer.render(buffer)
        runBlocking {
            events.takeWhile { it !is TerminalEvent.Quit }.collect { }
        }
    }
}
