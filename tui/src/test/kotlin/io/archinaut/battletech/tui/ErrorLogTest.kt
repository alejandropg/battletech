package io.archinaut.battletech.tui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.readText

internal class ErrorLogTest {

    @TempDir
    private lateinit var dir: Path

    @Test
    fun `a file log appends each stack trace and names the file`() {
        val path = dir.resolve("tui.log")
        val log = FileErrorLog(path)

        val first = log.record("first event", IllegalStateException("boom"))
        log.record("second event", IllegalArgumentException("bang"))

        assertThat(first).isEqualTo("details in $path")
        assertThat(path.readText())
            .contains("while processing first event", "IllegalStateException: boom")
            .contains("while processing second event", "IllegalArgumentException: bang")
    }

    @Test
    fun `a file log that cannot write says so instead of throwing`() {
        val path = dir.resolve("a-directory").createDirectory()

        assertThat(FileErrorLog(path).record("event", IllegalStateException("boom")))
            .startsWith("could not write $path")
    }

    @Test
    fun `the flash text names the error and where its details went`() {
        assertThat(internalErrorText(IllegalStateException("boom"), "details in x.log"))
            .isEqualTo("internal error: boom — details in x.log")
        assertThat(internalErrorText(IllegalStateException(), "details in x.log"))
            .isEqualTo("internal error: IllegalStateException — details in x.log")
    }
}

/** An [ErrorLog] that keeps every record in memory, for asserting what a loop swallowed. */
internal class RecordingErrorLog : ErrorLog {
    val records: MutableList<Pair<String, Throwable>> = mutableListOf()

    override fun record(context: String, error: Throwable): String {
        records += context to error
        return "logged"
    }
}
