package io.archinaut.battletech.tui.input

import com.github.ajalt.mordant.input.KeyboardEvent
import io.archinaut.tenter.screen.Size
import io.archinaut.tenter.terminal.Terminal
import io.archinaut.tenter.terminal.TerminalEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TerminalInputTest {

    private fun TerminalEvent.describe(): String = when (this) {
        is TerminalEvent.Resized -> "resized ${size.width}x${size.height}"
        is TerminalEvent.Input -> "key ${(event as KeyboardEvent).key}"
    }

    @Test
    fun `the quit chord ends the events with the quit sentinel and is not converted`() {
        var afterQuit = false
        val terminal = Terminal.headless(width = 20, height = 5) {
            key("a")
            key("c", ctrl = true)
            afterQuit = true
        }

        val events = terminal.withScreen { screen ->
            runBlocking { screen.uiEvents(Keybindings.DEFAULT, quit = "quit") { it.describe() }.toList() }
        }

        assertEquals(listOf("resized 20x5", "key a", "quit"), events)
        assertEquals(false, afterQuit, "input after the quit chord must not be read")
    }

    @Test
    fun `input that ends without the quit chord still ends with the quit sentinel`() {
        val terminal = Terminal.headless(width = 20, height = 5) { resize(30, 6) }

        val events = terminal.withScreen { screen ->
            runBlocking { screen.uiEvents(Keybindings.DEFAULT, quit = "quit") { it.describe() }.toList() }
        }

        assertEquals(listOf("resized 20x5", "resized 30x6", "quit"), events)
        assertEquals(Size(30, 6), terminal.withScreen { Size(it.area.width, it.area.height) })
    }
}
