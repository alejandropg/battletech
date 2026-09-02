package battletech.tui.setup

import battletech.tactical.model.GameMap
import battletech.tui.view.BoardView
import tenter.screen.Canvas
import tenter.view.ContentExtent
import tenter.view.View

/**
 * The maximized MAP panel: the normal map selector at left and the highlighted map rendered with
 * the same content view used by the in-game tactical board at right.
 */
internal class MapSelectionMaximizedView(
    private val maps: List<String>,
    private val selected: String?,
    private val cursorIndex: Int,
    private val mapFor: (String) -> GameMap?,
) : View {

    internal val contentExtent: ContentExtent
        get() = cursorMap()?.let { map ->
            val (boardWidth, boardHeight) = BoardView.contentSize(map)
            ContentExtent.Fixed(
                width = SplitMaximizedView.totalWidth(MapListView.contentWidth(maps), boardWidth),
                height = maxOf(maps.size, boardHeight),
            )
        } ?: ContentExtent.Measured()

    override fun draw(canvas: Canvas) {
        val cursorMap = cursorMap()
        SplitMaximizedView(
            leftWidth = MapListView.contentWidth(maps),
            left = MapListView(maps, selected, cursorIndex),
            detail = cursorMap?.let(BoardView::preview) ?: View.None,
        ).draw(canvas)
    }

    private fun cursorMap(): GameMap? =
        if (maps.isEmpty()) null else mapFor(maps[cursorIndex.coerceIn(0, maps.lastIndex)])

}
