package battletech.tui.animation

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.ToHitAttempt
import battletech.tactical.attack.ToHitBase
import battletech.tactical.attack.ToHitBreakdown
import battletech.tactical.dice.DiceRoll
import battletech.tactical.unit.UnitId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WeaponCategoryTest {

    private fun miss(weaponName: String) = AttackResult.Miss(
        attempt = ToHitAttempt(
            attackerId = UnitId("a"),
            targetId = UnitId("b"),
            weaponName = weaponName,
            toHitRoll = DiceRoll(2, 3),
            toHit = ToHitBreakdown(ToHitBase.GUNNERY, skill = 4, modifiers = emptyList()),
        ),
    )

    @Test
    fun `a mixed volley yields one entry per category, in declaration order`() {
        val results = listOf(miss("LRM 10"), miss("Medium Laser"), miss("AC/5"))

        assertEquals(
            listOf(WeaponCategory.ENERGY, WeaponCategory.BALLISTIC, WeaponCategory.MISSILE),
            categoriesOf(results),
        )
    }

    @Test
    fun `several weapons of one category collapse to a single entry`() {
        val results = listOf(miss("Medium Laser"), miss("Large Laser"), miss("PPC"), miss("Small Laser"))

        assertEquals(listOf(WeaponCategory.ENERGY), categoriesOf(results))
    }

    @Test
    fun `a miss counts exactly like a hit — the weapon was still fired`() {
        assertEquals(listOf(WeaponCategory.ENERGY), categoriesOf(listOf(miss("Medium Laser"))))
    }

    @Test
    fun `an unrecognised weapon name contributes no category`() {
        // "Med Laser" is not a real display name; the real one is "Medium Laser".
        assertEquals(listOf(WeaponCategory.BALLISTIC), categoriesOf(listOf(miss("Med Laser"), miss("Machine Gun"))))
    }

    @Test
    fun `an entirely unrecognised volley yields nothing, so no overlay plays`() {
        assertTrue(categoriesOf(listOf(miss("Med Laser"), miss("Nonesuch"))).isEmpty())
        assertTrue(categoriesOf(emptyList()).isEmpty())
    }

    @Test
    fun `each category maps to its matching animation`() {
        assertTrue(WeaponAnimations.animationFor(WeaponCategory.ENERGY) is LaserBurstAnimation)
        assertTrue(WeaponAnimations.animationFor(WeaponCategory.BALLISTIC) is MachineGunAnimation)
        assertTrue(WeaponAnimations.animationFor(WeaponCategory.MISSILE) is MissileSalvoAnimation)
    }

    @Test
    fun `forVolley builds one animation per category, in categoriesOf's declared order`() {
        val results = listOf(miss("LRM 10"), miss("Medium Laser"), miss("AC/5"))

        val animations = WeaponAnimations.forVolley(results)

        assertEquals(3, animations.size)
        assertTrue(animations[0] is LaserBurstAnimation, "ENERGY sorts first")
        assertTrue(animations[1] is MachineGunAnimation, "BALLISTIC sorts second")
        assertTrue(animations[2] is MissileSalvoAnimation, "MISSILE sorts third")
    }

    @Test
    fun `every animation plays for the same fixed duration, regardless of its native frame count`() {
        val animations = WeaponCategory.entries.map { WeaponAnimations.animationFor(it) }

        animations.forEach { animation ->
            assertTrue(animation.frameCount > 0)
            // frameDuration is ANIMATION_DURATION / frameCount by construction, so the product can
            // land a few nanoseconds off ANIMATION_DURATION through integer division — assert the
            // relationship that actually matters (every panel's own frameDuration derivation),
            // rather than a product equality that rounding would make flaky.
            assertEquals(ANIMATION_DURATION / animation.frameCount, animation.frameDuration)
        }
    }
}
