package tenterexample

import com.github.ajalt.mordant.input.KeyboardEvent
import io.archinaut.tenter.input.HintGroup
import io.archinaut.tenter.input.KeyBinding
import io.archinaut.tenter.input.KeyLayer
import io.archinaut.tenter.input.KeyMap
import io.archinaut.tenter.input.ScrollAction

internal object ExampleKeyMap {
    internal val map: KeyMap<ExampleContext> = KeyMap(
        mapOf(
            ExampleContext.LIST to KeyLayer(
                title = "LIST",
                bindings = listOf(
                    KeyBinding(KeyboardEvent("ArrowUp"), ExampleAction.MOVE_UP, "movement"),
                    KeyBinding(KeyboardEvent("ArrowDown"), ExampleAction.MOVE_DOWN, "movement"),
                    KeyBinding(KeyboardEvent(" "), ExampleAction.TOGGLE, "toggle"),
                    KeyBinding(KeyboardEvent("h"), ExampleAction.FOCUS_HELP, "panels"),
                    KeyBinding(KeyboardEvent("m"), ExampleAction.CYCLE_PANEL, "panels"),
                    KeyBinding(KeyboardEvent("PageUp"), ScrollAction.Pages(-1), "scroll"),
                    KeyBinding(KeyboardEvent("PageDown"), ScrollAction.Pages(1), "scroll"),
                    KeyBinding(KeyboardEvent("q"), ExampleAction.QUIT, "quit"),
                ),
                hintGroups = listOf(
                    HintGroup("movement", "↑/↓", "move through the generated rows"),
                    HintGroup("toggle", "space", "toggle the selected row"),
                    HintGroup("panels", "h/m", "focus help or cycle its size"),
                    HintGroup("scroll", "PgUp/PgDn", "manually scroll the focused panel"),
                    HintGroup("quit", "q", "quit the example"),
                ),
            ),
        ),
    )
}
