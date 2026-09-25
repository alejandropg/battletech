package tenter.input

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
