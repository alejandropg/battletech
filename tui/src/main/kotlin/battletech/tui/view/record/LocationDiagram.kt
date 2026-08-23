package battletech.tui.view.record

import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.view.TextCursor
import tenter.view.View

/**
 * The record sheet's front-facing paper doll. Armor and internal structure share one configurable
 * robot renderer; armor embeds rear-torso tracks while internal structure omits them.
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
        drawRobot(canvas, sizesFor(silhouette))
    }

    private fun drawRobot(canvas: Canvas, sizes: DiagramSizes) {
        val geometry = geometryFor(sizes, centerX = canvas.width / 2)
        val rearLocations = rearLocationsFor(sizes)

        drawCentered(
            canvas,
            geometry.headX,
            sizes.head + 2,
            y = 1,
            text = caption(head),
            style = locationStyle(head),
        )
        val headBottom = drawSimpleBox(canvas, head, geometry.headX, topY = 2, width = sizes.head)
        drawSideLabels(canvas, geometry, sizes, headBottom - 1)
        val torsoTop = headBottom + 3
        drawTorsoLabels(canvas, geometry, sizes, torsoTop)

        val leftFrontRows = rowsFor(leftTorso.max, sizes.sideTorso)
        val rightFrontRows = rowsFor(rightTorso.max, sizes.sideTorso)
        val centerFrontRows = maxOf(
            rowsFor(centerTorso.max, sizes.centerTorso),
            leftFrontRows,
            rightFrontRows,
        )
        val leftRear = rearLocations?.let {
            val width = requireNotNull(sizes.rearSideTorso)
            RearTrack(it.left, width, rowsFor(it.left.max, width))
        }
        val rightRear = rearLocations?.let {
            val width = requireNotNull(sizes.rearSideTorso)
            RearTrack(it.right, width, rowsFor(it.right.max, width))
        }
        val centerRear = rearLocations?.let {
            val centerRows = rowsFor(it.center.max, sizes.centerTorso)
            RearTrack(
                it.center,
                sizes.centerTorso,
                maxOf(centerRows, requireNotNull(leftRear).rows, requireNotNull(rightRear).rows),
            )
        }
        val leftTorsoBottom = drawTorso(
            canvas,
            front = leftTorso,
            rear = leftRear,
            side = BodySide.LEFT,
            x = geometry.leftTorsoX,
            topY = torsoTop,
            frontWidth = sizes.sideTorso,
            frontRows = leftFrontRows,
        )
        val centerTorsoBottom = drawTorso(
            canvas,
            front = centerTorso,
            rear = centerRear,
            side = BodySide.CENTER,
            x = geometry.centerTorsoX,
            topY = torsoTop,
            frontWidth = sizes.centerTorso,
            frontRows = centerFrontRows,
        )
        val rightTorsoBottom = drawTorso(
            canvas,
            front = rightTorso,
            rear = rightRear,
            side = BodySide.RIGHT,
            x = geometry.rightTorsoX,
            topY = torsoTop,
            frontWidth = sizes.sideTorso,
            frontRows = rightFrontRows,
        )

        val armTop = torsoTop + 2
        drawArm(canvas, leftArm, BodySide.LEFT, geometry.leftArmX, armTop, sizes.arm)
        drawArm(canvas, rightArm, BodySide.RIGHT, geometry.rightArmX, armTop, sizes.arm)
        val torsoBottom = maxOf(leftTorsoBottom, centerTorsoBottom, rightTorsoBottom)
        val legTop = torsoBottom + 2
        val leftLegWidth = legWidth(leftLeg.max, sizes)
        val rightLegWidth = legWidth(rightLeg.max, sizes)
        val leftLegX = geometry.leftLegInnerEdgeX - leftLegWidth - 1
        drawLeg(canvas, leftLeg, BodySide.LEFT, leftLegX, legTop, leftLegWidth)
        drawLeg(canvas, rightLeg, BodySide.RIGHT, geometry.rightLegInnerEdgeX, legTop, rightLegWidth)
    }

    private fun drawSideLabels(
        canvas: Canvas,
        geometry: DiagramGeometry,
        sizes: DiagramSizes,
        y: Int,
    ) {
        canvas.writeString(
            geometry.headX - HEAD_SIDE_LABEL_GAP - LEFT_SIDE_LABEL.length,
            y,
            LEFT_SIDE_LABEL,
            locationStyle(leftTorso),
        )
        canvas.writeString(
            geometry.headX + sizes.head + HEAD_SIDE_LABEL_GAP + 2,
            y,
            RIGHT_SIDE_LABEL,
            locationStyle(rightTorso),
        )
    }

    private fun drawTorsoLabels(
        canvas: Canvas,
        geometry: DiagramGeometry,
        sizes: DiagramSizes,
        torsoTop: Int,
    ) {
        drawCentered(
            canvas,
            geometry.centerTorsoX,
            sizes.centerTorso + 2,
            torsoTop - 2,
            "Torso",
            locationStyle(centerTorso),
        )
        drawCentered(
            canvas,
            geometry.leftTorsoX,
            sizes.sideTorso + 2,
            torsoTop - 1,
            value(leftTorso),
            locationStyle(leftTorso),
        )
        drawCentered(
            canvas,
            geometry.centerTorsoX,
            sizes.centerTorso + 2,
            torsoTop - 1,
            value(centerTorso),
            locationStyle(centerTorso),
        )
        drawRightAligned(
            canvas,
            geometry.rightTorsoX,
            sizes.sideTorso + 2,
            torsoTop - 1,
            value(rightTorso),
            locationStyle(rightTorso),
            rightPadding = 1,
        )
    }

    private fun drawTorso(
        canvas: Canvas,
        front: Location,
        rear: RearTrack?,
        side: BodySide,
        x: Int,
        topY: Int,
        frontWidth: Int,
        frontRows: Int,
    ): Int {
        val frontStyle = locationStyle(front)
        drawTop(canvas, x, topY, frontWidth, frontStyle)
        drawPipRows(canvas, front, x, topY + 1, frontWidth, frontRows)

        if (rear == null) {
            val bottomY = topY + frontRows + 1
            drawBottom(canvas, x, bottomY, frontWidth, frontStyle)
            return bottomY
        }

        val rearWidth = rear.width
        val rearX = when (side) {
            BodySide.LEFT -> x + frontWidth - rearWidth
            BodySide.CENTER, BodySide.RIGHT -> x
        }
        val rearStyle = locationStyle(rear.location)

        val transitionY = topY + frontRows + 1
        drawTorsoTransition(canvas, side, x, transitionY, frontWidth, frontStyle)
        val separatorY = transitionY + 1
        drawRearSeparator(canvas, side, rearX, separatorY, rearWidth, rearStyle)
        drawPipRows(canvas, rear.location, rearX, separatorY + 1, rearWidth, rear.rows)
        val bottomY = separatorY + rear.rows + 1
        drawBottom(canvas, rearX, bottomY, rearWidth, rearStyle)
        when (side) {
            BodySide.LEFT -> canvas.writeString(rearX + 1, bottomY + 1, value(rear.location), rearStyle)
            BodySide.CENTER ->
                drawCentered(canvas, rearX, rearWidth + 2, bottomY + 1, value(rear.location), rearStyle)
            BodySide.RIGHT -> drawRightAligned(
                canvas,
                rearX,
                rearWidth + 2,
                bottomY + 1,
                value(rear.location),
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
        val separator = if (side == BodySide.CENTER) centeredRearSeparator(width) else "╌".repeat(width)
        canvas.writeString(x, y, "│" + separator + "│", style)
    }

    private fun centeredRearSeparator(width: Int): String {
        val fill = width - REAR_LABEL.length
        require(fill >= 0) { "center rear torso width must fit $REAR_LABEL" }
        val leftFill = fill / 2
        return "╌".repeat(leftFill) + REAR_LABEL + "╌".repeat(fill - leftFill)
    }

    private fun drawArm(
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
        val narrowX = when (side) {
            BodySide.LEFT -> {
                canvas.writeString(x, transitionY, "│", style)
                canvas.writeString(x + width, transitionY, "╱", style)
                x
            }
            BodySide.RIGHT -> {
                canvas.writeString(x + 1, transitionY, "╲", style)
                canvas.writeString(x + width + 1, transitionY, "│", style)
                x + 1
            }
            BodySide.CENTER -> error("an arm must be left or right")
        }
        drawEmptyRow(canvas, narrowX, transitionY + 1, width - 1, style)
        val bottomY = transitionY + 2
        drawBottom(canvas, narrowX, bottomY, width - 1, style)
        drawLimbCaption(canvas, location, side, x, width, topY + maxOf(2, rows / 2), "Arm")
        return bottomY
    }

    private fun drawLeg(
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

    private fun drawSimpleBox(
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

    private fun legWidth(max: Int, sizes: DiagramSizes): Int =
        sizes.fixedLeg ?: when {
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

    private data class DiagramSizes(
        public val head: Int,
        public val sideTorso: Int,
        public val centerTorso: Int,
        public val rearSideTorso: Int?,
        public val arm: Int,
        public val fixedLeg: Int?,
    )

    private data class DiagramGeometry(
        public val headX: Int,
        public val leftTorsoX: Int,
        public val centerTorsoX: Int,
        public val rightTorsoX: Int,
        public val leftArmX: Int,
        public val rightArmX: Int,
        public val leftLegInnerEdgeX: Int,
        public val rightLegInnerEdgeX: Int,
    )

    private data class RearLocations(
        public val left: Location,
        public val center: Location,
        public val right: Location,
    )

    private data class RearTrack(
        public val location: Location,
        public val width: Int,
        public val rows: Int,
    )

    private fun sizesFor(silhouette: Silhouette): DiagramSizes = when (silhouette) {
        Silhouette.ARMOR -> ARMOR_SIZES
        Silhouette.INTERNAL_STRUCTURE -> INTERNAL_STRUCTURE_SIZES
    }

    private fun geometryFor(sizes: DiagramSizes, centerX: Int): DiagramGeometry {
        val centerTorsoX = centerX - (sizes.centerTorso + 2) / 2
        val leftTorsoX = centerTorsoX - sizes.sideTorso - 2
        val rightTorsoX = centerTorsoX + sizes.centerTorso + 2
        val headX = centerX - (sizes.head + 2) / 2
        val leftArmX = leftTorsoX - ARM_TORSO_GAP - sizes.arm - 2
        val rightArmX = rightTorsoX + sizes.sideTorso + 2 + ARM_TORSO_GAP
        val leftLegInnerEdgeX = centerX - LEG_INNER_GAP / 2 - 1
        val rightLegInnerEdgeX = centerX + LEG_INNER_GAP / 2 + 1
        return DiagramGeometry(
            headX = headX,
            leftTorsoX = leftTorsoX,
            centerTorsoX = centerTorsoX,
            rightTorsoX = rightTorsoX,
            leftArmX = leftArmX,
            rightArmX = rightArmX,
            leftLegInnerEdgeX = leftLegInnerEdgeX,
            rightLegInnerEdgeX = rightLegInnerEdgeX,
        )
    }

    private fun rearLocationsFor(sizes: DiagramSizes): RearLocations? {
        if (sizes.rearSideTorso == null) {
            require(leftTorsoRear == null && centerTorsoRear == null && rightTorsoRear == null) {
                "internal structure diagram cannot contain rear torso locations"
            }
            return null
        }

        return RearLocations(
            left = requireNotNull(leftTorsoRear) { "armor diagram requires left rear torso armor" },
            center = requireNotNull(centerTorsoRear) { "armor diagram requires center rear torso armor" },
            right = requireNotNull(rightTorsoRear) { "armor diagram requires right rear torso armor" },
        )
    }

    private companion object {
        private const val HEAD_SIDE_LABEL_GAP = 10
        private const val LEFT_SIDE_LABEL = "Left"
        private const val RIGHT_SIDE_LABEL = "Right"
        private const val ARM_TORSO_GAP = 1
        private const val LEG_INNER_GAP = 5
        private const val REAR_LABEL = "REAR"

        private val ARMOR_SIZES = DiagramSizes(
            head = 5,
            sideTorso = 6,
            centerTorso = 9,
            rearSideTorso = 4,
            arm = 4,
            fixedLeg = null,
        )
        private val INTERNAL_STRUCTURE_SIZES = DiagramSizes(
            head = 3,
            sideTorso = 4,
            centerTorso = 5,
            rearSideTorso = null,
            arm = 3,
            fixedLeg = 4,
        )
    }
}
