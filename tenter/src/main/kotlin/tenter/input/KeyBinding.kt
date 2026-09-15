package tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * One chord bound to one action, credited to the [HintGroup] that documents it in a help panel.
 *
 * [chord] is a plain Mordant [KeyboardEvent] — `tenter` adds vocabulary on top of Mordant rather
 * than wrapping it, so a caller already holding an event binds it directly. It names the keystroke
 * a binding waits for, not one that happened; the property name carries that, not a separate type.
 *
 * The chord is compared to the event exactly as the terminal reported it — `tenter` applies no
 * folding. A binding must therefore be declared in the form its platform actually produces; whether
 * two spellings are the same keystroke (`?` vs. shift+`?` across posix and Windows, say) is an
 * application decision, not one `tenter` makes on a caller's behalf.
 */
public data class KeyBinding(
    val chord: KeyboardEvent,
    val action: InputAction,
    val hintGroup: String,
) {
    init {
        require(chord.key.isNotEmpty()) { "A key binding needs a key" }
    }
}
