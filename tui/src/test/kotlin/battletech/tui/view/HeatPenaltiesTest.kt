package battletech.tui.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole

/** Focused tests for [HeatPenalties.lines] — the pure current/projected heat penalty mapping. */
internal class HeatPenaltiesTest {

    @Test
    fun `cool unit has no penalties`() {
        assertEquals(emptyList<Pair<String, ChromeRole>>(), HeatPenalties.lines(current = 0, projected = 0))
    }

    @Test
    fun `penalty already applied at current heat is solid`() {
        // current 9: -1 MP (5+), +1 To-Hit (8+); projected same -> both solid
        val lines = HeatPenalties.lines(current = 9, projected = 9)

        assertEquals(
            listOf(
                "-1 MP" to ChromeRole.DEFAULT,
                "+1 To-Hit" to ChromeRole.DEFAULT,
            ),
            lines,
        )
    }

    @Test
    fun `penalty only at projected heat is gray`() {
        // current 0: nothing applied; projected 9: -1 MP, +1 To-Hit -> projection only
        val lines = HeatPenalties.lines(current = 0, projected = 9)

        assertEquals(
            listOf(
                "-1 MP" to ChromeRole.DRAFT,
                "+1 To-Hit" to ChromeRole.DRAFT,
            ),
            lines,
        )
    }

    @Test
    fun `mixed applied and projection-only categories`() {
        // current 9: -1 MP, +1 To-Hit (both applied/solid)
        // projected 15: -3 MP, +2 To-Hit (worse -> gray), shutdown 4+ (new -> gray), ammo 4+ (new -> gray)
        val lines = HeatPenalties.lines(current = 9, projected = 15)

        assertEquals(
            listOf(
                "-3 MP" to ChromeRole.DRAFT,
                "+2 To-Hit" to ChromeRole.DRAFT,
                "Shutdown 4+" to ChromeRole.DRAFT,
                "Ammo 4+" to ChromeRole.DRAFT,
            ),
            lines,
        )
    }

    @Test
    fun `shutdown target applied when already at current heat`() {
        // current 14: -2 MP, +2 To-Hit, shutdown 4+ all applied; projected 14: same -> all solid
        val lines = HeatPenalties.lines(current = 14, projected = 14)

        assertEquals(
            listOf(
                "-2 MP" to ChromeRole.DEFAULT,
                "+2 To-Hit" to ChromeRole.DEFAULT,
                "Shutdown 4+" to ChromeRole.DEFAULT,
            ),
            lines,
        )
    }

    @Test
    fun `shutdown target projection-only when only reached at projected heat`() {
        // current 0: none; projected 17: -3 MP, +2 To-Hit, shutdown 6+, ammo 4+ -> all gray
        val lines = HeatPenalties.lines(current = 0, projected = 17)

        assertEquals(
            listOf(
                "-3 MP" to ChromeRole.DRAFT,
                "+2 To-Hit" to ChromeRole.DRAFT,
                "Shutdown 6+" to ChromeRole.DRAFT,
                "Ammo 4+" to ChromeRole.DRAFT,
            ),
            lines,
        )
    }

    @Test
    fun `auto shutdown at 30 plus is the most severe rung`() {
        // current 17 (-3 MP, shutdown 6+), projected 30 (-5 MP, +4 To-Hit, auto, ammo 10+) -> all projection-only gray
        val lines = HeatPenalties.lines(current = 17, projected = 30)

        assertEquals(
            listOf(
                "-5 MP" to ChromeRole.DRAFT,
                "+4 To-Hit" to ChromeRole.DRAFT,
                "Shutdown AUTO" to ChromeRole.DRAFT,
                "Ammo 10+" to ChromeRole.DRAFT,
            ),
            lines,
        )
    }

    @Test
    fun `auto shutdown already applied at current heat is solid`() {
        val lines = HeatPenalties.lines(current = 30, projected = 30)

        assertEquals(
            listOf(
                "-5 MP" to ChromeRole.DEFAULT,
                "+4 To-Hit" to ChromeRole.DEFAULT,
                "Shutdown AUTO" to ChromeRole.DEFAULT,
                "Ammo 10+" to ChromeRole.DEFAULT,
            ),
            lines,
        )
    }

    @Test
    fun `ammo explosion target nullable and projection-only`() {
        // current 0: none; projected 15: -3 MP, +2 To-Hit, shutdown 4+, ammo 4+ -> all gray
        val lines = HeatPenalties.lines(current = 0, projected = 15)

        assertEquals(
            listOf(
                "-3 MP" to ChromeRole.DRAFT,
                "+2 To-Hit" to ChromeRole.DRAFT,
                "Shutdown 4+" to ChromeRole.DRAFT,
                "Ammo 4+" to ChromeRole.DRAFT,
            ),
            lines,
        )
    }

    @Test
    fun `ammo explosion target already applied at current heat`() {
        // current == projected == 19: -3 MP, +3 To-Hit, shutdown 6+, ammo 6+ -> all solid
        val lines = HeatPenalties.lines(current = 19, projected = 19)

        assertEquals(
            listOf(
                "-3 MP" to ChromeRole.DEFAULT,
                "+3 To-Hit" to ChromeRole.DEFAULT,
                "Shutdown 6+" to ChromeRole.DEFAULT,
                "Ammo 6+" to ChromeRole.DEFAULT,
            ),
            lines,
        )
    }

    @Test
    fun `cooling unit shows worst (current) value as solid`() {
        // current 15: -3 MP, +2 To-Hit, shutdown 4+, ammo 4+ (all worse than at 8) -> worst stays current, solid
        val lines = HeatPenalties.lines(current = 15, projected = 8)

        assertEquals(
            listOf(
                "-3 MP" to ChromeRole.DEFAULT,
                "+2 To-Hit" to ChromeRole.DEFAULT,
                "Shutdown 4+" to ChromeRole.DEFAULT,
                "Ammo 4+" to ChromeRole.DEFAULT,
            ),
            lines,
        )
    }
}
