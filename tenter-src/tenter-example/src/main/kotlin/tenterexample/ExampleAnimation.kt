package tenterexample

import kotlin.time.Duration.Companion.milliseconds
import tenter.animation.Animation
import tenter.animation.AnimationPlayback
import tenter.animation.AnimationSize
import tenter.animation.GlyphGrid
import tenter.screen.Cell
import tenter.palette.ChromeRole
import tenter.view.View

internal class ExampleAnimation : Animation {
    override val size: AnimationSize = AnimationSize(width = 3, height = 1)
    override val frameCount: Int = 2
    override val frameDuration = 100.milliseconds

    private val frames: List<View> = listOf(
        frame('A', ChromeRole.INFO),
        frame('B', ChromeRole.SUCCESS),
    )

    override fun frame(index: Int): View = frames[index]

    private fun frame(glyph: Char, role: ChromeRole): View = GlyphGrid(
        size = size,
        priority = { if (it == ' ') 0 else 1 },
        style = { if (it == glyph) Cell.Style(fg = role) else Cell.Style.DEFAULT },
    ).also { grid ->
        grid.set(1, 0, glyph)
    }
}

internal fun animationProbe(): AnimationProbe {
    val playback = AnimationPlayback(
        listOf(AnimationPlayback.Clip(animation = ExampleAnimation(), value = "demo")),
    )
    val start = playback.sample(0.milliseconds)
    val next = playback.sample(100.milliseconds)
    val end = playback.sample(200.milliseconds)

    check(start.frames.single().value == "demo") { "animation start value was not preserved" }
    check(next.frames.single().value == "demo") { "animation next value was not preserved" }
    check(end.frames.isEmpty()) { "animation remained visible at its exact end" }
    check(start.nextChangeIn == 100.milliseconds) { "animation start transition was not reported" }
    check(next.nextChangeIn == 100.milliseconds) { "animation end transition was not reported" }
    check(end.nextChangeIn == null) { "finished animation reported another transition" }

    return AnimationProbe(start.frames.single().content, next.frames.single().content, end.frames.size)
}
