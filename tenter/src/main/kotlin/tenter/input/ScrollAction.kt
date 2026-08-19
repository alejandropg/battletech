package tenter.input

/** Keyboard scrolling for the focused panel — see [ChromeInput.scrollAction]. */
public sealed interface ScrollAction {
    /** -1 up, +1 down: one content row. */
    public data class Lines(val delta: Int) : ScrollAction

    /** -1 up, +1 down: one viewport height. */
    public data class Pages(val delta: Int) : ScrollAction
}
