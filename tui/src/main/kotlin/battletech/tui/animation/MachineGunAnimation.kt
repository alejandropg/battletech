package battletech.tui.animation

import battletech.tui.animation.LaserBurstAnimation.Companion.DEFAULT_RADIUS
import battletech.tui.animation.LaserBurstAnimation.Companion.DEFAULT_TARGET_Y
import tenter.screen.Cell
import kotlin.math.hypot
import kotlin.random.Random
import kotlin.time.Duration

/**
 * First-person machine-gun bursts with tracers and ejected casings. Each animation generates its
 * shot layout once at construction and reuses it for every frame.
 */
internal class MachineGunAnimation(
    bursts: Int = DEFAULT_BURSTS,
    targetX: Int = DEFAULT_TARGET_X,
    targetY: Int = DEFAULT_TARGET_Y,
    radius: Int = DEFAULT_RADIUS,
    random: Random = Random.Default,
) : WeaponAnimation {
    override val size: AnimationSize = AnimationSize(width = 70, height = 20)
    private val shots: List<GunShot> = buildShots(bursts, targetX, targetY, radius, size, random)
    override val frameCount: Int = sequenceFrames(shots)
    override val frameDuration: Duration = ANIMATION_DURATION / frameCount

    override fun frame(index: Int): Glyphs {
        val glyphs = Glyphs(size, ::machineGunPriority, ::machineGunStyle)
        drawStars(glyphs, index)
        for (shot in shots) drawShot(glyphs, shot, index)
        return glyphs
    }

    internal companion object {
        const val DEFAULT_BURSTS: Int = 32 // 24
        const val DEFAULT_TARGET_X: Int = 58 // 50
        const val DEFAULT_TARGET_Y: Int = 8 // 7
        const val DEFAULT_RADIUS: Int = 4 // 5
    }
}

private data class GunShot(
    val delay: Int,
    val travel: Int,
    val origin: Point,
    val target: Point,
    val tracerChar: Char,
    val casingSpeed: Double,
    val casingLift: Double,
)

private val MACHINE_GUN_PRIORITY: Map<Char, Int> = mapOf(
    ' ' to 0, '.' to 1, ',' to 1, ':' to 2, 'c' to 3, 'o' to 4,
    '-' to 5, '=' to 5, '+' to 6, '*' to 7, '@' to 8, 'X' to 9, '#' to 10,
)

private fun machineGunPriority(char: Char): Int = MACHINE_GUN_PRIORITY[char] ?: 0

private val MACHINE_GUN_COLORS: Map<Char, AnimationColor> = mapOf(
    '.' to ANIMATION_GRAY, ',' to ANIMATION_GRAY, ':' to ANIMATION_GRAY,
    'c' to ANIMATION_AMBER, 'o' to ANIMATION_WARNING, '-' to ANIMATION_WARNING,
    '=' to ANIMATION_BRIGHT, '+' to ANIMATION_DANGER, '*' to ANIMATION_WARNING,
    '@' to ANIMATION_BRIGHT, 'X' to ANIMATION_BRIGHT, '#' to ANIMATION_BRIGHT,
)

// See laserStyle: unmapped (i.e. space) falls back to a FixedColorRole so no cell ever resolves
// through the loaded theme.
private fun machineGunStyle(char: Char): Cell.Style =
    Cell.Style(fg = MACHINE_GUN_COLORS[char] ?: ANIMATION_GRAY, bg = ANIMATION_BACKGROUND)

private enum class GunSide { LEFT, BOTTOM }

private fun randomOrigin(random: Random, side: GunSide, size: AnimationSize): Point = when (side) {
    GunSide.LEFT -> point(0, random.nextInt(size.height / 2, size.height))
    GunSide.BOTTOM -> point(random.nextInt(0, size.width / 3), size.height - 1)
}

