package battletech.tui.view.record

import battletech.tactical.model.MechLocation
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.VisibleUnit
import battletech.tui.icon.emptyCircleIcon
import battletech.tui.icon.filledCircleIcon
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.view.TextCursor
import tenter.view.View
import tenter.widget.PipTrack

/**
 * The record sheet's front-facing paper doll. [armor] and [internalStructure] share one
 * configurable robot renderer; armor embeds rear-torso tracks, internal structure never does.
 * That split is [Body]'s two variants, not a runtime check — an internal-structure diagram simply
 * has nowhere to put rear locations, so passing them is a compile error rather than a caught
 * `require()`.
 *
 * A filled pip is damage taken (`max - remaining`), matching the pilot-hit and critical-slot
 * convention elsewhere on the sheet. Empty pips are points still available.
 */
internal class LocationDiagram private constructor(
    private val title: String,
    private val body: Body,
) : View {

    /** One body section: armor/structure [remaining] out of [max], and destruction state. */
    internal data class Location(
        internal val remaining: Int,
        internal val max: Int,
        internal val destroyed: Boolean = false,
    )

    /**
     * The eight always-present locations, plus — only for [FrontAndRear] — the three rear torso
     * tracks the armor diagram embeds. [sizes] is fixed per variant: there is exactly one set of
     * proportions for "structure with rear tracks" (the armor sheet) and one for "structure alone"
     * (the internal structure sheet), so it travels with the variant rather than being threaded in
     * separately by a caller who could get the two out of sync.
     */
    private sealed class Body {
        abstract val sizes: DiagramSizes
        abstract val head: Location
        abstract val leftArm: Location
        abstract val rightArm: Location
        abstract val leftTorso: Location
        abstract val rightTorso: Location
        abstract val centerTorso: Location
        abstract val leftLeg: Location
        abstract val rightLeg: Location

        data class Front(
            override val head: Location,
            override val leftArm: Location,
            override val rightArm: Location,
            override val leftTorso: Location,
            override val rightTorso: Location,
            override val centerTorso: Location,
            override val leftLeg: Location,
            override val rightLeg: Location,
        ) : Body() {
            override val sizes: DiagramSizes get() = INTERNAL_STRUCTURE_SIZES
        }

        data class FrontAndRear(
            override val head: Location,
            override val leftArm: Location,
            override val rightArm: Location,
            override val leftTorso: Location,
            override val rightTorso: Location,
            override val centerTorso: Location,
            override val leftLeg: Location,
            override val rightLeg: Location,
            val leftTorsoRear: Location,
            val centerTorsoRear: Location,
            val rightTorsoRear: Location,
        ) : Body() {
            override val sizes: DiagramSizes get() = ARMOR_SIZES

            /** Fixed width of the rear side-torso boxes — same for every armor diagram. */
            val rearSideTorsoWidth: Int get() = 4
        }
    }

    override fun draw(canvas: Canvas) {
        TextCursor(canvas).writeHeader(title)
        drawRobot(canvas, body.sizes)
    }

    private fun drawRobot(canvas: Canvas, sizes: DiagramSizes) {
        val geometry = geometryFor(sizes, centerX = canvas.width / 2)
        val rear = body as? Body.FrontAndRear

        drawCentered(
            canvas,
            geometry.headX,
            sizes.head + 2,
            y = 1,
            text = "Head " + value(body.head),
            style = locationStyle(body.head),
        )
        val headTop = 2
        val headBottom = drawSimpleBox(canvas, body.head, geometry.headX, topY = headTop, width = sizes.head)
        drawSideLabels(canvas, geometry, sizes, headTop + 1)
        val torsoTop = headBottom + 3
        drawTorsoLabels(canvas, geometry, sizes, torsoTop)

        val leftFrontRows = rowsFor(body.leftTorso.max, sizes.sideTorso)
        val rightFrontRows = rowsFor(body.rightTorso.max, sizes.sideTorso)
        val centerFrontRows = maxOf(
            rowsFor(body.centerTorso.max, sizes.centerTorso),
            leftFrontRows,
            rightFrontRows,
        )
        val leftRear = rear?.let { RearTrack(it.leftTorsoRear, it.rearSideTorsoWidth, rowsFor(it.leftTorsoRear.max, it.rearSideTorsoWidth)) }
        val rightRear = rear?.let { RearTrack(it.rightTorsoRear, it.rearSideTorsoWidth, rowsFor(it.rightTorsoRear.max, it.rearSideTorsoWidth)) }
        val centerRear = rear?.let {
            val centerRows = rowsFor(it.centerTorsoRear.max, sizes.centerTorso)
            RearTrack(
                it.centerTorsoRear,
                sizes.centerTorso,
                maxOf(centerRows, requireNotNull(leftRear).rows, requireNotNull(rightRear).rows),
            )
        }
        val leftTorsoBottom = drawTorso(
            canvas,
            front = body.leftTorso,
            rear = leftRear,
            side = BodySide.LEFT,
            x = geometry.leftTorsoX,
            topY = torsoTop,
            frontWidth = sizes.sideTorso,
            frontRows = leftFrontRows,
        )
        val centerTorsoBottom = drawTorso(
            canvas,
            front = body.centerTorso,
            rear = centerRear,
            side = BodySide.CENTER,
            x = geometry.centerTorsoX,
            topY = torsoTop,
            frontWidth = sizes.centerTorso,
            frontRows = centerFrontRows,
        )
        val rightTorsoBottom = drawTorso(
            canvas,
            front = body.rightTorso,
            rear = rightRear,
            side = BodySide.RIGHT,
            x = geometry.rightTorsoX,
            topY = torsoTop,
            frontWidth = sizes.sideTorso,
            frontRows = rightFrontRows,
        )

        val armTop = torsoTop + 2
        drawArm(canvas, body.leftArm, BodySide.LEFT, geometry.leftArmX, armTop, sizes.arm)
        drawArm(canvas, body.rightArm, BodySide.RIGHT, geometry.rightArmX, armTop, sizes.arm)
        val torsoBottom = maxOf(leftTorsoBottom, centerTorsoBottom, rightTorsoBottom)
        val legTop = torsoBottom + 2
        val leftLegWidth = legWidth(body.leftLeg.max, sizes)
        val rightLegWidth = legWidth(body.rightLeg.max, sizes)
        val leftLegX = geometry.leftLegInnerEdgeX - leftLegWidth - 1
        drawLeg(canvas, body.leftLeg, BodySide.LEFT, leftLegX, legTop, leftLegWidth)
        drawLeg(canvas, body.rightLeg, BodySide.RIGHT, geometry.rightLegInnerEdgeX, legTop, rightLegWidth)
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
            locationStyle(body.leftTorso),
        )
        canvas.writeString(
            geometry.headX + sizes.head + HEAD_SIDE_LABEL_GAP + 2,
            y,
            RIGHT_SIDE_LABEL,
            locationStyle(body.rightTorso),
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
            locationStyle(body.centerTorso),
        )
        canvas.writeString(
            geometry.leftTorsoX + 1,
            torsoTop - 1,
            value(body.leftTorso),
            locationStyle(body.leftTorso),
        )
        drawCentered(
            canvas,
            geometry.centerTorsoX,
            sizes.centerTorso + 2,
            torsoTop - 1,
            value(body.centerTorso),
            locationStyle(body.centerTorso),
        )
        drawRightAligned(
            canvas,
            geometry.rightTorsoX,
            sizes.sideTorso + 2,
            torsoTop - 1,
            value(body.rightTorso),
            locationStyle(body.rightTorso),
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
        for (row in 0 until rows) drawEmptyRow(canvas, x, y + row, width, outlineStyle)

        val damage = (location.max - location.remaining).coerceIn(0, location.max)
        PipTrack(filledCircleIcon(), emptyCircleIcon(), perRow = width, spacing = 0).draw(
            TextCursor(canvas),
            column = x + 1,
            row = y,
            used = damage,
            capacity = location.max,
            usedStyle = SheetStyles.DANGER,
            emptyStyle = outlineStyle,
        )
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

    private fun value(location: Location): String = location.remaining.toString() + "/" + location.max

    private fun locationStyle(location: Location): Cell.Style =
        if (location.destroyed) SheetStyles.DESTROYED else SheetStyles.TEXT_PRIMARY

    private enum class BodySide {
        LEFT,
        CENTER,
        RIGHT,
    }

    private data class DiagramSizes(
        val head: Int,
        val sideTorso: Int,
        val centerTorso: Int,
        val arm: Int,
        val fixedLeg: Int?,
    )

    private data class DiagramGeometry(
        val headX: Int,
        val leftTorsoX: Int,
        val centerTorsoX: Int,
        val rightTorsoX: Int,
        val leftArmX: Int,
        val rightArmX: Int,
        val leftLegInnerEdgeX: Int,
        val rightLegInnerEdgeX: Int,
    )

    private data class RearTrack(
        val location: Location,
        val width: Int,
        val rows: Int,
    )

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

    internal companion object {
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
            arm = 4,
            fixedLeg = null,
        )
        private val INTERNAL_STRUCTURE_SIZES = DiagramSizes(
            head = 3,
            sideTorso = 4,
            centerTorso = 5,
            arm = 3,
            fixedLeg = 4,
        )

        /** The ARMOR DIAGRAM card. Works for any [VisibleUnit] — armor and its maximum are public. */
        internal fun armor(unit: VisibleUnit, destroyed: (MechLocation) -> Boolean): LocationDiagram {
            val armor = unit.armor
            val max = unit.maxArmor
            fun at(remaining: Int, atMax: Int, location: MechLocation) = Location(remaining, atMax, destroyed(location))
            return LocationDiagram(
                title = "ARMOR DIAGRAM",
                body = Body.FrontAndRear(
                    head = at(armor.head, max.head, MechLocation.HEAD),
                    leftArm = at(armor.leftArm, max.leftArm, MechLocation.LEFT_ARM),
                    rightArm = at(armor.rightArm, max.rightArm, MechLocation.RIGHT_ARM),
                    leftTorso = at(armor.leftTorso, max.leftTorso, MechLocation.LEFT_TORSO),
                    rightTorso = at(armor.rightTorso, max.rightTorso, MechLocation.RIGHT_TORSO),
                    centerTorso = at(armor.centerTorso, max.centerTorso, MechLocation.CENTER_TORSO),
                    leftLeg = at(armor.leftLeg, max.leftLeg, MechLocation.LEFT_LEG),
                    rightLeg = at(armor.rightLeg, max.rightLeg, MechLocation.RIGHT_LEG),
                    leftTorsoRear = at(armor.leftTorsoRear, max.leftTorsoRear, MechLocation.LEFT_TORSO),
                    centerTorsoRear = at(armor.centerTorsoRear, max.centerTorsoRear, MechLocation.CENTER_TORSO),
                    rightTorsoRear = at(armor.rightTorsoRear, max.rightTorsoRear, MechLocation.RIGHT_TORSO),
                ),
            )
        }

        /**
         * The INTERNAL STRUCTURE DIAGRAM card. Owner-only: [CombatUnit.internalStructure] and its
         * maximum are private record-sheet data, never exposed on the public [VisibleUnit] surface.
         */
        internal fun internalStructure(unit: CombatUnit): LocationDiagram {
            val current = unit.internalStructure
            val max = unit.maxInternalStructure
            fun at(remaining: Int, atMax: Int, location: MechLocation) = Location(remaining, atMax, !current.isIntact(location))
            return LocationDiagram(
                title = "INTERNAL STRUCTURE DIAGRAM",
                body = Body.Front(
                    head = at(current.head, max.head, MechLocation.HEAD),
                    leftArm = at(current.leftArm, max.leftArm, MechLocation.LEFT_ARM),
                    rightArm = at(current.rightArm, max.rightArm, MechLocation.RIGHT_ARM),
                    leftTorso = at(current.leftTorso, max.leftTorso, MechLocation.LEFT_TORSO),
                    rightTorso = at(current.rightTorso, max.rightTorso, MechLocation.RIGHT_TORSO),
                    centerTorso = at(current.centerTorso, max.centerTorso, MechLocation.CENTER_TORSO),
                    leftLeg = at(current.leftLeg, max.leftLeg, MechLocation.LEFT_LEG),
                    rightLeg = at(current.rightLeg, max.rightLeg, MechLocation.RIGHT_LEG),
                ),
            )
        }
    }
}
