package battletech.tui.animation

import tenter.view.Bordered
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Every weapon animation plays for exactly this long, regardless of its native frame count. A
 * concrete animation derives [WeaponAnimation.frameDuration] as `ANIMATION_DURATION / frameCount`
 * rather than exposing its own fps — generative parameters such as burst count and travel speed
 * produce a different frame count per animation and per random layout, and this is what keeps the
 * wall-clock length the same regardless.
 */
internal val ANIMATION_DURATION: Duration = 5.seconds

/**
 * One weapon-fire ASCII animation: its intrinsic [size] grid played over [frameCount] frames at
 * [frameDuration] each, always spanning [ANIMATION_DURATION] in total.
 *
 * [frame] must be pure in [index] — [battletech.tui.loop.runLoop] calls it once per tick and never
 * caches the result across renders — it recomputes the whole canvas from scratch every call. The
 * returned [Glyphs] carries its own palette, so it can be drawn without consulting the animation
 * again.
 *
 * A concrete animation owns its own random burst/shot/missile layout, generated once at
 * construction from the injected [kotlin.random.Random].
 */
internal interface WeaponAnimation {
    /** The fixed dimensions of every [frame] returned by this animation. */
    val size: AnimationSize
    val frameCount: Int
    val frameDuration: Duration

    /** The fully painted frame at [index], `0 until frameCount`. */
    fun frame(index: Int): Glyphs
}

/** The outer dimensions of this animation when wrapped in a [Bordered] panel. */
internal val WeaponAnimation.panelSize: AnimationSize
    get() = AnimationSize(
        width = size.width + Bordered.BORDER.left + Bordered.BORDER.right,
        height = size.height + Bordered.BORDER.top + Bordered.BORDER.bottom,
    )
