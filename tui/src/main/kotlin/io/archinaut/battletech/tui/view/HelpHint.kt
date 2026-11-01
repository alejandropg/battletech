package io.archinaut.battletech.tui.view

import io.archinaut.tenter.color.StandardRole
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Style
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.widget.Widget

/** The help hint shared by the TUI's chrome views, drawn whole or not at all. */
internal object HelpHint : Widget {
    internal const val LABEL: String = "? : help"
    internal val WIDTH: Int = CellWidth.of(LABEL)

    override fun render(canvas: Canvas) {
        if (canvas.width >= WIDTH) canvas.setString(0, 0, LABEL, STYLE)
    }

    private val STYLE = Style(StandardRole.TEXT_PRIMARY)
}
