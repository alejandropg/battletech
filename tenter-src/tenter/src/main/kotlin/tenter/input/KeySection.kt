package tenter.input

/** A titled group of [hints] — e.g. one "local" section (current context) or a "global" section. */
public data class KeySection(
    val title: String,
    val hints: List<KeyHint>
)
