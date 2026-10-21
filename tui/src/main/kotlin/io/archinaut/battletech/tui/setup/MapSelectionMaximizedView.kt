package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tui.view.BoardView
import io.archinaut.tenter.widget.MeasurableWidget
import io.archinaut.tenter.widget.RowWriter
import io.archinaut.tenter.widget.fixedSize

/**
 * The maximized MAP panel: the normal map selector at left and the highlighted map rendered with
 * the same content view used by the in-game tactical board at right.
 */
internal class MapSelectionMaximizedView(
    private val maps: List<String>,
    private val selected: String?,
    private val cursorIndex: Int,
    private val mapFor: (String) -> GameMap?,
) : MeasurableWidget {

    override fun write(writer: RowWriter) {
        val detail = cursorMap()?.let { map ->
            val (boardWidth, boardHeight) = BoardView.contentSize(map)
            BoardView.preview(map).fixedSize(boardWidth, boardHeight)
        }
        writer.draw(
            SplitMaximizedView(
                leftWidth = MapListView.contentWidth(maps),
                left = MapListView(maps, selected, cursorIndex),
                detail = detail,
            ),
        )
    }

    private fun cursorMap(): GameMap? =
        if (maps.isEmpty()) null else mapFor(maps[cursorIndex.coerceIn(0, maps.lastIndex)])

}
