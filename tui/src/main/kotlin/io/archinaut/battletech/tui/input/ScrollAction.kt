package io.archinaut.battletech.tui.input

/** Keyboard scrolling for the focused panel. */
public sealed interface ScrollAction : Action {

    /** -1 up, +1 down: one content row. */
    public data class Lines(val delta: Int) : ScrollAction

    /** -1 up, +1 down: one viewport height. */
    public data class Pages(val delta: Int) : ScrollAction
}
