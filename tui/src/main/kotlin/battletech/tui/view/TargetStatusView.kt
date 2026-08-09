package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import battletech.tui.game.PanelId
import battletech.tui.screen.Canvas
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter

public class TargetStatusView(private val unit: ForeignUnit?) : View {

    override fun render(canvas: Canvas) {
        val content = ContentWriter(canvas)

        if (unit == null) {
            content.writeln("No target selected", WHITE_STYLE)
            return
        }

        ForeignUnitPanel.render(content, unit)
    }

    internal companion object {
        internal val KEY: Char = PanelId.TARGET_STATUS.key
        internal const val TITLE: String = "TARGET STATUS"

        private val WHITE_STYLE = Cell.Style(Color.WHITE)
    }
}
