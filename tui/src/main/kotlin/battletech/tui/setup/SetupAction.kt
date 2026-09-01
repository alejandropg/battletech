package battletech.tui.setup

import tenter.input.InputAction

internal sealed interface SetupAction : InputAction {
    data class MoveCursor(val delta: Int) : SetupAction {
        override val id: String get() = "setup.moveCursor.$delta"
    }

    data class Adjust(val delta: Int) : SetupAction {
        override val id: String get() = "setup.adjust.$delta"
    }

    data object Toggle : SetupAction {
        override val id: String get() = "setup.toggle"
    }

    data object NextPanel : SetupAction {
        override val id: String get() = "setup.nextPanel"
    }

    data object Commit : SetupAction {
        override val id: String get() = "setup.commit"
    }
}
