package tenter.animation

import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.view.View
import tenter.view.render

internal class AnimationPlaybackTest {

    private class LetterView(private val letter: Char) : View {
        public override fun draw(canvas: Canvas) {
            canvas.set(0, 0, Cell(letter.toString()))
        }
    }

    private class TestAnimation(
        public override var frameCount: Int,
        public override var frameDuration: Duration,
        private val calls: MutableList<Int> = mutableListOf(),
        private val firstLetter: Char = 'A',
    ) : Animation {
        public override val size: AnimationSize = AnimationSize(width = 1, height = 1)

        public override fun frame(index: Int): View {
            calls += index
            return LetterView((firstLetter.code + index).toChar())
        }
    }

    private class NumericView(private val value: Int) : View {
        public override fun draw(canvas: Canvas) {
            canvas.writeString(0, 0, value.toString())
        }
    }

    private class NumericAnimation(
        public override val frameCount: Int,
        public override val frameDuration: Duration,
        private val calls: MutableList<Int>,
    ) : Animation {
        public override val size: AnimationSize = AnimationSize(width = 12, height = 1)

        public override fun frame(index: Int): View {
            calls += index
            return NumericView(index)
        }
    }

    private fun AnimationPlayback.Frame<String>.letter(): Char =
        render(content, size.width, size.height).get(0, 0).char.single()

