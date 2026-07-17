package battletech.tui.view

import battletech.tactical.unit.ForeignUnit
import battletech.tui.game.PanelId
import battletech.tui.screen.Cell
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer

public class TargetStatusView(private val unit: ForeignUnit?) : View {

    public companion object {
        public val INDEX: Int = PanelId.TARGET_STATUS.index
        public const val TITLE: String = "TARGET STATUS"

        private val WHITE_STYLE = Cell.Style(Color.WHITE)
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        // One blank row for pixel parity with the decorator's y+1 inner-content start
        val content = ContentWriter(buffer, x, y + 1, width)

        if (unit == null) {
            content.writeln("No target selected", WHITE_STYLE)
            return
        }

        ForeignUnitPanel.render(content, unit)
    }
}
