package battletech.tui.animation

import battletech.tactical.attack.AttackResult
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Builds the animation overlay for one resolved weapon volley: one panel per distinct weapon
 * category fired, placed per [AnimationLayout]'s fixed positions and appearing [STAGGER] apart.
 */
internal object WeaponAnimations {
    /** Gap between one panel appearing and the next. */
    val STAGGER: Duration = 1.seconds

    fun animationFor(category: WeaponCategory, random: Random = Random.Default): WeaponAnimation =
        when (category) {
            WeaponCategory.ENERGY -> LaserBurstAnimation(random = random)
            WeaponCategory.BALLISTIC -> MachineGunAnimation(random = random)
            WeaponCategory.MISSILE -> MissileSalvoAnimation(random = random)
        }

    /**
     * One animation per distinct category fired in [results], in [categoriesOf]'s (declaration)
     * order. Every animation shares [ANIMATION_DURATION], so — unlike the [STAGGER]ed *start* of
     * each panel — the order they're built in has no effect on how long the volley runs, and they
     * finish in the same order they appeared: first in, first out.
     */
    fun forVolley(results: List<AttackResult>, random: Random = Random.Default): List<WeaponAnimation> =
        categoriesOf(results).map { animationFor(it, random) }

    /**
     * The whole overlay for [results] on a [width] x [height] screen, or `null` when nothing should
     * be drawn — either no fired weapon was recognised, or a single panel does not fit the screen.
     */
    fun volleyFor(
        results: List<AttackResult>,
        width: Int,
        height: Int,
        generation: Long,
        random: Random = Random.Default,
    ): VolleyPlayback? {
        val animations = forVolley(results, random)
        if (animations.isEmpty()) return null
        val placements = AnimationLayout.place(animations, width, height)
        if (placements.isEmpty()) return null
        return VolleyPlayback.start(animations, placements, generation)
    }
}
