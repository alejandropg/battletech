package io.archinaut.tenter.terminal

import com.github.ajalt.mordant.terminal.Terminal
import io.archinaut.tenter.palette.RolePalette
import io.archinaut.tenter.screen.ScreenRenderer

// Mordant exposes cursor control but not the alternate screen buffer. These are raw escapes,
// gated by the same interactive check as Mordant's cursor implementation.
private const val ENTER_ALT_SCREEN: String = "\u001b[?1049h"
private const val EXIT_ALT_SCREEN: String = "\u001b[?1049l"

/**
 * Runs [block] inside one alternate-screen and cursor scope.
 *
 * The [palette] is selected by the caller and is used only to construct the renderer. This
 * operation does not collect input, run an event loop, or make the mutable renderer safe to use
 * from multiple threads. A caller may put a synchronous `runBlocking` application inside [block].
 * Input flows acquire and release raw mode separately when they are collected.
 *
 * Entry and cleanup are exception-safe, including a failure after one of the entry operations has
 * already written to the terminal. Cleanup is idempotent. If the block or entry fails and cleanup
 * fails as well, the original failure is rethrown and the cleanup failure is suppressed on it.
 * Noninteractive terminals do not receive alternate-screen or cursor escape sequences.
 */
public fun <T> Terminal.withScreen(
    palette: RolePalette,
    block: (ScreenRenderer) -> T,
): T {
    val lifecycle = ScreenLifecycle(this)
    var failure: Throwable? = null
    try {
        lifecycle.enter()
        val renderer = ScreenRenderer(this, palette)
        renderer.clear()
        return block(renderer)
    } catch (thrown: Throwable) {
        failure = thrown
        throw thrown
    } finally {
        try {
            lifecycle.close()
        } catch (cleanupFailure: Throwable) {
            val originalFailure = failure
            if (originalFailure == null) {
                throw cleanupFailure
            }
            originalFailure.addSuppressed(cleanupFailure)
        }
    }
}

private class ScreenLifecycle(
    private val terminal: Terminal,
) {
    private var cursorHidden: Boolean = false
    private var alternateScreenEntered: Boolean = false
    private var closed: Boolean = false

    internal fun enter() {
        check(!closed) { "screen lifecycle is already closed" }
        if (!terminal.terminalInfo.interactive) return

        // Set the flags before the writes. A sink can fail after partially accepting a write, so
        // cleanup must still attempt to undo every operation whose escape may have been sent.
        cursorHidden = true
        terminal.cursor.hide()
        alternateScreenEntered = true
        terminal.rawPrint(ENTER_ALT_SCREEN)
    }

    internal fun close() {
        if (closed) return
        closed = true

        var failure: Throwable? = null
        if (cursorHidden) {
            try {
                terminal.cursor.show()
            } catch (thrown: Throwable) {
                failure = thrown
            }
        }
        if (alternateScreenEntered) {
            try {
                terminal.rawPrint(EXIT_ALT_SCREEN)
            } catch (thrown: Throwable) {
                if (failure == null) {
                    failure = thrown
                } else {
                    failure.addSuppressed(thrown)
                }
            }
        }
        if (failure != null) throw failure
    }
}
