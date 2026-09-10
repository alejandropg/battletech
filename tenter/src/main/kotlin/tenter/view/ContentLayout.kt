package tenter.view

import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.RevealRect

/**
 * Recorded dimensions, text, and placements for one frame. Dimensions describe logical content,
 * not the number of nonblank pixels painted into a canvas. The instruction list is immutable,
 * but referenced roles and raw views are not deep-copied. In particular, [fixedContent] retains
 * its original [View] and invokes it on each paint. Capture stable application data when preparing
 * a frame; repeated paints are allowed and should not change application state.
 */
public class ContentLayout internal constructor(
    public val width: Int,
    public val height: Int,
    private val instructions: List<Instruction>,
    private val revealPreference: RevealPreference,
) : View {

    init {
        require(width >= 0) { "content width must not be negative: $width" }
        require(height >= 0) { "content height must not be negative: $height" }
    }

    /**
     * The small construction surface for custom prepared composition. Children are already
     * prepared, so placement never measures by painting them a second time.
     */
    public class Builder internal constructor() {
        private val instructions = mutableListOf<Instruction>()

        /** Places [content] at the declared layout coordinate ([x], [y]). */
        public fun place(x: Int, y: Int, content: ContentLayout) {
            instructions += Instruction.Child(x, y, content)
        }

        /** Requests visibility of a layout-local rectangle. Empty rectangles are ignored. */
        public fun reveal(x: Int, y: Int, width: Int, height: Int) {
            require(width >= 0) { "reveal width must not be negative: $width" }
            require(height >= 0) { "reveal height must not be negative: $height" }
            instructions += Instruction.Reveal(RevealRect(x, y, width, height))
        }

        internal fun snapshot(): List<Instruction> = instructions.toList()
    }

    override fun draw(canvas: Canvas) {
        canvas.clearReveal()
        val reveal = paint(canvas)
        reveal?.let { canvas.markReveal(it.x, it.y, it.width, it.height) }
    }

    /** Paints once and returns the final request in this layout's local coordinates. */
    internal fun paint(canvas: Canvas): RevealRect? {
        canvas.clearReveal()
        val requests = mutableListOf<RevealRect>()
        for (instruction in instructions) {
            when (instruction) {
                is Instruction.Text -> canvas.writeString(
                    instruction.x,
                    instruction.y,
                    instruction.text,
                    instruction.style,
                )
                is Instruction.Reveal -> clip(instruction.rect, width, height)?.let(requests::add)
                is Instruction.Child -> {
                    val child = instruction.content
                    if (child.width > 0 && child.height > 0) {
                        val source = Canvas.offscreen(child.width, child.height)
                        val childReveal = child.paint(source)
                        canvas.blit(
                            source,
                            0,
                            0,
                            instruction.x,
                            instruction.y,
                            child.width,
                            child.height,
                        )
                        childReveal?.let {
                            translateAndClip(
                                it,
                                instruction.x,
                                instruction.y,
                                child.width,
                                child.height,
                                width,
                                height,
                            )?.let(requests::add)
                        }
                    }
                }
                is Instruction.Raw -> {
                    if (width > 0 && height > 0) {
                        val source = Canvas.offscreen(width, height)
                        instruction.view.draw(source)
                        canvas.blit(source, 0, 0, 0, 0, width, height)
                        source.revealRect()?.let { clip(it, width, height)?.let(requests::add) }
                    }
                }
            }
        }
        return when (revealPreference) {
            RevealPreference.FIRST -> requests.firstOrNull()
            RevealPreference.LAST -> requests.lastOrNull()
        }
    }

    internal companion object {
        fun raw(width: Int, height: Int, view: View): ContentLayout = ContentLayout(
            width = width,
            height = height,
            instructions = listOf(Instruction.Raw(view)),
            revealPreference = RevealPreference.LAST,
        )
    }

    internal sealed interface Instruction {
        data class Text(
            val x: Int,
            val y: Int,
            val text: String,
            val style: Cell.Style,
        ) : Instruction

        data class Reveal(val rect: RevealRect) : Instruction

        data class Child(val x: Int, val y: Int, val content: ContentLayout) : Instruction

        data class Raw(val view: View) : Instruction
    }

    private fun clip(rect: RevealRect, boundWidth: Int, boundHeight: Int): RevealRect? {
        return translateAndClip(rect, 0, 0, boundWidth, boundHeight, boundWidth, boundHeight)
    }

    private fun translateAndClip(
        rect: RevealRect,
        x: Int,
        y: Int,
        childWidth: Int,
        childHeight: Int,
        boundWidth: Int,
        boundHeight: Int,
    ): RevealRect? {
        val left = maxOf(rect.x.toLong(), 0L)
        val top = maxOf(rect.y.toLong(), 0L)
        val right = minOf(rect.x.toLong() + rect.width.toLong(), childWidth.toLong())
        val bottom = minOf(rect.y.toLong() + rect.height.toLong(), childHeight.toLong())
        if (left >= right || top >= bottom) return null

        val translatedLeft = left + x.toLong()
        val translatedTop = top + y.toLong()
        val translatedRight = right + x.toLong()
        val translatedBottom = bottom + y.toLong()
        val clippedLeft = translatedLeft.coerceIn(0L, boundWidth.toLong())
        val clippedTop = translatedTop.coerceIn(0L, boundHeight.toLong())
        val clippedRight = translatedRight.coerceIn(clippedLeft, boundWidth.toLong())
        val clippedBottom = translatedBottom.coerceIn(clippedTop, boundHeight.toLong())
        if (clippedLeft >= clippedRight || clippedTop >= clippedBottom) return null
        return RevealRect(
            clippedLeft.toInt(),
            clippedTop.toInt(),
            (clippedRight - clippedLeft).toInt(),
            (clippedBottom - clippedTop).toInt(),
        )
    }
}
