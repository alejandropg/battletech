package tenter.input

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
