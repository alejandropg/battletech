package tenterexample

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlinx.coroutines.flow.flow
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.terminal.terminalEvents
import tenterexample.hello.runHello

internal class HelloExampleTest {
    @Test
    fun `readme entry point is the compiled hello source`() {
        val readme = Files.readString(Path.of("../README.md"))
        val snippet = readme.substringAfter("```kotlin\n").substringBefore("\n```")
        val source = Files.readString(Path.of("src/main/kotlin/tenterexample/hello/HelloMain.kt"))

        assertEquals(source.substringAfter("package tenterexample.hello\n").trim(), snippet.trim())
    }

    @Test
    fun `hello paints before reading and quits without consuming later input`() {
        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE, width = 50, height = 8)
        val terminal = Terminal(ansiLevel = AnsiLevel.NONE, terminalInterface = recorder)
        var read = 0
        val raw = flow {
            assertTrue(recorder.output().contains("Hello from Tenter"))
            read++
            emit(KeyboardEvent("x"))
            read++
            emit(KeyboardEvent("q"))
            error("input after quit must not be read")
        }

        runHello(terminal, terminalEvents(raw) { it.key == "q" })

        assertEquals(2, read)
    }
}