    @Test
    fun `immediate playback starts with frame zero`() {
        val animation = TestAnimation(frameCount = 3, frameDuration = 10.milliseconds)
        val playback = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "first")))

        val sample = playback.sample(ZERO)

        assertEquals(listOf("first"), sample.frames.map { it.value })
        assertEquals('A', sample.frames.single().letter())
        assertEquals(10.milliseconds, sample.nextChangeIn)
    }

    @Test
    fun `delayed clip remains absent before and appears at its exact start`() {
        val animation = TestAnimation(frameCount = 1, frameDuration = 10.milliseconds)
        val playback = AnimationPlayback(
            listOf(AnimationPlayback.Clip(animation, "delayed", startAfter = 1_000.milliseconds)),
        )

        assertTrue(playback.sample(999.milliseconds).frames.isEmpty())
        assertEquals(1.milliseconds, playback.sample(999.milliseconds).nextChangeIn)
        assertEquals(listOf('A'), playback.sample(1_000.milliseconds).frames.map { it.letter() })
        assertEquals(10.milliseconds, playback.sample(1_000.milliseconds).nextChangeIn)
    }

    @Test
    fun `frame boundaries select the containing interval`() {
        val animation = TestAnimation(frameCount = 3, frameDuration = 10.milliseconds)
        val playback = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "value")))

        assertEquals('A', playback.sample(9.milliseconds).frames.single().letter())
        assertEquals('B', playback.sample(10.milliseconds).frames.single().letter())
        assertEquals('B', playback.sample(10_001_000.nanoseconds).frames.single().letter())

        val nanosecondAnimation = TestAnimation(frameCount = 3, frameDuration = 7.nanoseconds)
        val nanosecondPlayback = AnimationPlayback(
            listOf(AnimationPlayback.Clip(nanosecondAnimation, "nanoseconds")),
        )
        assertEquals('A', nanosecondPlayback.sample(6.nanoseconds).frames.single().letter())
        assertEquals('B', nanosecondPlayback.sample(7.nanoseconds).frames.single().letter())
        assertEquals('B', nanosecondPlayback.sample(8.nanoseconds).frames.single().letter())
    }

    @Test
    fun `last frame remains visible through its full interval and completion removes it`() {
        val animation = TestAnimation(frameCount = 2, frameDuration = 10.milliseconds)
        val playback = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "value")))

        assertEquals('B', playback.sample(19.milliseconds).frames.single().letter())
        assertEquals(1.milliseconds, playback.sample(19.milliseconds).nextChangeIn)
        val completed = playback.sample(20.milliseconds)
        assertTrue(completed.frames.isEmpty())
        assertNull(completed.nextChangeIn)
    }

    @Test
    fun `different durations and starts advance independently`() {
        val first = TestAnimation(frameCount = 4, frameDuration = 10.milliseconds, firstLetter = 'A')
        val second = TestAnimation(frameCount = 3, frameDuration = 5.milliseconds, firstLetter = 'K')
        val playback = AnimationPlayback(
            listOf(
                AnimationPlayback.Clip(first, "first"),
                AnimationPlayback.Clip(second, "second", startAfter = 20.milliseconds),
            ),
        )

        val atTwentyNine = playback.sample(29.milliseconds)
        assertEquals(listOf("first", "second"), atTwentyNine.frames.map { it.value })
        assertEquals(listOf('C', 'L'), atTwentyNine.frames.map { it.letter() })
        assertEquals(1.milliseconds, atTwentyNine.nextChangeIn)

        val atThirtyFive = playback.sample(35.milliseconds)
        assertEquals(listOf("first"), atThirtyFive.frames.map { it.value })
        assertEquals(listOf('D'), atThirtyFive.frames.map { it.letter() })
        assertEquals(5.milliseconds, atThirtyFive.nextChangeIn)

        val complete = playback.sample(40.milliseconds)
        assertTrue(complete.frames.isEmpty())
        assertNull(complete.nextChangeIn)
    }

    @Test
    fun `visible frame order stays in original clip order after earlier clips finish`() {
        val first = TestAnimation(frameCount = 1, frameDuration = 5.milliseconds)
        val second = TestAnimation(frameCount = 5, frameDuration = 10.milliseconds, firstLetter = 'K')
        val third = TestAnimation(frameCount = 5, frameDuration = 10.milliseconds, firstLetter = 'U')
        val playback = AnimationPlayback(
            listOf(
                AnimationPlayback.Clip(first, "first"),
                AnimationPlayback.Clip(second, "second", startAfter = 1.milliseconds),
                AnimationPlayback.Clip(third, "third", startAfter = 1.milliseconds),
            ),
        )

        assertEquals(listOf("second", "third"), playback.sample(6.milliseconds).frames.map { it.value })
        assertEquals(listOf("second", "third"), playback.sample(11.milliseconds).frames.map { it.value })
        assertEquals(listOf('K', 'U'), playback.sample(6.milliseconds).frames.map { it.letter() })
        assertEquals(listOf('L', 'V'), playback.sample(11.milliseconds).frames.map { it.letter() })
    }

    @Test
    fun `a gap reports the next pending start and creates no frame`() {
        val animation = TestAnimation(frameCount = 1, frameDuration = 10.milliseconds)
        val playback = AnimationPlayback(
            listOf(AnimationPlayback.Clip(animation, "value", startAfter = 100.milliseconds)),
        )

        val sample = playback.sample(50.milliseconds)

        assertTrue(sample.frames.isEmpty())
        assertEquals(50.milliseconds, sample.nextChangeIn)
    }

    @Test
    fun `late sampling selects the current frame directly`() {
        val calls = mutableListOf<Int>()
        val animation = TestAnimation(frameCount = 100, frameDuration = 10.milliseconds, calls = calls)
        val playback = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "value")))

        val sample = playback.sample(875.milliseconds)

        assertEquals('A' + 87, sample.frames.single().letter())
        assertEquals(listOf(87), calls)
    }

    @Test
    fun `binary search handles maximum frame count near its end`() {
        val calls = mutableListOf<Int>()
        val animation = NumericAnimation(
            frameCount = Int.MAX_VALUE,
            frameDuration = 1.nanoseconds,
            calls = calls,
        )
        val playback = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "large")))
        val end = 1.nanoseconds * Int.MAX_VALUE

        val sample = playback.sample(end - 1.nanoseconds)

        assertEquals(Int.MAX_VALUE - 1, calls.single())
        val rendered = render(sample.frames.single().content, animation.size.width, animation.size.height)
        assertEquals(
            (Int.MAX_VALUE - 1).toString(),
            (0 until animation.size.width).joinToString("") { rendered.get(it, 0).char }.trim(),
        )
    }

    @Test
    fun `sampling beyond all clips and empty playback are complete`() {
        val animation = TestAnimation(frameCount = 1, frameDuration = 10.milliseconds)
        val playback = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "value")))

        assertEquals(SampleOf(emptyList(), null), sampleShape(playback.sample(11.milliseconds)))
        assertEquals(SampleOf(emptyList(), null), sampleShape(AnimationPlayback<String>(emptyList()).sample(ZERO)))
    }

    @Test
    fun `next change is the earliest pending or visible transition`() {
        val first = TestAnimation(frameCount = 2, frameDuration = 30.milliseconds)
        val second = TestAnimation(frameCount = 1, frameDuration = 10.milliseconds)
        val playback = AnimationPlayback(
            listOf(
                AnimationPlayback.Clip(first, "first"),
                AnimationPlayback.Clip(second, "second", startAfter = 20.milliseconds),
            ),
        )

        assertEquals(20.milliseconds, playback.sample(ZERO).nextChangeIn)
        assertEquals(10.milliseconds, playback.sample(20.milliseconds).nextChangeIn)
        assertEquals(30.milliseconds, playback.sample(30.milliseconds).nextChangeIn)
    }

    @Test
    fun `sampling is repeatable and supports out of order times`() {
        val animation = TestAnimation(frameCount = 3, frameDuration = 10.milliseconds)
        val playback = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "value")))

        val late = playback.sample(25.milliseconds)
        playback.sample(5.milliseconds)
        val lateAgain = playback.sample(25.milliseconds)

        assertEquals(late.frames.map { it.letter() }, lateAgain.frames.map { it.letter() })
        assertEquals(late.nextChangeIn, lateAgain.nextChangeIn)
    }

    @Test
    fun `only visible clips request frames`() {
        val calls = mutableListOf<Int>()
        val animation = TestAnimation(frameCount = 2, frameDuration = 10.milliseconds, calls = calls)
        val playback = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "value", 100.milliseconds)))

        playback.sample(50.milliseconds)
        assertTrue(calls.isEmpty())
        playback.sample(100.milliseconds)
        assertEquals(listOf(0), calls)
        playback.sample(120.milliseconds)
        assertEquals(listOf(0), calls)
    }

    @Test
    fun `caller clip list mutation does not alter playback`() {
        val clips = mutableListOf<AnimationPlayback.Clip<String>>()
        val animation = TestAnimation(frameCount = 1, frameDuration = 10.milliseconds)
        clips += AnimationPlayback.Clip(animation, "original")
        val playback = AnimationPlayback(clips)
        clips.clear()

        assertEquals(listOf("original"), playback.sample(ZERO).frames.map { it.value })
    }

    @Test
    fun `metadata is captured at construction`() {
        val animation = TestAnimation(frameCount = 2, frameDuration = 10.milliseconds)
        val playback = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "value")))
        animation.frameCount = 1
        animation.frameDuration = 100.milliseconds

        assertEquals('B', playback.sample(15.milliseconds).frames.single().letter())
        assertEquals(5.milliseconds, playback.sample(15.milliseconds).nextChangeIn)
    }

    @Test
    fun `invalid timeline metadata and elapsed values are rejected`() {
        fun playback(frameCount: Int = 1, frameDuration: Duration = 1.milliseconds, startAfter: Duration = ZERO) =
            AnimationPlayback(listOf(AnimationPlayback.Clip(TestAnimation(frameCount, frameDuration), "value", startAfter)))

        assertThrows<IllegalArgumentException> { playback(frameCount = 0) }
        assertThrows<IllegalArgumentException> { playback(frameCount = -1) }
        assertThrows<IllegalArgumentException> { playback(frameDuration = ZERO) }
        assertThrows<IllegalArgumentException> { playback(frameDuration = (-1).milliseconds) }
        assertThrows<IllegalArgumentException> { playback(frameDuration = INFINITE) }
        assertThrows<IllegalArgumentException> { playback(startAfter = (-1).milliseconds) }
        assertThrows<IllegalArgumentException> { playback(startAfter = INFINITE) }
        assertThrows<IllegalArgumentException> { playback().sample((-1).milliseconds) }
        assertThrows<IllegalArgumentException> { playback().sample(INFINITE) }
    }

    @Test
    fun `finite metadata whose total duration overflows is rejected`() {
        val animation = TestAnimation(frameCount = Int.MAX_VALUE, frameDuration = Long.MAX_VALUE.nanoseconds)

        assertThrows<IllegalArgumentException> {
            AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "value")))
        }
    }

    @Test
    fun `finite start and duration whose end offset overflows are rejected`() {
        val large = (Long.MAX_VALUE / 3).milliseconds
        assertTrue(large.isFinite())
        assertTrue((large + large).isInfinite())
        val animation = TestAnimation(frameCount = 1, frameDuration = large)

        assertThrows<IllegalArgumentException> {
            AnimationPlayback(
                listOf(
                    AnimationPlayback.Clip(
                        animation,
                        "value",
                        startAfter = large,
                    ),
                ),
            )
        }
    }

    @Test
    fun `ordinary views can be played without glyph grids`() {
        val animation = object : Animation {
            override val size = AnimationSize(1, 1)
            override val frameCount = 1
            override val frameDuration = 1.milliseconds
            override fun frame(index: Int): View = LetterView('Z')
        }

        val sample = AnimationPlayback(listOf(AnimationPlayback.Clip(animation, "plain"))).sample(ZERO)

        assertFalse(sample.frames.single().content is GlyphGrid)
        assertEquals('Z', sample.frames.single().letter())
    }

    private data class SampleOf(
        private val values: List<Any?>,
        private val nextChangeIn: Duration?,
    )

    private fun <T> sampleShape(sample: AnimationPlayback.Sample<T>): SampleOf =
        SampleOf(sample.frames.map { it.value }, sample.nextChangeIn)
}
