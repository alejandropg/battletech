package battletech.tui.animation

import tenter.animation.Animation
import tenter.animation.AnimationSize
import tenter.animation.GlyphGrid
import tenter.screen.Cell
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration

/**
 * A radial salvo of missiles on cubic-Bezier curves, bursting outward from a central origin.
 *
 * The class always builds the full 40-missile set and [missileCount] governs how many trajectories
 * actually get drawn — see [buildMissiles]. Taking [random] produces a fresh salvo shape each time
 * an animation is chosen.
 */
internal class MissileSalvoAnimation(
    private val missileCount: Int = DEFAULT_MISSILE_COUNT,
    private val originX: Double = DEFAULT_ORIGIN_X,
    private val originY: Double = DEFAULT_ORIGIN_Y,
    random: Random = Random.Default,
) : Animation {
    override val size: AnimationSize = AnimationSize(width = 70, height = 20)
    override val frameCount: Int = CYCLE_FRAMES
    override val frameDuration: Duration = ANIMATION_DURATION / frameCount

    private val missiles: List<Missile> = buildMissiles(TOTAL_BUILT, originX, originY, random).take(missileCount)

    override fun frame(index: Int): GlyphGrid {
        require(index in 0 until frameCount) { "frame index out of range: $index" }
        val glyphs = GlyphGrid(size, ::missilePriority, ::missileStyle)
        drawStars(glyphs, index)
        for (missile in missiles) drawMissile(glyphs, missile, index)
        drawLaunchBloom(glyphs, index, originX, originY)
        if (index > 78) fadeOut(glyphs, index)
        return glyphs
    }

    internal companion object {
        const val CYCLE_FRAMES: Int = 88
        const val DEFAULT_MISSILE_COUNT: Int = 40 // 30
        const val DEFAULT_ORIGIN_X: Double = 47.0 // 35.0
        const val DEFAULT_ORIGIN_Y: Double = 14.0 // 11.0
        private const val TOTAL_BUILT: Int = 40
    }
}

private data class Missile(
    val delay: Int,
    val flight: Int,
    val start: Point,
    val controlA: Point,
    val controlB: Point,
    val end: Point,
    val scale: Int,
)

private val MISSILE_PRIORITY: Map<Char, Int> = mapOf(
    ' ' to 0, '.' to 1, ',' to 1, ':' to 2, '-' to 3, '|' to 3, '/' to 3, '\\' to 3,
    '~' to 4, '+' to 5, '*' to 6, 'o' to 7, 'O' to 8, '@' to 9, '#' to 10,
)

private fun missilePriority(char: Char): Int = MISSILE_PRIORITY[char] ?: 0

private val MISSILE_COLORS: Map<Char, AnimationColor> = mapOf(
    '.' to ANIMATION_GRAY, ',' to ANIMATION_GRAY, ':' to ANIMATION_CYAN,
    '-' to ANIMATION_BRIGHT, '|' to ANIMATION_BRIGHT, '/' to ANIMATION_BRIGHT, '\\' to ANIMATION_BRIGHT,
    '~' to ANIMATION_BRIGHT_CYAN, '+' to ANIMATION_WARNING, '*' to ANIMATION_BRIGHT,
    'o' to ANIMATION_WARNING, 'O' to ANIMATION_DANGER, '@' to ANIMATION_BRIGHT, '#' to ANIMATION_BRIGHT,
)

// See laserStyle: unmapped (i.e. space) falls back to a FixedColorRole so no cell ever resolves
// through the loaded theme.
private fun missileStyle(char: Char): Cell.Style =
    Cell.Style(fg = MISSILE_COLORS[char] ?: ANIMATION_GRAY, bg = ANIMATION_BACKGROUND)

/**
 * Always builds [total] deterministic-per-[random] curves; the caller slices the prefix it wants
 * drawn, so which missiles appear never changes as [MissileSalvoAnimation.missileCount] is tuned —
 * only how many of the same 40 do.
 */
private fun buildMissiles(total: Int, originX: Double, originY: Double, random: Random): List<Missile> {
    val missiles = mutableListOf<Missile>()
    for (index in 0 until total) {
        var angle = (index.toDouble() / total) * (2 * Math.PI) - Math.PI * 0.94
        angle += random.nextDouble(-0.14, 0.14)
        val radiusX = random.nextDouble(43.0, 58.0)
        val radiusY = random.nextDouble(15.0, 23.0)
        val end = point(originX + cos(angle) * radiusX, originY + sin(angle) * radiusY)

        var turn = if (index % 2 != 0) -1.0 else 1.0
        if (index % 7 == 0) turn *= -1.8
        val normalX = -sin(angle)
        val normalY = cos(angle)
        val firstPush = random.nextDouble(6.0, 14.0) * turn
        val secondPush = random.nextDouble(8.0, 18.0) * -turn

        val controlAx = originX + cos(angle) * random.nextDouble(5.0, 12.0) + normalX * firstPush
        val controlAy = originY + sin(angle) * random.nextDouble(3.0, 7.0) + normalY * firstPush * 0.42
        val controlBx = originX + cos(angle) * radiusX * random.nextDouble(0.48, 0.72) + normalX * secondPush
        val controlBy = originY + sin(angle) * radiusY * random.nextDouble(0.48, 0.72) + normalY * secondPush * 0.58

        val flight = random.nextInt(38, 55)
        val startX = originX + random.nextDouble(-1.8, 1.8)
        val startY = originY + random.nextDouble(-0.8, 0.8)

        missiles.add(
            Missile(
                delay = (index * 7) % 19 - 8,
                flight = flight,
                start = point(startX, startY),
                controlA = point(controlAx, controlAy),
                controlB = point(controlBx, controlBy),
                end = end,
                scale = if (index % 9 == 0) 2 else if (index % 4 == 0) 1 else 0,
            ),
        )
    }
    return missiles
}

