package tenter.input

/** Keyboard scrolling for the focused panel. */
public sealed interface ScrollAction : InputAction {
    /** -1 up, +1 down: one content row. */
    public data class Lines(val delta: Int) : ScrollAction {
        override val id: String get() = if (delta < 0) "scroll.lines.up" else "scroll.lines.down"
    }

    /** -1 up, +1 down: one viewport height. */
    public data class Pages(val delta: Int) : ScrollAction {
        override val id: String get() = if (delta < 0) "scroll.pages.up" else "scroll.pages.down"
    }
}
