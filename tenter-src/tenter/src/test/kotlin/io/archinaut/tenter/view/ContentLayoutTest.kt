package io.archinaut.tenter.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.archinaut.tenter.screen.Canvas
import io.archinaut.tenter.screen.Cell
import io.archinaut.tenter.palette.ChromeRole
import io.archinaut.tenter.screen.RevealRect
import io.archinaut.tenter.screen.ScreenBuffer

internal class ContentLayoutTest {

    @Test
    fun `raw layouts retain the view while captured frame data stays repeatable`() {
        var label = "before"
        val captured = label
        val snapshot = fixedContent(8, 1, object : View {
            public override fun draw(canvas: Canvas) { canvas.writeString(0, 0, captured) }
        }).layout(8)
        val live = fixedContent(8, 1, object : View {
            public override fun draw(canvas: Canvas) { canvas.writeString(0, 0, label) }
        }).layout(8)
        label = "after"

        repeat(2) {
            val buffer = ScreenBuffer(8, 1)
            snapshot.draw(Canvas.of(buffer))
            assertEquals("before", buffer.line(0))
            val liveBuffer = ScreenBuffer(8, 1)
            live.draw(Canvas.of(liveBuffer))
            assertEquals("after", liveBuffer.line(0))
        }
    }

    @Test
    fun `flowing content counts trailing blank rows and blank-only content`() {
        val content = contentView { cursor ->
            cursor.writeLine("line")
            cursor.newLine()
            cursor.newLine()
        }

        val layout = content.layout(12)

        assertEquals(12, layout.width)
        assertEquals(3, layout.height)
        assertEquals(1, contentView { it.newLine() }.layout(12).height)
        assertEquals(
            6,
            contentView { cursor -> cursor.writeAt(column = 0, atRow = 5, text = "overlay") }.layout(12).height,
        )
    }

    @Test
    fun `flowing content prepares a thousand rows without a height ceiling`() {
        val content = contentView { cursor ->
            repeat(1_000) { row -> cursor.writeLine("row$row") }
        }

        val layout = content.layout(20)
        val buffer = ScreenBuffer(layout.width, layout.height)
        layout.draw(Canvas.of(buffer))

        assertEquals(1_000, layout.height)
        assertEquals("row999", buffer.line(999, width = 7))
    }

    @Test
    fun `zero available width remains valid and fixed content keeps its declared width`() {
        val flowing = contentView { cursor -> cursor.writeLine("ignored") }
        val fixed = fixedContent(width = 20, height = 4, view = EmptyView)

        assertEquals(0, flowing.layout(0).width)
        assertEquals(1, flowing.layout(0).height)
        assertEquals(20, fixed.layout(0).width)
        assertEquals(4, fixed.layout(0).height)
    }

    @Test
    fun `prepared text paints immutable recorded values`() {
        var label = "before"
        val content = contentView { cursor -> cursor.writeLine(label) }

        val layout = content.layout(20)
        label = "after"
        val buffer = ScreenBuffer(20, 1)
        layout.draw(Canvas.of(buffer))

        assertEquals("before", buffer.line(0))
    }

    @Test
    fun `builder snapshot is not changed by later builder operations`() {
        lateinit var builder: ContentLayout.Builder
        val layout = contentLayout(8, 1) {
            builder = this
            reveal(0, 0, 1, 1)
        }
        builder.reveal(7, 0, 1, 1)
        val canvas = Canvas.offscreen(8, 1)

        layout.draw(canvas)

        assertEquals(RevealRect(0, 0, 1, 1), canvas.revealRect())
    }

    @Test
    fun `fixed content does not draw its raw view while preparing`() {
        var drawCount = 0
        val raw = object : View {
            override fun draw(canvas: Canvas) {
                drawCount++
                canvas.writeString(0, 0, "raw")
            }
        }
        val content = fixedContent(width = 8, height = 3, view = raw)

        val layout = content.layout(2)

        assertEquals(0, drawCount)
        assertEquals(8, layout.width)
        assertEquals(3, layout.height)

        layout.draw(Canvas.offscreen(8, 3))
        layout.draw(Canvas.offscreen(8, 3))
        assertEquals(2, drawCount)
    }

    @Test
    fun `prepared child is painted once per layout paint and does not append during painting`() {
        var prepareCount = 0
        val child = object : ContentView {
            override fun layout(availableWidth: Int): ContentLayout {
                prepareCount++
                return contentLayout(availableWidth, 1) { reveal(0, 0, 1, 1) }
            }
        }
        val parent = contentView { cursor -> cursor.draw(child) }

        val layout = parent.layout(10)
        assertEquals(1, prepareCount)

        layout.draw(Canvas.offscreen(10, 1))
        layout.draw(Canvas.offscreen(10, 1))

        assertEquals(1, prepareCount)
    }

