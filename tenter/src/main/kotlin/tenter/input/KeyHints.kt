package tenter.input

/** One key (or chord) and what it does, as shown in a help panel. */
public data class KeyHint(val keys: String, val description: String)

/** A titled group of [hints] — e.g. one "local" section (current context) or a "global" section. */
public data class KeySection(val title: String, val hints: List<KeyHint>)

/**
 * Single-cell glyphs standing in for special keys in [KeyHint] labels. Each codepoint is
 * confirmed width-1 by [tenter.screen.CellWidth] (none fall in its wide-glyph ranges), which is
 * what keeps a help panel's key column aligned.
 */
public object KeyGlyph {
    public const val ENTER: String = "⏎"
    public const val TAB: String = "⇥"
    public const val ESC: String = "⎋"
    public const val SPACE: String = "␣"
    public const val CTRL: String = "⌃"
    public const val ALT: String = "⌥"
}
