package tenterexample

import com.github.ajalt.mordant.input.KeyboardEvent
import tenter.input.HintGroup
import tenter.input.KeyBinding
import tenter.input.KeyLayer
import tenter.input.KeyMap

internal object ExampleKeyMap {
    internal val map: KeyMap<ExampleContext> = KeyMap(
        mapOf(
            ExampleContext.LIST to KeyLayer(
                title = "LIST",
                bindings = listOf(
                    KeyBinding(KeyboardEvent("k"), ExampleAction.MOVE_UP, "movement"),
                    KeyBinding(KeyboardEvent("j"), ExampleAction.MOVE_DOWN, "movement"),
                    KeyBinding(KeyboardEvent("q"), ExampleAction.QUIT, "quit"),
                ),
                hintGroups = listOf(
                    HintGroup("movement", "j/k", "move through the generated rows"),
                    HintGroup("quit", "q", "quit the example"),
                ),
            ),
        ),
    )
}