private fun cubicPoint(missile: Missile, tRaw: Double): Point {
    val t = tRaw.coerceIn(0.0, 1.0)
    val u = 1.0 - t
    val x = u * u * u * missile.start.first +
        3 * u * u * t * missile.controlA.first +
        3 * u * t * t * missile.controlB.first +
        t * t * t * missile.end.first
    val y = u * u * u * missile.start.second +
        3 * u * u * t * missile.controlA.second +
        3 * u * t * t * missile.controlB.second +
        t * t * t * missile.end.second
    return x to y
}

/** The recent segment is bright; older segments fade to particles. */
private fun trailChar(oldT: Double, newT: Double, oldPos: Point, newPos: Point, fade: Double): Char {
    val age = newT - oldT
    if (fade > 0.62 || age > 0.56) return '.'
    if (fade > 0.28 || age > 0.38) return ':'
    if (age < 0.075) {
        val dx = newPos.first - oldPos.first
        val dy = newPos.second - oldPos.second
        return when {
            abs(dx) > abs(dy) * 1.7 -> '-'
            abs(dy) > abs(dx) * 1.7 -> '|'
            dx * dy > 0 -> '\\'
            else -> '/'
        }
    }
    return '~'
}

private fun drawStars(glyphs: GlyphGrid, frame: Int) {
    val random = Random(707)
    for (index in 0 until 40) {
        val x = random.nextInt(glyphs.width)
        val y = random.nextInt(glyphs.height)
        val phase = (frame + index * 3) % 17
        glyphs.put(x, y, if (phase == 0) ':' else if (phase < 11) '.' else ',')
    }
}

private fun drawMissile(glyphs: GlyphGrid, missile: Missile, frame: Int): Boolean {
    val local = frame - missile.delay
    if (local < 0 || local > missile.flight + 19) return false

    val headT = min(1.0, local.toDouble() / missile.flight)
    val fade = maxOf(0.0, (local - missile.flight) / 19.0)
    val trailLength = if (fade == 0.0) 0.76 else 0.92
    val startT = maxOf(0.0, headT - trailLength)
    val samples = 70
    var previousT = startT
    var previous = cubicPoint(missile, previousT)

    for (sample in 1..samples) {
        val currentT = startT + (headT - startT) * sample / samples
        val current = cubicPoint(missile, currentT)
        val char = trailChar(currentT, headT, previous, current, fade)
        if (fade > 0.45 && (sample + missile.flight) % 4 != 0) {
            previousT = currentT
            previous = current
            continue
        }
        glyphs.drawSegment(previous, current, char)
        previousT = currentT
        previous = current
    }

    if (local <= missile.flight) {
        val (x, y) = cubicPoint(missile, headT)
        val hx = pyRound(x)
        val hy = pyRound(y)
        val head = if (missile.scale == 0) '@' else if (missile.scale == 1) 'O' else '#'
        glyphs.put(hx, hy, head)

        val backT = maxOf(0.0, headT - 0.025)
        val (bx, by) = cubicPoint(missile, backT)
        glyphs.put(pyRound((hx + bx) / 2), pyRound((hy + by) / 2), '*')
        if (missile.scale == 2 && headT > 0.72) {
            glyphs.put(hx - 1, hy, 'O')
            glyphs.put(hx + 1, hy, 'O')
            glyphs.put(hx, hy - 1, '+')
            glyphs.put(hx, hy + 1, '+')
        }
    }
    return true
}

private fun drawLaunchBloom(glyphs: GlyphGrid, frame: Int, originX: Double, originY: Double) {
    if (frame > 28) return
    val pulse = frame % 8
    val radius = maxOf(1, min(5, frame / 4 + (if (pulse < 3) 1 else 0)))
    val cx = pyRound(originX)
    val cy = pyRound(originY)
    for (ring in radius downTo 1) {
        val char = if (ring > 4) '.' else if (ring > 2) ':' else '*'
        var spoke = 0
        while (spoke < 360) {
            val angle = Math.toRadians(spoke.toDouble())
            val x = cx + pyRound(cos(angle) * ring * 1.65)
            val y = cy + pyRound(sin(angle) * ring * 0.72)
            if ((spoke + frame + ring) % 3 != 0) glyphs.put(x, y, char)
            spoke += 30
        }
    }
    glyphs.put(cx, cy, if (pulse < 2) '#' else if (pulse < 5) '@' else '*')
}

/** Post-cycle dissolve so the loop point (played once here, so really just the tail) reads cleanly. */
private fun fadeOut(glyphs: GlyphGrid, frame: Int) {
    val darkness = (frame - 78) / 10.0
    for (y in 0 until glyphs.height) {
        for (x in 0 until glyphs.width) {
            val char = glyphs.get(x, y)
            if (char != ' ' && char != '.' && char != ',' && (x * 7 + y * 11 + frame) % 10 < darkness * 10) {
                glyphs.set(x, y, '.')
            }
        }
    }
}
