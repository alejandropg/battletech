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

    /** Returns a validated scroll delta: negative for wheel-up, positive for wheel-down. */
    public fun scrollDelta(event: MouseEvent, step: Int = SCROLL_STEP): Int? {
        require(step > 0) { "mouse scroll step must be positive: $step" }
        return when {
            event.wheelUp -> -step
            event.wheelDown -> step
            else -> null
        }
    }
}