    @Test
    fun `text cursor draw content uses the prepared logical height`() {
        val canvas = Canvas.offscreen(10, 5)
        val cursor = TextCursor(canvas)
        val child = contentView { content ->
            content.writeLine("A")
            content.newLine()
        }

        val used = cursor.draw(child)

        assertEquals(2, used)
        assertEquals(2, cursor.row)
        assertEquals("A", canvas.get(0, 0).char)
    }

    @Test
    fun `custom layout places child and clips translated reveal to both rectangles`() {
        val child = contentLayout(5, 4) {
            reveal(x = -2, y = 1, width = 6, height = 3)
            place(x = 0, y = 0, content = contentView { it.writeLine("child") }.layout(5))
        }
        val parent = contentLayout(4, 3) {
            place(x = -1, y = 1, content = child)
        }
        val buffer = ScreenBuffer(4, 3)
        val canvas = Canvas.of(buffer)

        parent.draw(canvas)

        assertEquals(RevealRect(0, 2, 3, 1), canvas.revealRect())
        assertEquals("hild", buffer.line(1, width = 4))
    }

    @Test
    fun `last and first preferences resolve prepared and raw requests in declaration order`() {
        val prepared = contentLayout(4, 2) { reveal(0, 0, 4, 1) }
        val raw = fixedContent(
            width = 4,
            height = 2,
            view = object : View {
                override fun draw(canvas: Canvas) {
                    canvas.markReveal(0, 1, 4, 1)
                }
            },
        ).layout(4)

        val last = contentLayout(4, 2, RevealPreference.LAST) {
            place(0, 0, prepared)
            place(0, 0, raw)
        }
        val first = contentLayout(4, 2, RevealPreference.FIRST) {
            place(0, 0, prepared)
            place(0, 0, raw)
        }

        val lastCanvas = Canvas.offscreen(4, 2)
        val firstCanvas = Canvas.offscreen(4, 2)
        last.draw(lastCanvas)
        first.draw(firstCanvas)

        assertEquals(RevealRect(0, 1, 4, 1), lastCanvas.revealRect())
        assertEquals(RevealRect(0, 0, 4, 1), firstCanvas.revealRect())

        val reverseLast = contentLayout(4, 2, RevealPreference.LAST) {
            place(0, 0, raw)
            place(0, 0, prepared)
        }
        val reverseFirst = contentLayout(4, 2, RevealPreference.FIRST) {
            place(0, 0, raw)
            place(0, 0, prepared)
        }
        val reverseLastCanvas = Canvas.offscreen(4, 2)
        val reverseFirstCanvas = Canvas.offscreen(4, 2)
        reverseLast.draw(reverseLastCanvas)
        reverseFirst.draw(reverseFirstCanvas)

        assertEquals(RevealRect(0, 0, 4, 1), reverseLastCanvas.revealRect())
        assertEquals(RevealRect(0, 1, 4, 1), reverseFirstCanvas.revealRect())
    }

    @Test
    fun `nested preference resolves inside the child before parent composition`() {
        val child = contentLayout(4, 3, RevealPreference.FIRST) {
            reveal(0, 0, 4, 1)
            reveal(0, 2, 4, 1)
        }
        val parent = contentLayout(4, 3) { place(0, 0, child) }
        val canvas = Canvas.offscreen(4, 3)

        parent.draw(canvas)

        assertEquals(RevealRect(0, 0, 4, 1), canvas.revealRect())
    }

    @Test
    fun `first preference falls back when the first child has no target`() {
        val noTarget = contentLayout(4, 1) { }
        val target = contentLayout(4, 1) { reveal(0, 0, 4, 1) }
        val parent = contentLayout(4, 1, RevealPreference.FIRST) {
            place(0, 0, noTarget)
            place(0, 0, target)
        }
        val canvas = Canvas.offscreen(4, 1)

        parent.draw(canvas)

        assertEquals(RevealRect(0, 0, 4, 1), canvas.revealRect())
    }

    @Test
    fun `painting a layout with no request clears a stale canvas reveal`() {
        val buffer = ScreenBuffer(4, 2)
        val canvas = Canvas.of(buffer)
        val revealing = contentLayout(4, 2) { reveal(0, 1, 4, 1) }
        val quiet = contentLayout(4, 2) { }

        revealing.draw(canvas)
        assertTrue(canvas.revealRect() != null)
        quiet.draw(canvas)

        assertFalse(canvas.revealRect() != null)
    }

    @Test
    fun `fixed content preserves raw paint order and reveal`() {
        val content = fixedContent(
            width = 5,
            height = 1,
            view = object : View {
                override fun draw(canvas: Canvas) {
                    canvas.set(0, 0, Cell("X", Cell.Style(fg = ChromeRole.SUCCESS)))
                    canvas.markReveal(0, 0, 1, 1)
                }
            },
        )
        val canvas = Canvas.offscreen(5, 1)

        content.draw(canvas)

        assertEquals("X", canvas.get(0, 0).char)
        assertEquals(RevealRect(0, 0, 1, 1), canvas.revealRect())
    }
}
