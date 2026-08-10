package battletech.tui.view

import battletech.tui.input.KeySection
import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.CellWidth
import battletech.tui.screen.Color
import battletech.tui.screen.TextWrap

/**
 * Content for the HELP panel: a single `KEYS` section (the top-level rule, via
 * [ContentWriter.writeHeader]) holding one sub-section per entry in [sections] — the active
 * phase's local keys first, then [battletech.tui.input.Keymap.GLOBAL] — each rendered
 * lazygit-style as an indented label followed by its `key  description` rows.
 */
internal class HelpView(private val sections: List<KeySection>) : View {

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

    internal companion object {
        internal const val TITLE: String = "HELP"

        private val SUBSECTION_STYLE = Cell.Style(Color.BRIGHT_YELLOW)
        private val KEY_STYLE = Cell.Style(Color.CYAN)
        private val DESC_STYLE = Cell.Style(Color.WHITE)
    }
}
