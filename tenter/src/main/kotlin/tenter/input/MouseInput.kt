package tenter.input

import com.github.ajalt.mordant.input.MouseEvent

/**
 * Mouse input mappings that belong to the terminal chrome itself — scrolling — rather than to any
 * application-specific content. Keyboard chrome bindings (quit, panel chords, scrolling, panning)
 * moved to the declarative [KeyMap] — see `Keybindings` in the application layer.
 */
public object MouseInput {

    /** Number of rows scrolled per wheel tick (matches lazygit default). */
    public const val SCROLL_STEP: Int = 2

    /**
     * Returns the scroll delta for a mouse event: negative for scroll-up, positive for
     * scroll-down, null when the event carries no scroll intent.
     *
     * [overPanel] must be true when the pointer is over a scrollable panel slot; false
     * when it is over any other, non-scrollable area.
     *
     * ### Mordant 3.0.2 wheel-parsing workaround
     * Mordant's posix event parser checks `wheelUp = cb == 64` and `wheelDown = cb == 65`,
     * but real terminals in X10/1005 encoding (which Mordant enables) transmit wheel events
     * as button-code-plus-32: cb 96 (wheel up) and cb 97 (wheel down).
     * `96 and 3 == 0` → Mordant decodes wheel-up as `left = true`; `97 and 3 == 1` →
     * `right = true`.  Consequently, on any real posix terminal `wheelUp` and `wheelDown`
     * are never true and a physical wheel tick is indistinguishable from a button press.
     *
     * Workaround: over a scrollable panel slot, treat a left press as wheel-up and a right
     * press as wheel-down.  Panels have no click semantics of their own, so nothing is lost.
     * Content click handling is unaffected because it is reached only when [overPanel] is false.
     * The canonical `wheelUp`/`wheelDown` branches keep precedence so the code is correct on
     * Windows and future-proof once Mordant is patched upstream.
     *
     * A button *release* arrives with all button flags false and yields null.
     */
    public fun scrollDelta(event: MouseEvent, overPanel: Boolean): Int? = when {
        event.wheelUp -> -SCROLL_STEP
        event.wheelDown -> SCROLL_STEP
        overPanel && event.left -> -SCROLL_STEP
        overPanel && event.right -> SCROLL_STEP
        else -> null
    }
}
