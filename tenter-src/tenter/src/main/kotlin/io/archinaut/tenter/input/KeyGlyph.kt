package io.archinaut.tenter.input

/**
 * Single-cell glyphs standing in for special keys in [KeyHint] labels. Each codepoint is
 * confirmed width-1 by [io.archinaut.tenter.text.CellWidth] (none fall in its wide-glyph ranges), which is
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
