package tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * Something a key can do. The [id] is a stable, human-readable name — it exists so the HELP panel
 * and a future config file can refer to an action without depending on its type. Implementations
 * live wherever the thing they act on lives: [PanAction]/[ScrollAction] here, everything that names
 * an application object (a panel, a game unit) in the application.
 */
public interface InputAction {
    public val id: String
}

/**
 * This key press in the one form [KeyMap] matches against, so that two spellings of the same
 * keystroke are the same key.
 *
 * A printable key is reported by the terminal as the character it actually produces, so `shift+h`
 * arrives as `key = "H"` with [KeyboardEvent.shift] set — the modifier carries nothing the key
 * does not already say. Folding single-character keys to lowercase and clearing `shift` for them
 * is what keeps `alt+H` and `alt+h` the same key. Named keys (`"ArrowUp"`, `"PageUp"`, …) keep
 * `shift` as reported, so `shift+ArrowUp` stays expressible.
 */
public fun KeyboardEvent.normalized(): KeyboardEvent =
    if (key.length == 1) copy(key = key.lowercase(), shift = false) else this

/**
 * One chord bound to one action, credited to the [HintGroup] that documents it in a help panel.
 *
 * [chord] is a plain Mordant [KeyboardEvent] — `tenter` adds vocabulary on top of Mordant rather
 * than wrapping it, so a caller already holding an event binds it directly. It names the keystroke
 * a binding waits for, not one that happened; the property name carries that, not a separate type.
 *
 * A chord is only ever matched against [normalized] output, so one that [normalized] can never
 * produce could never fire. `KeyBinding(KeyboardEvent("H", alt = true), …)` would be exactly that:
 * silently dead, and indistinguishable from a working binding at the declaration site. Rejecting it
 * here is what keeps "declared" and "resolvable" the same set — including for a future config file,
 * whose parsed bindings are built through this same constructor.
 */
public data class KeyBinding(
    val chord: KeyboardEvent,
    val action: InputAction,
    val hintGroup: String,
) {
    init {
        require(chord.key.isNotEmpty()) { "A key binding needs a key" }
        require(chord == chord.normalized()) {
            "Chord $chord could never be resolved: KeyMap matches against normalized() output, which " +
                "would fold this to ${chord.normalized()}"
        }
    }
}

/**
 * One row of a help panel: a hand-written [label] for the keys (`"←→↑↓/wasd"`) and what they do.
 * Which rows exist, and that every binding is credited to one, are derived and test-enforced; only
 * the compact glyph [label] stays prose, because auto-joining eight chords reads far worse than
 * `"←→↑↓/wasd"` in the panel a user actually looks at.
 *
 * [bindingless] marks a row that documents something implemented outside the keymap — the mouse
 * wheel, which is deliberately not a binding. It is the only exemption from "every declared group
 * has at least one binding".
 */
public data class HintGroup(
    val id: String,
    val label: String,
    val description: String,
    val bindingless: Boolean = false,
)

/**
 * One addressable set of bindings.
 *
 * [title] is the help panel's section heading, or null for a layer that never renders a section of
 * its own (its bindings are credited to a [HintGroup] declared on another layer — see
 * `Keybindings.DEFAULT`'s `PANEL_SCROLL`).
 *
 * [shadowing] marks a layer that deliberately binds chords a lower-precedence layer also binds —
 * `PANEL_SCROLL`'s bare arrows over the phases' cursor arrows. It is what separates a designed
 * override from an accidental collision, and it is the only exemption from the collision invariant.
 */
public data class KeyLayer(
    val title: String?,
    val bindings: List<KeyBinding>,
    val hintGroups: List<HintGroup> = emptyList(),
    val shadowing: Boolean = false,
)
