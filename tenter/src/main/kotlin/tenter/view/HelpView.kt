package tenter.view

import tenter.input.KeySection
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.CellWidth
import tenter.screen.TextWrap
import tenter.screen.UiRole

/**
 * Content for a HELP panel: a single `KEYS` section (the top-level rule, via
 * [ContentWriter.writeHeader]) holding one sub-section per entry in [sections] — each rendered
 * lazygit-style as an indented label followed by its `key  description` rows.
 */
public class HelpView(private val sections: List<KeySection>) : View {

    override fun render(canvas: Canvas) {
        val content = ContentWriter(canvas)
        content.writeHeader("KEYS")

        for ((index, section) in sections.withIndex()) {
            content.writeln(section.title, SUBSECTION_STYLE)
            for (hint in section.hints) {
                val prefixWidth = CellWidth.of(hint.keys) + 2
                val indent = " ".repeat(prefixWidth)
                TextWrap.wrap(hint.description, content.width - prefixWidth, content.width - prefixWidth).forEachIndexed { i, wrapped ->
                    if (i == 0) {
                        content.writeStr(0, hint.keys, KEY_STYLE)
                        content.writeStr(prefixWidth, wrapped, DESC_STYLE)
                        content.newLine()
                    } else {
                        content.writeln("$indent$wrapped", DESC_STYLE)
                    }
                }
            }
            if (index < sections.size - 1) content.newLine()
        }
    }

    public companion object {
        public const val TITLE: String = "HELP"

        private val SUBSECTION_STYLE = Cell.Style(UiRole.ACCENT)
        private val KEY_STYLE = Cell.Style(UiRole.INFO)
        private val DESC_STYLE = Cell.Style(UiRole.TEXT_PRIMARY)
    }
}