private fun buildShots(
    total: Int,
    centerX: Int,
    centerY: Int,
    radius: Int,
    size: AnimationSize,
    random: Random,
): List<GunShot> {
    val targets = validTargetPoints(centerX, centerY, radius, size)
    val sides = MutableList(total) { if (it % 2 == 0) GunSide.LEFT else GunSide.BOTTOM }
    sides.shuffle(random)

    val result = mutableListOf<GunShot>()
    for ((index, side) in sides.withIndex()) {
        val target = targets.random(random)
        var origin = randomOrigin(random, side, size)
        // Retry an origin while it is too close to the target, for at most 20 attempts.
        var attempt = 0
        while (attempt < 20 && hypot(origin.first - target.first, origin.second - target.second) < 9.0) {
            origin = randomOrigin(random, side, size)
            attempt++
        }
        val distance = hypot(origin.first - target.first, origin.second - target.second)
        val travel = maxOf(4, pyRound(distance / random.nextDouble(5.0, 7.0)))
        val delay = (index / 6) * 14 + (index % 6) * 2 + random.nextInt(0, 2)
        result.add(
            GunShot(
                delay = delay,
                travel = travel,
                origin = origin,
                target = target,
                tracerChar = listOf('-', '-', '=').random(random),
                casingSpeed = random.nextDouble(0.95, 1.45),
                casingLift = random.nextDouble(1.05, 1.45),
            ),
        )
    }
    return result
}

private fun sequenceFrames(shots: List<GunShot>): Int =
    shots.maxOf { it.delay + maxOf(it.travel + 7, 19) } + 10

private fun drawStars(glyphs: Glyphs, frame: Int) {
    val random = Random(812)
    for (index in 0 until 34) {
        val x = random.nextInt(glyphs.width)
        val y = random.nextInt(glyphs.height)
        val phase = (frame + index * 7) % 23
        glyphs.put(x, y, if (phase == 0) ':' else if (phase < 15) '.' else ',')
    }
}

private fun drawMuzzleFlash(glyphs: Glyphs, origin: Point, age: Int) {
    val x = origin.first.toInt()
    val y = origin.second.toInt()
    when (age) {
        0 -> {
            glyphs.put(x, y, '#')
            glyphs.put(x + 1, y, '*')
            glyphs.put(x + 2, y, '+')
            glyphs.put(x + 1, y - 1, '*')
            glyphs.put(x + 1, y + 1, '*')
        }
        1 -> {
            glyphs.put(x, y, '*')
            glyphs.put(x + 1, y, '+')
            glyphs.put(x + 2, y, ':')
        }
    }
}

/** Ejected casing, arcing right independently of the tracer. */
private fun drawCasing(glyphs: Glyphs, shot: GunShot, age: Int) {
    if (age < 1 || age > 18) return
    val startX = shot.origin.first + 2
    val startY = minOf(glyphs.height - 2.0, maxOf(1.0, shot.origin.second - 1))
    val x = startX + shot.casingSpeed * age
    if (x >= glyphs.width) return
    var y = startY - shot.casingLift * age + 0.095 * age * age
    val landed = y >= glyphs.height - 1
    y = minOf(glyphs.height - 1.0, y)
    val char = if (landed && age > 15) '.' else if (age % 3 == 0) 'o' else 'c'
    glyphs.put(pyRound(x), pyRound(y), char)
}

private fun drawImpact(glyphs: Glyphs, target: Point, age: Int) {
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
            glyphs.put(x - 1, y - 1, '+')
            glyphs.put(x + 1, y + 1, '+')
        }
        age <= 6 -> {
            glyphs.put(x, y, ':')
            glyphs.put(x + 1, y - 1, '.')
        }
    }
}

private fun drawShot(glyphs: Glyphs, shot: GunShot, frame: Int): Boolean {
    val age = frame - shot.delay
    if (age < 0 || age > maxOf(shot.travel + 6, 18)) return false

    if (age <= 1) drawMuzzleFlash(glyphs, shot.origin, age)
    drawCasing(glyphs, shot, age)

    if (age <= shot.travel) {
        val progress = age.toDouble() / shot.travel
        val tail = maxOf(0.0, progress - 0.16)
        glyphs.drawSegment(
            pointBetween(shot.origin, shot.target, tail),
            pointBetween(shot.origin, shot.target, progress),
            shot.tracerChar,
        )
        val head = pointBetween(shot.origin, shot.target, progress)
        glyphs.put(pyRound(head.first), pyRound(head.second), '@')
    } else {
        drawImpact(glyphs, shot.target, age - shot.travel)
    }

    if (age in 2..7) {
        val ox = shot.origin.first.toInt()
        val oy = shot.origin.second.toInt()
        glyphs.put(ox + 1 + age / 3, oy - 1 - age / 4, if (age < 5) ':' else '.')
    }
    return true
}
