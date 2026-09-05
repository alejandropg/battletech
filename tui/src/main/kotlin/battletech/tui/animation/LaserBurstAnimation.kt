package battletech.tui.animation

import tenter.animation.Animation
import tenter.animation.AnimationSize
import tenter.animation.GlyphGrid
import tenter.screen.Cell
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.time.Duration

/**
 * First-person laser bursts converging on a target reticle. Each animation generates its burst
 * layout once at construction and reuses it for every frame.
 *
 * [random] drives every burst's origin, target, and timing. The star field ([drawStars]) uses its
 * own fixed-seed [Random] as a deterministic backdrop independent of the burst layout.
 */
internal class LaserBurstAnimation(
    bursts: Int = DEFAULT_BURSTS,
    targetX: Int = DEFAULT_TARGET_X,
    targetY: Int = DEFAULT_TARGET_Y,
    radius: Int = DEFAULT_RADIUS,
    random: Random = Random.Default,
) : Animation {
    override val size: AnimationSize = AnimationSize(width = 70, height = 20)
    private val bursts: List<LaserBurst> = buildBursts(bursts, targetX, targetY, radius, size, random)
    override val frameCount: Int = sequenceFrames(this.bursts)
    override val frameDuration: Duration = ANIMATION_DURATION / frameCount

    override fun frame(index: Int): GlyphGrid {
        require(index in 0 until frameCount) { "frame index out of range: $index" }
        val glyphs = GlyphGrid(size, ::laserPriority, ::laserStyle)
        drawStars(glyphs, index)
        for (burst in bursts) drawBurst(glyphs, burst, index)
        return glyphs
    }

    internal companion object {
        const val DEFAULT_BURSTS: Int = 20 // 16
        const val DEFAULT_TARGET_X: Int = 10 // 20
        const val DEFAULT_TARGET_Y: Int = 7 // 8
        const val DEFAULT_RADIUS: Int = 4 // 5
    }
}

private data class LaserBurst(
    val delay: Int,
    val travel: Int,
    val origin: Point,
    val target: Point,
    val beamChar: Char,
)

private val LASER_PRIORITY: Map<Char, Int> = mapOf(
    ' ' to 0, '.' to 1, ',' to 1, ':' to 2, 'o' to 3,
    '=' to 5, '~' to 5, '-' to 5, '+' to 6, '*' to 7, '@' to 8, 'X' to 9, '#' to 10,
)

private fun laserPriority(char: Char): Int = LASER_PRIORITY[char] ?: 0

private val LASER_COLORS: Map<Char, AnimationColor> = mapOf(
    '.' to ANIMATION_GRAY, ',' to ANIMATION_GRAY, ':' to ANIMATION_GRAY,
    'o' to ANIMATION_CYAN,
    '=' to ANIMATION_DANGER, '~' to ANIMATION_MAGENTA, '-' to ANIMATION_WARNING,
    '+' to ANIMATION_DANGER, '*' to ANIMATION_WARNING,
    '@' to ANIMATION_BRIGHT, 'X' to ANIMATION_BRIGHT, '#' to ANIMATION_BRIGHT,
)

// Unmapped chars are always ' '. The foreground is never actually seen on a space, but must still
// be a FixedColorRole rather than a themed ChromeRole, or resolving this Style's tags would pull
// the loaded theme's default foreground into an otherwise fully hardcoded palette.
private fun laserStyle(char: Char): Cell.Style =
    Cell.Style(fg = LASER_COLORS[char] ?: ANIMATION_GRAY, bg = ANIMATION_BACKGROUND)

