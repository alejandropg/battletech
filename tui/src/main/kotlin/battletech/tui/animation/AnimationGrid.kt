package battletech.tui.animation

import tenter.animation.AnimationSize
import tenter.animation.GlyphGrid
import kotlin.math.abs

/** A point in animation-grid space, always [Double] — a burst's origin/target are integer cells,
 * but `t`-sampled curve points (missile trails) are not, and every draw routine wants one shape. */
internal typealias Point = Pair<Double, Double>

internal fun point(x: Int, y: Int): Point = x.toDouble() to y.toDouble()

internal fun point(x: Double, y: Double): Point = x to y

/**
 * Animation geometry uses banker's rounding (half-to-even); Kotlin's [kotlin.math.roundToInt] is
 * half-up. Using [Math.rint], the JDK's half-to-even primitive, keeps glyphs on the intended cells
 * at exact `.5` boundaries.
 */
internal fun pyRound(value: Double): Int = Math.rint(value).toInt()

/** Linear interpolation from [origin] to [target] at [progress], clamped to `0.0..1.0`. */
internal fun pointBetween(origin: Point, target: Point, progress: Double): Point {
    val p = progress.coerceIn(0.0, 1.0)
    return (origin.first + (target.first - origin.first) * p) to
        (origin.second + (target.second - origin.second) * p)
}

/**
 * The integer cells inside the circle at ([centerX], [centerY]) with [radius], clipped to the
 * grid — the aim area a burst or shot picks its target from. Shared verbatim by the laser and
 * machine-gun animations (the missile salvo aims radially instead and has no equivalent). Falls
 * back to the center itself so a caller always gets at least one target.
 */
internal fun validTargetPoints(
    centerX: Int,
    centerY: Int,
    radius: Int,
    size: AnimationSize,
): List<Point> {
    val points = mutableListOf<Point>()
    for (y in maxOf(0, centerY - radius)..minOf(size.height - 1, centerY + radius)) {
        for (x in maxOf(0, centerX - radius)..minOf(size.width - 1, centerX + radius)) {
            if ((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY) <= radius * radius) {
                points.add(point(x, y))
            }
        }
    }
    return points.ifEmpty { listOf(point(centerX, centerY)) }
}

/**
 * Draws every integer cell on the line from [start] to [end] as [char] — a Bresenham-ish stepper
 * with enough samples (`max(|dx|, |dy|, 1)`) that consecutive frames' segments never leave a gap.
 */
internal fun GlyphGrid.drawSegment(start: Point, end: Point, char: Char) {
    val x0 = pyRound(start.first)
    val y0 = pyRound(start.second)
    val x1 = pyRound(end.first)
    val y1 = pyRound(end.second)
    val steps = maxOf(abs(x1 - x0), abs(y1 - y0), 1)
    for (step in 0..steps) {
        val x = pyRound(x0 + (x1 - x0) * step.toDouble() / steps)
        val y = pyRound(y0 + (y1 - y0) * step.toDouble() / steps)
        put(x, y, char)
    }
}
