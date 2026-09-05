package battletech.tui.animation

import tenter.screen.Cell
import kotlin.math.abs

/** The intrinsic cell dimensions of one animation frame. */
internal data class AnimationSize(val width: Int, val height: Int) {
    init {
        require(width > 0) { "width must be positive, was $width" }
        require(height > 0) { "height must be positive, was $height" }
    }
}

/** A point in animation-grid space, always [Double] — a burst's origin/target are integer cells,
 * but `t`-sampled curve points (missile trails) are not, and every draw routine wants one shape. */
internal typealias Point = Pair<Double, Double>

internal fun point(x: Int, y: Int): Point = x.toDouble() to y.toDouble()

internal fun point(x: Double, y: Double): Point = x to y

/**
 * An [AnimationSize] grid of single-character glyphs, plus the palette those glyphs render in.
 *
 * [priority] and [style] are per-instance rather than shared because the three animations
 * genuinely disagree: `'o'` is priority 3 in the laser animation but 7 in the missile one, and
 * yellow in one but cyan in another. Carrying [style] here (rather than leaving it on
 * [WeaponAnimation]) is what lets a finished frame be rendered from the frame alone — a caller
 * never has to remember to pair the grid with the animation that produced it.
 *
 * [put] enforces the "a higher-priority glyph is never overdrawn by a lower one" compositing rule
 * used to layer effects (an impact flash over the beam that produced it, a beam core over its own
 * dim trail) without callers having to draw in exactly the right order.
 */
internal class Glyphs(
    val size: AnimationSize,
    private val priority: (Char) -> Int,
    private val style: (Char) -> Cell.Style,
) {
    val width: Int = size.width
    val height: Int = size.height

    private val cells: Array<CharArray> = Array(height) { CharArray(width) { ' ' } }

    /** The glyph at ([x], [y]). Throws outside bounds — callers index deliberately, unlike [put]. */
    fun get(x: Int, y: Int): Char = cells[y][x]

    /** The [Cell.Style] the glyph at ([x], [y]) renders in. Throws outside bounds, like [get]. */
    fun styleAt(x: Int, y: Int): Cell.Style = style(get(x, y))

    /**
     * Writes [char] at ([x], [y]) unless a strictly higher-priority glyph already occupies that
     * cell. Silently drops writes outside the grid — every animation's geometry routinely computes
     * off-canvas points (a beam's origin past the right edge, a missile control point above row 0).
     */
    fun put(x: Int, y: Int, char: Char) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        if (priority(char) >= priority(cells[y][x])) {
            cells[y][x] = char
        }
    }

    /**
     * Unconditional overwrite, bypassing [priority] entirely — used by the missile salvo's
     * end-of-cycle fade-out when a cell must be changed directly instead of going through `put`.
     */
    fun set(x: Int, y: Int, char: Char) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        cells[y][x] = char
    }
}

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
internal fun Glyphs.drawSegment(start: Point, end: Point, char: Char) {
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
