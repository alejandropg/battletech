package io.archinaut.battletech.tui.setup

import io.archinaut.battletech.tactical.model.GameMap
import io.archinaut.battletech.tactical.model.HexCoordinates
import io.archinaut.battletech.tactical.model.PlayerId
import io.archinaut.battletech.tactical.unit.MechModel
import io.archinaut.battletech.tactical.unit.UnitId
import io.archinaut.battletech.tactical.unit.createUnit
import io.archinaut.battletech.tui.view.record.MechRecordSheetView
import io.archinaut.battletech.tui.view.record.SheetLayout
import io.archinaut.tenter.text.CellWidth
import io.archinaut.tenter.view.ContentLayout
import io.archinaut.tenter.view.ContentView

/** Prepared maximized roster selection with the selected record sheet as its detail child. */
internal class MechSelectionMaximizedView(
    private val variants: List<String>,
    private val counts: (String) -> Int,
    private val cursorIndex: Int,
    private val mechFor: (String) -> MechModel?,
) : ContentView {

    override fun layout(availableWidth: Int): ContentLayout = SplitMaximizedView(
        leftWidth = listWidth(),
        left = UnitListView(variants, counts, cursorIndex),
        detail = MechRecordSheetView(selectedUnit(), GameMap(emptyMap())),
    ).layout(availableWidth)

    private fun selectedUnit(): io.archinaut.battletech.tactical.unit.VisibleUnit? = selectedModel()?.let { model ->
        model.createUnit(
            id = UnitId(model.variant),
            owner = PlayerId.PLAYER_1,
            position = HexCoordinates(0, 0),
        )
    }

    private fun selectedModel(): MechModel? {
        if (variants.isEmpty()) return null
        return mechFor(variants[cursorIndex.coerceIn(0, variants.lastIndex)])
    }

    private fun listWidth(): Int {
        val widestName = variants.maxOfOrNull(CellWidth::of) ?: CellWidth.of("No mechs registered")
        val widestCount = variants.maxOfOrNull { counts(it).toString().length } ?: 0
        return 4 + widestName + if (widestCount > 0) 1 + widestCount else 0
    }
}
