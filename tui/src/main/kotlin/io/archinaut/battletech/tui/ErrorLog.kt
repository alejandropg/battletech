package io.archinaut.battletech.tui

import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.Instant
import kotlin.io.path.Path

/**
 * Where a screen loop records a throwable it swallowed to stay alive. While a screen is up,
 * stderr is drawn straight over the frame and vanishes with the alternate screen, so the loops
 * report here instead and show the user a flash message built from [record]'s answer.
 */
internal fun interface ErrorLog {
    /** Records [error], raised while handling [context], and returns where its details went, for the user. */
    fun record(context: String, error: Throwable): String

    companion object {
        /** The log file both screens append to: one per machine, in the JVM's temp directory. */
        val DEFAULT: ErrorLog = FileErrorLog(Path(System.getProperty("java.io.tmpdir"), "battletech-tui.log"))
    }
}

/** An [ErrorLog] appending a timestamped stack trace to [path] for each record. */
internal class FileErrorLog(private val path: Path) : ErrorLog {
    override fun record(context: String, error: Throwable): String = try {
        PrintWriter(Files.newBufferedWriter(path, CREATE, APPEND)).use { out ->
            out.println("${Instant.now()} unhandled throwable while processing $context:")
            error.printStackTrace(out)
        }
        "details in $path"
    } catch (e: IOException) {
        "could not write $path: ${e.message}"
    }
}

/** The flash text announcing [error], with [where] its details went. */
internal fun internalErrorText(error: Throwable, where: String): String =
    "internal error: ${error.message ?: error::class.simpleName} — $where"
