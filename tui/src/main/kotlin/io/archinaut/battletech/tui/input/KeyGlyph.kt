package io.archinaut.battletech.tui.input

/**
 * Single-cell glyphs standing in for special keys in help-panel labels. Each codepoint is
 * width-1 by [io.archinaut.tenter.text.CellWidth] (none fall in its wide-glyph ranges), which is
 * what keeps a help panel's key column aligned.
 */
internal object KeyGlyph {
    const val ENTER: String = "⏎"
    const val TAB: String = "⇥"
    const val ESC: String = "⎋"
    const val SPACE: String = "␣"
    const val CTRL: String = "⌃"
}
