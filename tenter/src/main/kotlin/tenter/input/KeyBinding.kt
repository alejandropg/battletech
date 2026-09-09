package tenter.input

import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * Something a key can do. The [id] is a stable, human-readable name — it exists so the HELP panel
 * and a caller's configuration can refer to an action without depending on its type. Implementations
 * live wherever the thing they act on lives: [PanAction]/[ScrollAction] here, everything that names
 * an application object (a panel, a game unit) in the application.
 */
public interface InputAction {
    public val id: String
}

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
 * Structural validity, including sectionless credits, is owned by [KeyMap] at construction.
 */
public data class KeyLayer(
    public val title: String?,
    public val bindings: List<KeyBinding>,
    public val hintGroups: List<HintGroup> = emptyList(),
)
