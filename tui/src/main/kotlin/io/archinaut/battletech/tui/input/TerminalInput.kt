package io.archinaut.battletech.tui.input

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseTracking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import io.archinaut.tenter.terminal.Terminal
import io.archinaut.tenter.terminal.TerminalEvent

/**
 * This terminal's input and resize events as an application's own event type, ending at [keys]'
 * quit chord: the quit keypress itself is absorbed, and [quit] is emitted once instead, so the
 * application loop stops on its own sentinel while the rest of its event sources keep running
 * until it does. Stopping here is also what releases raw mode — see tenter's
 * [Terminal.events].
 */
internal fun <E> Terminal.uiEvents(keys: Keybindings, quit: E, convert: (TerminalEvent) -> E): Flow<E> =
    events(MouseTracking.Normal)
        .takeWhile { !(it is TerminalEvent.Input && it.event.let { event -> event is KeyboardEvent && keys.isQuit(event) }) }
        .map(convert)
        .onCompletion { cause -> if (cause == null) emit(quit) }
