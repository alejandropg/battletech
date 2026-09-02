package tenter.view

import tenter.input.KeySection
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.text.CellWidth
import tenter.text.TextWrap

/**
 * Content for a HELP panel: a single `KEYS` section (the top-level rule, via
 * [TextCursor.writeHeader]) holding one sub-section per entry in [sections] — each rendered
 * lazygit-style as an indented label followed by its `key  description` rows.
 */
public class HelpView(private val sections: List<KeySection>) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("KEYS")

        // One key column shared across every section (not per-hint) so descriptions line up in a
        // single straight edge down the panel instead of jagging with each key's own length.
        // The description is wrapped on its own — not as part of one combined "key + description"
        // string — because TextWrap collapses any run of whitespace to a single space, which would
        // destroy alignment padding embedded in the text.
        val keyColumnWidth = sections.flatMap { it.hints }.maxOfOrNull { CellWidth.of(it.keys) } ?: 0
        val prefixWidth = keyColumnWidth + 2
        val descWidth = (content.width - prefixWidth).coerceAtLeast(1)

        for ((index, section) in sections.withIndex()) {
            content.writeLine(section.title, SUBSECTION_STYLE)
            for (hint in section.hints) {
                TextWrap.wrap(hint.description, descWidth).forEachIndexed { i, wrapped ->
                    if (i == 0) content.write(0, hint.keys, KEY_STYLE)
                    content.write(prefixWidth, wrapped, DESC_STYLE)
                    content.newLine()
                }
            }
            if (index < sections.size - 1) content.newLine()
        }
    }

    public companion object {
        public const val TITLE: String = "HELP"

        private val SUBSECTION_STYLE = Cell.Style(ChromeRole.ACCENT)
        private val KEY_STYLE = Cell.Style(ChromeRole.INFO)
        private val DESC_STYLE = Cell.Style(ChromeRole.TEXT_PRIMARY)
    }
}
