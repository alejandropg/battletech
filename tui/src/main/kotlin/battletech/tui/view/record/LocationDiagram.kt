package battletech.tui.view.record

import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.view.TextCursor
import tenter.view.View

/**
 * The record sheet's front-facing paper doll. [Silhouette.ARMOR] draws fixed-width, variable-height
 * armor boxes with each rear-torso track embedded below its front track.
 * [Silhouette.INTERNAL_STRUCTURE] draws the same locations as a narrower skeleton.
 *
 * A filled pip is damage taken (`max - remaining`), matching the pilot-hit and critical-slot
 * convention elsewhere on the sheet. Empty pips are points still available.
 */
internal class LocationDiagram(
    private val title: String,
    private val silhouette: Silhouette,
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

    internal enum class Silhouette {
        ARMOR,
        INTERNAL_STRUCTURE,
    }

    /** One body section: [label], armor/structure [remaining] out of [max], and destruction state. */
    internal data class Location(
        internal val label: String,
        internal val remaining: Int,
        internal val max: Int,
        internal val destroyed: Boolean = false,
    )

    override fun draw(canvas: Canvas) {
        TextCursor(canvas).writeHeader(title)
        when (silhouette) {
            Silhouette.ARMOR -> drawArmor(canvas)
            Silhouette.INTERNAL_STRUCTURE -> drawInternalStructure(canvas)
        }
    }

    private fun drawArmor(canvas: Canvas) {
        val leftRear = requireNotNull(leftTorsoRear) { "armor diagram requires left rear torso armor" }
        val centerRear = requireNotNull(centerTorsoRear) { "armor diagram requires center rear torso armor" }
        val rightRear = requireNotNull(rightTorsoRear) { "armor diagram requires right rear torso armor" }

        drawCentered(
            canvas,
            HEAD_X,
            HEAD_WIDTH + 2,
            y = 1,
            text = caption(head),
            style = locationStyle(head),
        )
        val headBottom = drawSimpleArmorBox(canvas, head, HEAD_X, topY = 2, width = HEAD_WIDTH)
        drawSideLabels(canvas, headBottom - 1)
        val torsoTop = headBottom + 3
        drawTorsoLabels(canvas, torsoTop)

        val leftFrontRows = rowsFor(leftTorso.max, SIDE_TORSO_WIDTH)
        val rightFrontRows = rowsFor(rightTorso.max, SIDE_TORSO_WIDTH)
        val leftRearRows = rowsFor(leftRear.max, REAR_SIDE_TORSO_WIDTH)
        val centerRearRows = rowsFor(centerRear.max, CENTER_TORSO_WIDTH)
        val rightRearRows = rowsFor(rightRear.max, REAR_SIDE_TORSO_WIDTH)
        val centerFrontRows = maxOf(
            rowsFor(centerTorso.max, CENTER_TORSO_WIDTH),
            leftFrontRows,
            rightFrontRows,
        )
        val centerDisplayRearRows = maxOf(centerRearRows, leftRearRows, rightRearRows)
        val leftTorsoBottom = drawArmorTorso(
            canvas,
            front = leftTorso,
            rear = leftRear,
            side = BodySide.LEFT,
            x = LEFT_TORSO_X,
            topY = torsoTop,
            frontRows = leftFrontRows,
            rearRows = leftRearRows,
        )
        val centerTorsoBottom = drawArmorTorso(
            canvas,
            front = centerTorso,
            rear = centerRear,
            side = BodySide.CENTER,
            x = CENTER_TORSO_X,
            topY = torsoTop,
            frontRows = centerFrontRows,
            rearRows = centerDisplayRearRows,
        )
        val rightTorsoBottom = drawArmorTorso(
            canvas,
            front = rightTorso,
            rear = rightRear,
            side = BodySide.RIGHT,
            x = RIGHT_TORSO_X,
            topY = torsoTop,
            frontRows = rightFrontRows,
            rearRows = rightRearRows,
        )

        val armTop = torsoTop + 2
        drawArmorArm(canvas, leftArm, BodySide.LEFT, LEFT_ARM_X, armTop)
        drawArmorArm(canvas, rightArm, BodySide.RIGHT, RIGHT_ARM_X, armTop)
        val torsoBottom = maxOf(leftTorsoBottom, centerTorsoBottom, rightTorsoBottom)
        val legTop = torsoBottom + 2
        val leftLegWidth = legWidth(leftLeg.max)
        val rightLegWidth = legWidth(rightLeg.max)
        val leftLegX = LEFT_LEG_INNER_EDGE_X - leftLegWidth - 1
        drawArmorLeg(canvas, leftLeg, BodySide.LEFT, leftLegX, legTop, leftLegWidth)
        drawArmorLeg(canvas, rightLeg, BodySide.RIGHT, RIGHT_LEG_INNER_EDGE_X, legTop, rightLegWidth)
    }

    private fun drawSideLabels(canvas: Canvas, y: Int) {
        canvas.writeString(
            HEAD_X - HEAD_SIDE_LABEL_GAP - LEFT_SIDE_LABEL.length,
            y,
            LEFT_SIDE_LABEL,
            locationStyle(leftTorso),
        )
        canvas.writeString(
            HEAD_X + HEAD_WIDTH + HEAD_SIDE_LABEL_GAP + 2,
            y,
            RIGHT_SIDE_LABEL,
            locationStyle(rightTorso),
        )
    }

    private fun drawTorsoLabels(canvas: Canvas, torsoTop: Int) {
        drawCentered(
            canvas,
            CENTER_TORSO_X,
            CENTER_TORSO_WIDTH + 2,
            torsoTop - 2,
            "Torso",
            locationStyle(centerTorso),
        )
        drawCentered(
            canvas,
            LEFT_TORSO_X,
            SIDE_TORSO_WIDTH + 2,
            torsoTop - 1,
            value(leftTorso),
            locationStyle(leftTorso),
        )
        drawCentered(
            canvas,
            CENTER_TORSO_X,
            CENTER_TORSO_WIDTH + 2,
            torsoTop - 1,
            value(centerTorso),
            locationStyle(centerTorso),
        )
        drawRightAligned(
            canvas,
            RIGHT_TORSO_X,
            SIDE_TORSO_WIDTH + 2,
            torsoTop - 1,
            value(rightTorso),
            locationStyle(rightTorso),
            rightPadding = 1,
        )
    }

    private fun drawArmorTorso(
        canvas: Canvas,
        front: Location,
        rear: Location,
        side: BodySide,
        x: Int,
        topY: Int,
        frontRows: Int,
        rearRows: Int,
    ): Int {
        val frontWidth = if (side == BodySide.CENTER) CENTER_TORSO_WIDTH else SIDE_TORSO_WIDTH
        val rearWidth = if (side == BodySide.CENTER) CENTER_TORSO_WIDTH else REAR_SIDE_TORSO_WIDTH
        val rearX = when (side) {
            BodySide.LEFT -> x + 2
            BodySide.CENTER, BodySide.RIGHT -> x
        }
        val frontStyle = locationStyle(front)
        val rearStyle = locationStyle(rear)

        drawTop(canvas, x, topY, frontWidth, frontStyle)
        drawPipRows(canvas, front, x, topY + 1, frontWidth, frontRows)
        val transitionY = topY + frontRows + 1
        drawTorsoTransition(canvas, side, x, transitionY, frontWidth, frontStyle)
        val separatorY = transitionY + 1
        drawRearSeparator(canvas, side, rearX, separatorY, rearWidth, rearStyle)
        drawPipRows(canvas, rear, rearX, separatorY + 1, rearWidth, rearRows)
        val bottomY = separatorY + rearRows + 1
        drawBottom(canvas, rearX, bottomY, rearWidth, rearStyle)
        when (side) {
            BodySide.LEFT -> canvas.writeString(rearX + 1, bottomY + 1, value(rear), rearStyle)
            BodySide.CENTER -> drawCentered(canvas, rearX, rearWidth + 2, bottomY + 1, value(rear), rearStyle)
            BodySide.RIGHT -> drawRightAligned(
                canvas,
                rearX,
                rearWidth + 2,
                bottomY + 1,
                value(rear),
                rearStyle,
                rightPadding = 1,
            )
        }
        return bottomY
    }

    private fun drawTorsoTransition(
        canvas: Canvas,
        side: BodySide,
        x: Int,
        y: Int,
        width: Int,
        style: Cell.Style,
    ) {
        when (side) {
            BodySide.LEFT -> {
                canvas.writeString(x + 1, y, "╲", style)
                canvas.writeString(x + width + 1, y, "│", style)
            }
            BodySide.CENTER -> drawEmptyRow(canvas, x, y, width, style)
            BodySide.RIGHT -> {
                canvas.writeString(x, y, "│", style)
                canvas.writeString(x + width, y, "╱", style)
            }
        }
    }

    private fun drawRearSeparator(
        canvas: Canvas,
        side: BodySide,
        x: Int,
        y: Int,
        width: Int,
        style: Cell.Style,
    ) {
        val separator = if (side == BodySide.CENTER) "╌╌REAR╌╌╌" else "╌".repeat(width)
        canvas.writeString(x, y, "│" + separator + "│", style)
    }

    private fun drawArmorArm(canvas: Canvas, location: Location, side: BodySide, x: Int, topY: Int): Int {
        val style = locationStyle(location)
        val rows = rowsFor(location.max, ARM_WIDTH)
        drawTop(canvas, x, topY, ARM_WIDTH, style)
        drawPipRows(canvas, location, x, topY + 1, ARM_WIDTH, rows)
        val transitionY = topY + rows + 1
        val narrowX = when (side) {
            BodySide.LEFT -> {
                canvas.writeString(x, transitionY, "│", style)
                canvas.writeString(x + ARM_WIDTH, transitionY, "╱", style)
                x
            }
            BodySide.RIGHT -> {
                canvas.writeString(x + 1, transitionY, "╲", style)
                canvas.writeString(x + ARM_WIDTH + 1, transitionY, "│", style)
                x + 1
            }
            BodySide.CENTER -> error("an arm must be left or right")
        }
        drawEmptyRow(canvas, narrowX, transitionY + 1, ARM_WIDTH - 1, style)
        val bottomY = transitionY + 2
        drawBottom(canvas, narrowX, bottomY, ARM_WIDTH - 1, style)
        drawLimbCaption(canvas, location, side, x, ARM_WIDTH, topY + maxOf(2, rows / 2), "Arm")
        return bottomY
    }

    private fun drawArmorLeg(
        canvas: Canvas,
        location: Location,
        side: BodySide,
        x: Int,
        topY: Int,
        width: Int,
    ): Int {
        val style = locationStyle(location)
        val rows = rowsFor(location.max, width)
        drawTop(canvas, x, topY, width, style)
        drawPipRows(canvas, location, x, topY + 1, width, rows)
        val transitionY = topY + rows + 1
        val footX = when (side) {
            BodySide.LEFT -> {
                canvas.writeString(x - 1, transitionY, "╱", style)
                canvas.writeString(x + width + 1, transitionY, "│", style)
                x - 2
            }
            BodySide.RIGHT -> {
                canvas.writeString(x, transitionY, "│", style)
                canvas.writeString(x + width + 2, transitionY, "╲", style)
                x
            }
            BodySide.CENTER -> error("a leg must be left or right")
        }
        val bottomY = transitionY + 1
        drawBottom(canvas, footX, bottomY, width + 2, style)
        drawLimbCaption(canvas, location, side, x, width, topY + maxOf(2, rows / 2), "Leg")
        return bottomY
    }

    private fun drawLimbCaption(
        canvas: Canvas,
        location: Location,
        side: BodySide,
        x: Int,
        width: Int,
        y: Int,
        label: String,
    ) {
        val style = locationStyle(location)
        when (side) {
            BodySide.LEFT -> {
                canvas.writeString(x - label.length - 1, y, label, style)
                canvas.writeString(x - value(location).length - 1, y + 1, value(location), style)
            }
            BodySide.RIGHT -> {
                canvas.writeString(x + width + 3, y, label, style)
                canvas.writeString(x + width + 3, y + 1, value(location), style)
            }
            BodySide.CENTER -> error("a limb must be left or right")
        }
    }

    private fun drawSimpleArmorBox(
        canvas: Canvas,
        location: Location,
        x: Int,
        topY: Int,
        width: Int,
    ): Int {
        val style = locationStyle(location)
        val rows = rowsFor(location.max, width)
        drawTop(canvas, x, topY, width, style)
        drawPipRows(canvas, location, x, topY + 1, width, rows)
        val bottomY = topY + rows + 1
        drawBottom(canvas, x, bottomY, width, style)
        return bottomY
    }

    private fun drawPipRows(
        canvas: Canvas,
        location: Location,
        x: Int,
        y: Int,
        width: Int,
        rows: Int,
    ) {
        val outlineStyle = locationStyle(location)
        val damage = (location.max - location.remaining).coerceIn(0, location.max)
        for (row in 0 until rows) {
            drawEmptyRow(canvas, x, y + row, width, outlineStyle)
            for (column in 0 until width) {
                val pip = row * width + column
                if (pip >= location.max) break
                val glyph = if (pip < damage) filledCircleIcon() else emptyCircleIcon()
                val style = when {
                    pip < damage -> SheetStyles.DANGER
                    location.destroyed -> SheetStyles.DESTROYED
                    else -> SheetStyles.TEXT_PRIMARY
                }
                canvas.writeString(x + column + 1, y + row, glyph, style)
            }
        }
    }

    private fun drawTop(canvas: Canvas, x: Int, y: Int, width: Int, style: Cell.Style) {
        canvas.writeString(x, y, "╭" + "─".repeat(width) + "╮", style)
    }

    private fun drawBottom(canvas: Canvas, x: Int, y: Int, width: Int, style: Cell.Style) {
        canvas.writeString(x, y, "╰" + "─".repeat(width) + "╯", style)
    }

    private fun drawEmptyRow(canvas: Canvas, x: Int, y: Int, width: Int, style: Cell.Style) {
        canvas.writeString(x, y, "│", style)
        canvas.writeString(x + width + 1, y, "│", style)
    }

    private fun drawCentered(
        canvas: Canvas,
        x: Int,
        width: Int,
        y: Int,
        text: String,
        style: Cell.Style,
    ) {
        canvas.writeString(x + (width - text.length) / 2, y, text, style)
    }

    private fun drawRightAligned(
        canvas: Canvas,
        x: Int,
        width: Int,
        y: Int,
        text: String,
        style: Cell.Style,
        rightPadding: Int = 0,
    ) {
        canvas.writeString(x + width - text.length - rightPadding, y, text, style)
    }

    private fun rowsFor(capacity: Int, width: Int): Int =
        (capacity.coerceAtLeast(0) + width - 1) / width

    private fun legWidth(max: Int): Int = when {
        max <= 16 -> 3
        max >= 24 -> 5
        else -> 4
    }

    private fun caption(location: Location): String = location.label + " " + value(location)

    private fun value(location: Location): String = location.remaining.toString() + "/" + location.max

    private fun locationStyle(location: Location): Cell.Style =
        if (location.destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY

    private enum class BodySide {
        LEFT,
        CENTER,
        RIGHT,
    }

    private fun drawInternalStructure(canvas: Canvas) {
        drawInternalConnectors(canvas)

        drawPart(canvas, head, INTERNAL_HEAD, x = 38, y = 2)
        drawPart(canvas, leftTorso, INTERNAL_SIDE_TORSO, x = 29, y = 7)
        drawPart(canvas, centerTorso, INTERNAL_CENTER_TORSO, x = 37, y = 7)
        drawPart(canvas, rightTorso, INTERNAL_SIDE_TORSO.mirrored(), x = 45, y = 7)
        drawPart(canvas, leftArm, INTERNAL_ARM, x = 20, y = 9)
        drawPart(canvas, rightArm, INTERNAL_ARM.mirrored(), x = 55, y = 9)
        drawPart(canvas, leftLeg, INTERNAL_LEG, x = 31, y = 16)
        drawPart(canvas, rightLeg, INTERNAL_LEG.mirrored(), x = 44, y = 16)

        drawCaption(canvas, head, x = 34, y = 1)
        drawCaption(canvas, leftTorso, x = 10, y = 6)
        drawCaption(canvas, centerTorso, x = 31, y = 6)
        drawCaption(canvas, rightTorso, x = 53, y = 6)
        drawCaption(canvas, leftArm, x = 0, y = 12)
        drawCaption(canvas, rightArm, x = 64, y = 12)
        drawCaption(canvas, leftLeg, x = 22, y = 24)
        drawCaption(canvas, rightLeg, x = 47, y = 24)
    }

    private fun drawInternalConnectors(canvas: Canvas) {
        drawMuted(canvas, 39, 5, "│ │")
        drawMuted(canvas, 25, 9, "────")
        drawMuted(canvas, 51, 9, "────")
        drawMuted(canvas, 34, 15, "╲ ╱")
        drawMuted(canvas, 42, 15, "╲ ╱")
    }

    private fun drawMuted(canvas: Canvas, x: Int, y: Int, text: String) {
        canvas.writeString(x, y, text, SheetStyles.TEXT_MUTED)
    }

    private fun drawCaption(canvas: Canvas, location: Location, x: Int, y: Int) {
        val style = if (location.destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY
        val caption = location.label + " " + location.remaining + "/" + location.max
        canvas.writeString(x, y, caption, style)
    }

    private fun drawPart(canvas: Canvas, location: Location, shape: Shape, x: Int, y: Int) {
        require(location.max <= shape.capacity) {
            location.label + " capacity " + location.max +
                " exceeds paper-doll mask capacity " + shape.capacity
        }

        val outlineStyle = if (location.destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_MUTED
        drawTopContour(canvas, shape, x, y, outlineStyle)

        val damage = (location.max - location.remaining).coerceIn(0, location.max)
        var pip = 0
        for ((index, row) in shape.rows.withIndex()) {
            val rowY = y + index + 1
            val rowX = x + row.offset
            canvas.writeString(rowX, rowY, leftContour(shape, index), outlineStyle)
            canvas.writeString(rowX + row.count + 1, rowY, rightContour(shape, index), outlineStyle)
            for (column in 0 until row.count) {
                if (pip < location.max) {
                    val glyph = if (pip < damage) filledCircleIcon() else emptyCircleIcon()
                    val style = when {
                        pip < damage -> SheetStyles.DANGER
                        location.destroyed -> SheetStyles.DESTROYED
                        else -> SheetStyles.TEXT_PRIMARY
                    }
                    canvas.writeString(rowX + column + 1, rowY, glyph, style)
                    pip += 1
                }
            }
        }

        drawBottomContour(canvas, shape, x, y + shape.rows.size + 1, outlineStyle)
    }

    private fun drawTopContour(canvas: Canvas, shape: Shape, x: Int, y: Int, style: Cell.Style) {
        val first = shape.rows.first()
        canvas.writeString(x + first.offset, y, "╭" + "─".repeat(first.count) + "╮", style)
    }

    private fun drawBottomContour(canvas: Canvas, shape: Shape, x: Int, y: Int, style: Cell.Style) {
        val last = shape.rows.last()
        canvas.writeString(x + last.offset, y, "╰" + "─".repeat(last.count) + "╯", style)
    }

    private fun leftContour(shape: Shape, index: Int): String {
        if (index == 0) return "│"
        return when {
            shape.rows[index].offset > shape.rows[index - 1].offset -> "╲"
            shape.rows[index].offset < shape.rows[index - 1].offset -> "╱"
            else -> "│"
        }
    }

    private fun rightContour(shape: Shape, index: Int): String {
        if (index == 0) return "│"
        val current = shape.rows[index]
        val previous = shape.rows[index - 1]
        val currentEdge = current.offset + current.count
        val previousEdge = previous.offset + previous.count
        return when {
            currentEdge > previousEdge -> "╲"
            currentEdge < previousEdge -> "╱"
            else -> "│"
        }
    }

    private data class ShapeRow(
        public val offset: Int,
        public val count: Int,
    )

    private data class Shape(
        public val rows: List<ShapeRow>,
    ) {
        public val capacity: Int = rows.sumOf(ShapeRow::count)

        public fun mirrored(): Shape {
            val width = rows.maxOf { it.offset + it.count }
            return Shape(rows.map { ShapeRow(width - it.offset - it.count, it.count) })
        }
    }

    private companion object {
        private const val HEAD_X = 37
        private const val HEAD_WIDTH = 5
        private const val HEAD_SIDE_LABEL_GAP = 10
        private const val LEFT_SIDE_LABEL = "Left"
        private const val RIGHT_SIDE_LABEL = "Right"
        private const val LEFT_TORSO_X = 27
        private const val CENTER_TORSO_X = 35
        private const val RIGHT_TORSO_X = 46
        private const val SIDE_TORSO_WIDTH = 6
        private const val CENTER_TORSO_WIDTH = 9
        private const val REAR_SIDE_TORSO_WIDTH = 4
        private const val LEFT_ARM_X = 20
        private const val RIGHT_ARM_X = 55
        private const val ARM_WIDTH = 4
        private const val LEFT_LEG_INNER_EDGE_X = 37
        private const val RIGHT_LEG_INNER_EDGE_X = 43

        private val INTERNAL_HEAD = shape(3)
        private val INTERNAL_ARM = shiftedShape(0 to 3, 0 to 3, 0 to 3, 0 to 3, 0 to 3, 1 to 2)
        private val INTERNAL_SIDE_TORSO = shiftedShape(0 to 4, 0 to 4, 0 to 4, 1 to 3, 1 to 3, 1 to 3)
        private val INTERNAL_CENTER_TORSO =
            shiftedShape(1 to 4, 0 to 5, 0 to 5, 0 to 5, 1 to 4, 1 to 4, 1 to 4)
        private val INTERNAL_LEG = shiftedShape(0 to 4, 0 to 4, 0 to 4, 1 to 3, 1 to 3, 1 to 3)

        private fun shape(vararg counts: Int): Shape =
            Shape(counts.map { ShapeRow(offset = 0, count = it) })

        private fun shiftedShape(vararg rows: Pair<Int, Int>): Shape =
            Shape(rows.map { ShapeRow(offset = it.first, count = it.second) })
    }
}
