package tenter.view

import tenter.screen.Cell
import tenter.screen.ColorRole
import tenter.screen.UiRole

// Nerd Fonts icons (https://www.nerdfonts.com/cheat-sheet)
private val NF_MD_CHECKBOX_BLANK_OUTLINE = String(Character.toChars(0xF0131))
private val NF_MD_CHECKBOX_MARKED_OUTLINE = String(Character.toChars(0xF0135))
private val NF_MD_MINUS_BOX_OUTLINE = String(Character.toChars(0xF06F2))

/** The single-cell NerdFont glyph for [state] — public so a caller can match it without redrawing. */
public fun checkboxIcon(state: CheckState): String = when (state) {
    CheckState.UNCHECKED -> NF_MD_CHECKBOX_BLANK_OUTLINE
    CheckState.CHECKED -> NF_MD_CHECKBOX_MARKED_OUTLINE
    CheckState.INDETERMINATE -> NF_MD_MINUS_BOX_OUTLINE
}

/** Reusable single-cell NerdFont checkbox glyph. */
public object Checkbox {

    /** Default per-state color when the surrounding row does not override it. */
    public fun intrinsicColor(state: CheckState): ColorRole = when (state) {
        CheckState.CHECKED -> UiRole.SUCCESS
        else -> UiRole.TEXT_MUTED
    }

    /** Overlays the checkbox onto [row] at [column]; occupies exactly one cell. */
    public fun draw(
        content: ContentWriter,
        column: Int,
        row: Int,
        state: CheckState,
        color: ColorRole = intrinsicColor(state),
    ) {
        content.writeAt(column, row, checkboxIcon(state), Cell.Style(color))
    }
}
