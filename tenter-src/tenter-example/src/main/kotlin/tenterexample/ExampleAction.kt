package tenterexample

import io.archinaut.tenter.input.InputAction

internal enum class ExampleAction(override val id: String) : InputAction {
    MOVE_UP("move-up"),
    MOVE_DOWN("move-down"),
    TOGGLE("toggle"),
    FOCUS_HELP("focus-help"),
    CYCLE_PANEL("cycle-panel"),
    QUIT("quit"),
}
