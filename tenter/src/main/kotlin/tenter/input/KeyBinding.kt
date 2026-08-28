package tenter.input

/**
 * Something a key can do. The [id] is a stable, human-readable name — it exists so the HELP panel
 * and a future config file can refer to an action without depending on its type. Implementations
 * live wherever the thing they act on lives: [PanAction]/[ScrollAction] here, everything that names
 * an application object (a panel, a game unit) in the application.
 */
public interface InputAction {
    public val id: String
}

/** One chord bound to one action, credited to the [HintGroup] that documents it in a help panel. */
public data class KeyBinding(
    val chord: KeyChord,
    val action: InputAction,
    val hintGroup: String,
)

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
