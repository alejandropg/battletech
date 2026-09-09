package tenter.widget

import tenter.screen.Cell
import tenter.screen.ColorRole
import tenter.screen.ChromeRole
import tenter.text.CellWidth
import tenter.view.TextCursor

// Nerd Fonts icons (https://www.nerdfonts.com/cheat-sheet)
private val NF_MD_CHECKBOX_BLANK_OUTLINE = String(Character.toChars(0xF0131))
private val NF_MD_CHECKBOX_MARKED_OUTLINE = String(Character.toChars(0xF0135))
private val NF_MD_MINUS_BOX_OUTLINE = String(Character.toChars(0xF06F2))

/** Immutable glyph choices for the three [CheckState] values. Every glyph occupies one cell. */
public data class CheckboxGlyphs(
    public val unchecked: String,
    public val checked: String,
    public val indeterminate: String,
) {

    init {
        require(CellWidth.isSingleCellGlyph(unchecked)) { "unchecked must be one width-one grapheme" }
        require(CellWidth.isSingleCellGlyph(checked)) { "checked must be one width-one grapheme" }
        require(CellWidth.isSingleCellGlyph(indeterminate)) { "indeterminate must be one width-one grapheme" }
    }

    public companion object {
        /** Plain Unicode glyphs suitable for terminals without a special icon font. */
        public val DEFAULT: CheckboxGlyphs = CheckboxGlyphs("□", "✓", "−")

        /** ASCII glyphs for the most conservative terminal/font combination. */
        public val ASCII: CheckboxGlyphs = CheckboxGlyphs("o", "x", "-")

        /** The existing Material Design Nerd Font glyphs, opt-in for applications that use them. */
        public val NERD_FONT: CheckboxGlyphs = CheckboxGlyphs(
            unchecked = NF_MD_CHECKBOX_BLANK_OUTLINE,
            checked = NF_MD_CHECKBOX_MARKED_OUTLINE,
            indeterminate = NF_MD_MINUS_BOX_OUTLINE,
        )
    }
}

/** The single-cell glyph for [state] using [glyphs]. */
public fun checkboxIcon(
    state: CheckState,
    glyphs: CheckboxGlyphs = CheckboxGlyphs.DEFAULT,
): String = when (state) {
    CheckState.UNCHECKED -> glyphs.unchecked
    CheckState.CHECKED -> glyphs.checked
    CheckState.INDETERMINATE -> glyphs.indeterminate
}

/** Reusable checkbox drawing operations with no global font or theme state. */
public object Checkbox {

    /** Default per-state color when the surrounding row does not override it. */
    public fun intrinsicColor(state: CheckState): ColorRole = when (state) {
        CheckState.CHECKED -> ChromeRole.SUCCESS
        else -> ChromeRole.TEXT_MUTED
    }

    /** Overlays the checkbox onto [row] at [column]; occupies exactly one cell. */
    public fun draw(
        content: TextCursor,
        column: Int,
        row: Int,
        state: CheckState,
        color: ColorRole = intrinsicColor(state),
        glyphs: CheckboxGlyphs = CheckboxGlyphs.DEFAULT,
    ) {
        content.writeAt(column, row, checkboxIcon(state, glyphs), Cell.Style(color))
    }
}
