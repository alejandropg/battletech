package tenter.view

import tenter.input.KeySection
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.ChromeRole
import tenter.screen.styled
import tenter.text.CellWidth

/**
 * Content for a HELP panel: a single `KEYS` section (the top-level rule, via
 * [TextCursor.writeHeader]) holding one sub-section per entry in [sections] — each rendered
 * lazygit-style as an indented label followed by its `key  description` rows.
 */
public class HelpView(private val sections: List<KeySection>) : View {

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader("KEYS")

        for ((index, section) in sections.withIndex()) {
            content.writeLine(section.title, SUBSECTION_STYLE)
            for (hint in section.hints) {
                val prefixWidth = CellWidth.of(hint.keys) + 2
                val indent = " ".repeat(prefixWidth)
                val line = styled {
                    append(hint.keys, KEY_STYLE)
                    append("  ")
                    append(hint.description, DESC_STYLE)
                }
                line.wrap(content.width, content.width - prefixWidth).forEachIndexed { i, wrapped ->
                    content.writeLine(if (i == 0) wrapped else styled { append(indent); append(wrapped) })
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
