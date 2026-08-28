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
 *
 * A chord is only ever matched against [of]'s output, so one that [of] can never produce could
 * never fire. Declaring `KeyChord("H", alt = true)` would be exactly that: silently dead, and
 * indistinguishable from a working binding at the declaration site. The constructor rejects it
 * instead, which is what keeps "declared" and "resolvable" the same set — including for a future
 * config file, whose parsed chords come through this same constructor.
 */
public data class KeyChord(
    val key: String,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
) {
    init {
        require(key.isNotEmpty()) { "A key chord needs a key" }
        if (key.length == 1) {
            require(key == key.lowercase()) {
                "Single-character key '$key' must be declared lowercase — a shifted printable key is " +
                    "reported by the terminal as the character it produces, so KeyChord.of folds it to " +
                    "lowercase and '$key' could never be resolved"
            }
            require(!shift) {
                "Single-character key '$key' must not carry shift — KeyChord.of clears it for printable " +
                    "keys, since the character already encodes it"
            }
        }
    }

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
