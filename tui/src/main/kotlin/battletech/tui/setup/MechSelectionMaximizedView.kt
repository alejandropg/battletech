package battletech.tui.setup

import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.MechModel
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.createUnit
import battletech.tui.view.record.MechRecordSheetView
import battletech.tui.view.record.SheetLayout
import tenter.screen.Canvas
import tenter.text.CellWidth
import tenter.view.ContentExtent
import tenter.view.View

/**
 * The maximized PLAYER panel: the same selectable roster at left and the selected model's full
 * record sheet at right. The split is one content stream, so [tenter.panel.Panel]'s existing
 * viewport scrolls the list, divider, and record sheet together.
 *
 * Setup has [MechModel]s rather than deployed [battletech.tactical.unit.CombatUnit]s. The record
 * sheet is intentionally reused through a neutral preview unit with pristine state; this keeps
 * the setup preview and the maximized UNIT STATUS sheet on the same rendering seam.
 */
internal class MechSelectionMaximizedView(
    private val variants: List<String>,
    private val counts: (String) -> Int,
    private val cursorIndex: Int,
    private val mechFor: (String) -> MechModel?,
) : View {

    internal val contentExtent: ContentExtent
        get() = ContentExtent.Fixed(
            width = SplitMaximizedView.totalWidth(listWidth(), SheetLayout.SHEET_WIDTH),
            height = maxOf(
                variants.size.coerceAtLeast(1),
                SplitMaximizedView.contentHeight(detailView(), SheetLayout.SHEET_WIDTH),
            ),
        )

    override fun draw(canvas: Canvas) {
        SplitMaximizedView(
            leftWidth = listWidth(),
            left = UnitListView(variants, counts, cursorIndex),
            detail = detailView(),
        ).draw(canvas)
    }

    private fun detailView(): View = selectedModel()?.let { model ->
        MechRecordSheetView(previewUnit(model), GameMap(emptyMap()))
    } ?: MechRecordSheetView(null, GameMap(emptyMap()))

    private fun selectedModel(): MechModel? {
        if (variants.isEmpty()) return null
        return mechFor(variants[cursorIndex.coerceIn(0, variants.lastIndex)])
    }

    private fun listWidth(): Int {
        val widestName = variants.maxOfOrNull(CellWidth::of) ?: CellWidth.of("No mechs registered")
        val widestCount = variants.maxOfOrNull { counts(it).toString().length } ?: 0
        return 4 + widestName + if (widestCount > 0) 1 + widestCount else 0
    }

    private fun previewUnit(model: MechModel) = model.createUnit(
        id = UnitId(model.variant),
        owner = PlayerId.PLAYER_1,
        position = HexCoordinates(0, 0),
    )
}
