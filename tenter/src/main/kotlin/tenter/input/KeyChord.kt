package tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * One key press, as a value: Mordant's [KeyboardEvent] shape minus the event-ness. This is the key
 * side of a [KeyBinding] and the unit of lookup in a [KeyMap].
 *
 * [key] uses Mordant's own spelling — the character itself for printable keys (`"h"`, `"+"`), a
 * name for special keys (`"ArrowLeft"`, `"PageUp"`, `"Home"`, `"Enter"`, `"Tab"`, `"Escape"`,
 * `" "` for space). That spelling is already config-file-friendly, which is why no separate key
 * vocabulary exists.
 */
public data class KeyChord(
    val key: String,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    public companion object {
        /**
         * The chord an event names, normalised for lookup.
         *
         * A printable key is reported by the terminal as the character actually produced, so
         * `shift+h` arrives as `key = "H"` with `shift` set — the modifier carries no information
         * the key doesn't already. Folding single-character keys to lowercase and clearing [shift]
         * for them is what keeps `alt+H` and `alt+h` the same chord, as `ChromeInput.panelKey`'s
         * `lowercaseChar()` did. Named keys (`"ArrowUp"`, `"PageUp"`, …) keep [shift] as reported,
         * so a future `shift+ArrowUp` binding stays expressible.
         */
        public fun of(event: KeyboardEvent): KeyChord =
            if (event.key.length == 1) {
                KeyChord(event.key.lowercase(), event.ctrl, event.alt, shift = false)
            } else {
                KeyChord(event.key, event.ctrl, event.alt, event.shift)
            }
    }
}
