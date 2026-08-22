package battletech.tui.view.record

import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import tenter.screen.Canvas
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.PipTrack

/**
 * The record sheet's paper-doll diagram, laid out as an actual body silhouette on a fixed
 * 5-column grid (LA | LT | CT | RT | RA): the head centered above the torso row, arms flanking
 * the torso rather than stacked above it, rear facets directly under their torso (never under an
 * arm), and legs centered below. Reused for both the ARMOR DIAGRAM and INTERNAL STRUCTURE DIAGRAM
 * cards — they differ only in which [Location] values are passed in and whether rear facets
 * exist.
 *
 * A pip is filled for *damage taken* (`max - remaining`), matching the pilot-hits and crit-slot
 * convention elsewhere on this sheet: filled = bad. The block caption prints `remaining/max` —
 * only as many pips as this unit's own maximum, never every value a chassis could theoretically
 * have. Pips are packed with no gap between them ([PipTrack]'s `spacing = 0`) so a location's
 * block fits inside its 1/5-width grid column.
 */
internal class LocationDiagram(
    private val title: String,
    private val head: Location,
    private val leftArm: Location,
    private val rightArm: Location,
    private val leftTorso: Location,
    private val rightTorso: Location,
    private val centerTorso: Location,
    private val leftLeg: Location,
    private val rightLeg: Location,
    private val leftTorsoRear: Location? = null,
    private val centerTorsoRear: Location? = null,
    private val rightTorsoRear: Location? = null,
) : View {

    /** One diagram block: [label], armor/structure [remaining] out of [max], [destroyed] styling. */
    internal data class Location(
        val label: String,
        val remaining: Int,
        val max: Int,
        val destroyed: Boolean = false,
    )

    override fun draw(canvas: Canvas) {
        val content = TextCursor(canvas)
        content.writeHeader(title)

        val track = PipTrack(filledCircleIcon(), emptyCircleIcon(), SheetLayout.PIPS_PER_ROW, spacing = 0)
        val slot = canvas.width / GRID_COLUMNS
        fun x(column: Int) = column * slot

        // Head, centered above the torso row (column 2 = CT's column).
        drawRow(content, track, listOf(head to x(2)))
        // Torso row: arms flank the torsos, not stacked above them.
        drawRow(
            content,
            track,
            listOf(leftArm to x(0), leftTorso to x(1), centerTorso to x(2), rightTorso to x(3), rightArm to x(4)),
        )
        // Rear facets sit directly under their own torso only — never under an arm's column.
        val rears = listOfNotNull(
            leftTorsoRear?.let { it to x(1) },
            centerTorsoRear?.let { it to x(2) },
            rightTorsoRear?.let { it to x(3) },
        )
        if (rears.isNotEmpty()) drawRow(content, track, rears)
        // Legs, centered under the torso.
        drawRow(content, track, listOf(leftLeg to x(1), rightLeg to x(3)))
    }

    private fun drawRow(content: TextCursor, track: PipTrack, blocks: List<Pair<Location, Int>>) {
        val startRow = content.row
        var rowsUsed = 1
        for ((block, col) in blocks) {
            val labelStyle = if (block.destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY
            content.writeAt(col, startRow, "${block.label} ${block.remaining}/${block.max}", labelStyle)
            val damage = (block.max - block.remaining).coerceAtLeast(0)
            val pipStyle = if (block.destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY
            val used = track.draw(content, col, startRow + 1, damage, block.max, SheetStyles.DANGER, pipStyle)
            rowsUsed = maxOf(rowsUsed, 1 + used)
        }
        repeat(rowsUsed) { content.newLine() }
    }

    private companion object {
        /** Grid columns: LA, LT, CT, RT, RA — the torso row's width, in slots. */
        const val GRID_COLUMNS = 5
    }
}
