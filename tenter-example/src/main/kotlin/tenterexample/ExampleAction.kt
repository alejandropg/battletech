package tenterexample

import tenter.input.InputAction

internal enum class ExampleAction(override val id: String) : InputAction {
    MOVE_UP("move-up"),
    MOVE_DOWN("move-down"),
    QUIT("quit"),
}
