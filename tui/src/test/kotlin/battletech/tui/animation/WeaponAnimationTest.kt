package battletech.tui.animation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest
import tenter.animation.Animation
import tenter.screen.Cell
import tenter.screen.ScreenBuffer
import tenter.view.render
import kotlin.random.Random

/** Renders every cell of [this] frame as one string per row, for equality/content assertions. */
private fun Animation.rendered(index: Int): ScreenBuffer = render(frame(index), size.width, size.height)

private fun ScreenBuffer.rows(): List<String> =
    (0 until height).map { y -> (0 until width).map { x -> get(x, y).char }.joinToString("") }

private fun ScreenBuffer.cells(): List<Cell> =
    (0 until height).flatMap { y -> (0 until width).map { x -> get(x, y) } }

internal class WeaponAnimationTest {

    private fun animations(random: Random): List<Animation> = listOf(
        LaserBurstAnimation(random = random),
        MachineGunAnimation(random = random),
        MissileSalvoAnimation(random = random),
    )

    @TestFactory
    fun `every frame is exactly 70x20`(): List<DynamicTest> =
        animations(Random(1)).map { animation ->
            DynamicTest.dynamicTest(animation::class.simpleName ?: "animation") {
                val rows = animation.rendered(0).rows()
                assertEquals(20, rows.size)
                rows.forEach { assertEquals(70, it.length) }
            }
        }

    @TestFactory
    fun `frameCount is positive and the first and last frame render without throwing`(): List<DynamicTest> =
        animations(Random(2)).map { animation ->
            DynamicTest.dynamicTest(animation::class.simpleName ?: "animation") {
                assertTrue(animation.frameCount > 0)
                animation.frame(0)
                animation.frame(animation.frameCount - 1)
            }
        }

    @TestFactory
    fun `the same seed reproduces identical frames`(): List<DynamicTest> {
        val pairs = listOf(
            LaserBurstAnimation(random = Random(99)) to LaserBurstAnimation(random = Random(99)),
            MachineGunAnimation(random = Random(99)) to MachineGunAnimation(random = Random(99)),
            MissileSalvoAnimation(random = Random(99)) to MissileSalvoAnimation(random = Random(99)),
        )
        return pairs.map { (a, b) ->
            DynamicTest.dynamicTest(a::class.simpleName ?: "animation") {
                assertEquals(a.frameCount, b.frameCount)
                val sample = listOf(0, a.frameCount / 2, a.frameCount - 1)
                sample.forEach { index ->
                    assertEquals(
                        a.rendered(index).cells(),
                        b.rendered(index).cells(),
                        "frame $index differs",
                    )
                }
            }
        }
    }

    @TestFactory
    fun `requesting another frame does not change a previously rendered frame`(): List<DynamicTest> =
        animations(Random(123)).map { animation ->
            DynamicTest.dynamicTest(animation::class.simpleName ?: "animation") {
                val index = minOf(2, animation.frameCount - 1)
                val retained = animation.frame(index)
                val expected = render(retained, animation.size.width, animation.size.height)
                animation.frame(animation.frameCount - 1)
                val actual = render(retained, animation.size.width, animation.size.height)

                assertEquals(expected.cells(), actual.cells())
            }
        }

    @Test
    fun `laser radius 0 collapses every burst's target onto the aim point`() {
        val animation = LaserBurstAnimation(bursts = 6, targetX = 30, targetY = 9, radius = 0, random = Random(7))
        var sawImpact = false
        for (index in 0 until animation.frameCount) {
            val rows = animation.rendered(index).rows()
            for (y in rows.indices) {
                for (x in rows[y].indices) {
                    if (rows[y][x] == 'X') {
                        sawImpact = true
                        assertEquals(30, x, "impact 'X' at unexpected column, frame $index")
                        assertEquals(9, y, "impact 'X' at unexpected row, frame $index")
                    }
                }
            }
        }
        assertTrue(sawImpact, "expected at least one impact marker across the animation")
    }

    @Test
    fun `more bursts light strictly more of the grid than fewer bursts, same seed`() {
        fun litCellCount(animation: Animation): Int {
            val lit = mutableSetOf<Pair<Int, Int>>()
            for (index in 0 until animation.frameCount) {
                val rows = animation.rendered(index).rows()
                for (y in rows.indices) for (x in rows[y].indices) if (rows[y][x] != ' ') lit += x to y
            }
            return lit.size
        }

        val few = LaserBurstAnimation(bursts = 1, random = Random(3))
        val many = LaserBurstAnimation(bursts = 40, random = Random(3))
        assertTrue(litCellCount(many) > litCellCount(few), "40 bursts should light more cells than 1")
    }
}