private fun buildBursts(
    total: Int,
    centerX: Int,
    centerY: Int,
    radius: Int,
    size: AnimationSize,
    random: Random,
): List<LaserBurst> {
    val targets = validTargetPoints(centerX, centerY, radius, size)
    val sides = MutableList(total) { if (it % 2 == 0) LaserSide.RIGHT else LaserSide.BOTTOM }
    sides.shuffle(random)

    // The RIGHT side asymmetrically excludes row 0 from its *first* origin only; every retry (and
    // the BOTTOM side, always) allows the full range.
    fun initialOrigin(side: LaserSide): Point = when (side) {
        LaserSide.RIGHT -> point(size.width - 1, random.nextInt(1, size.height))
        LaserSide.BOTTOM -> point(random.nextInt(size.width / 2, size.width), size.height - 1)
    }
    fun retryOrigin(side: LaserSide): Point = when (side) {
        LaserSide.RIGHT -> point(size.width - 1, random.nextInt(size.height))
        LaserSide.BOTTOM -> point(random.nextInt(size.width / 2, size.width), size.height - 1)
    }

    val result = mutableListOf<LaserBurst>()
    for ((index, side) in sides.withIndex()) {
        val target = targets.random(random)
        var origin = initialOrigin(side)
        // Check first, then redraw (and consume random values) only while the current origin is
        // still too close, for at most 20 attempts.
        var attempt = 0
        while (attempt < 20 && hypot(origin.first - target.first, origin.second - target.second) < 8.0) {
            origin = retryOrigin(side)
            attempt++
        }
        val distance = hypot(origin.first - target.first, origin.second - target.second)
        val travel = maxOf(6, pyRound(distance / random.nextDouble(3.2, 4.7)))
        val group = index / 4
        val delay = group * 8 + (index % 4) + random.nextInt(0, 2)
        result.add(
            LaserBurst(
                delay = delay,
                travel = travel,
                origin = origin,
                target = target,
                beamChar = listOf('=', '~', '-').random(random),
            ),
        )
    }
    return result
}

private enum class LaserSide { RIGHT, BOTTOM }

private fun sequenceFrames(bursts: List<LaserBurst>): Int =
    bursts.maxOf { it.delay + it.travel + 7 } + 12

private fun drawStars(glyphs: GlyphGrid, frame: Int) {
    val random = Random(191)
    for (index in 0 until 38) {
        val x = random.nextInt(glyphs.width)
        val y = random.nextInt(glyphs.height)
        val phase = (frame + index * 5) % 19
        glyphs.put(x, y, if (phase == 0) ':' else if (phase < 13) '.' else ',')
    }
}

private fun drawOriginFlash(glyphs: GlyphGrid, origin: Point, age: Int) {
    val x = origin.first.toInt()
    val y = origin.second.toInt()
    when (age) {
        0 -> {
            glyphs.put(x, y, '#')
            glyphs.put(x - 1, y, '*')
            glyphs.put(x + 1, y, '*')
            glyphs.put(x, y - 1, '+')
            glyphs.put(x, y + 1, '+')
        }
        1 -> {
            glyphs.put(x, y, '*')
            glyphs.put(x - 1, y, '+')
        }
    }
}

private fun drawImpact(glyphs: GlyphGrid, target: Point, age: Int) {
    val x = target.first.toInt()
    val y = target.second.toInt()
    when {
        age <= 1 -> {
            glyphs.put(x, y, 'X')
            glyphs.put(x - 1, y, '*')
            glyphs.put(x + 1, y, '*')
            glyphs.put(x, y - 1, '+')
            glyphs.put(x, y + 1, '+')
        }
        age <= 3 -> {
            glyphs.put(x, y, '*')
            glyphs.put(x - 2, y, '+')
            glyphs.put(x + 2, y, '+')
            glyphs.put(x, y - 1, '+')
            glyphs.put(x, y + 1, '+')
        }
        age <= 6 -> {
            glyphs.put(x, y, ':')
            glyphs.put(x - 1, y, '.')
            glyphs.put(x + 1, y, '.')
        }
    }
}

private fun drawBurst(glyphs: GlyphGrid, burst: LaserBurst, frame: Int): Boolean {
    val age = frame - burst.delay
    if (age < 0 || age > burst.travel + 6) return false

    if (age <= 1) drawOriginFlash(glyphs, burst.origin, age)

    if (age <= burst.travel) {
        val progress = age.toDouble() / burst.travel
        val tail = maxOf(0.0, progress - 0.34)
        val middle = tail + (progress - tail) * 0.38
        glyphs.drawSegment(
            pointBetween(burst.origin, burst.target, tail),
            pointBetween(burst.origin, burst.target, middle),
            ':',
        )
        glyphs.drawSegment(
            pointBetween(burst.origin, burst.target, middle),
            pointBetween(burst.origin, burst.target, progress),
            burst.beamChar,
        )
        val head = pointBetween(burst.origin, burst.target, progress)
        glyphs.put(pyRound(head.first), pyRound(head.second), '@')
    } else {
        val impactAge = age - burst.travel
        if (impactAge <= 2) glyphs.drawSegment(burst.origin, burst.target, ':')
        drawImpact(glyphs, burst.target, impactAge)
    }
    return true
}
