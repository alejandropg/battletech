package battletech.tui.view

import battletech.tui.aUnit
import battletech.tui.screen.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Focused tests for [UnitStatusView.penaltyLines] — the pure current/projected heat penalty mapping. */
internal class UnitStatusViewPenaltiesTest {

    private val view = UnitStatusView(aUnit())

    @Test
    fun `cool unit has no penalties`() {
        assertEquals(emptyList<Pair<String, Color>>(), view.penaltyLines(current = 0, projected = 0))
    }

    @Test
    fun `penalty already applied at current heat is solid`() {
        // current 9: -1 MP (5+), +1 To-Hit (8+); projected same -> both solid
        val lines = view.penaltyLines(current = 9, projected = 9)

        assertEquals(
            listOf(
                "-1 MP" to Color.DEFAULT,
                "+1 To-Hit" to Color.DEFAULT,
            ),
            lines,
        )
    }

    @Test
    fun `penalty only at projected heat is gray`() {
        // current 0: nothing applied; projected 9: -1 MP, +1 To-Hit -> projection only
        val lines = view.penaltyLines(current = 0, projected = 9)

        assertEquals(
            listOf(
                "-1 MP" to Color.GRAY,
                "+1 To-Hit" to Color.GRAY,
            ),
            lines,
        )
    }

    @Test
    fun `mixed applied and projection-only categories`() {
        // current 9: -1 MP, +1 To-Hit (both applied/solid)
        // projected 15: -3 MP, +2 To-Hit (worse -> gray), shutdown 4+ (new -> gray), ammo 4+ (new -> gray)
        val lines = view.penaltyLines(current = 9, projected = 15)

        assertEquals(
            listOf(
                "-3 MP" to Color.GRAY,
                "+2 To-Hit" to Color.GRAY,
                "Shutdown 4+" to Color.GRAY,
                "Ammo 4+" to Color.GRAY,
            ),
            lines,
        )
    }

    @Test
    fun `shutdown target applied when already at current heat`() {
        // current 14: -2 MP, +2 To-Hit, shutdown 4+ all applied; projected 14: same -> all solid
        val lines = view.penaltyLines(current = 14, projected = 14)

        assertEquals(
            listOf(
                "-2 MP" to Color.DEFAULT,
                "+2 To-Hit" to Color.DEFAULT,
                "Shutdown 4+" to Color.DEFAULT,
            ),
            lines,
        )
    }

    @Test
    fun `shutdown target projection-only when only reached at projected heat`() {
        // current 0: none; projected 17: -3 MP, +2 To-Hit, shutdown 6+, ammo 4+ -> all gray
        val lines = view.penaltyLines(current = 0, projected = 17)

        assertEquals(
            listOf(
                "-3 MP" to Color.GRAY,
                "+2 To-Hit" to Color.GRAY,
                "Shutdown 6+" to Color.GRAY,
                "Ammo 4+" to Color.GRAY,
            ),
            lines,
        )
    }

    @Test
    fun `auto shutdown at 30 plus is the most severe rung`() {
        // current 17 (-3 MP, shutdown 6+), projected 30 (-5 MP, +4 To-Hit, auto, ammo 10+) -> all projection-only gray
        val lines = view.penaltyLines(current = 17, projected = 30)

        assertEquals(
            listOf(
                "-5 MP" to Color.GRAY,
                "+4 To-Hit" to Color.GRAY,
                "Shutdown AUTO" to Color.GRAY,
                "Ammo 10+" to Color.GRAY,
            ),
            lines,
        )
    }

    @Test
    fun `auto shutdown already applied at current heat is solid`() {
        val lines = view.penaltyLines(current = 30, projected = 30)

        assertEquals(
            listOf(
                "-5 MP" to Color.DEFAULT,
                "+4 To-Hit" to Color.DEFAULT,
                "Shutdown AUTO" to Color.DEFAULT,
                "Ammo 10+" to Color.DEFAULT,
            ),
            lines,
        )
    }

    @Test
    fun `ammo explosion target nullable and projection-only`() {
        // current 0: none; projected 15: -3 MP, +2 To-Hit, shutdown 4+, ammo 4+ -> all gray
        val lines = view.penaltyLines(current = 0, projected = 15)

        assertEquals(
            listOf(
                "-3 MP" to Color.GRAY,
                "+2 To-Hit" to Color.GRAY,
                "Shutdown 4+" to Color.GRAY,
                "Ammo 4+" to Color.GRAY,
            ),
            lines,
        )
    }

    @Test
    fun `ammo explosion target already applied at current heat`() {
        // current == projected == 19: -3 MP, +3 To-Hit, shutdown 6+, ammo 6+ -> all solid
        val lines = view.penaltyLines(current = 19, projected = 19)

        assertEquals(
            listOf(
                "-3 MP" to Color.DEFAULT,
                "+3 To-Hit" to Color.DEFAULT,
                "Shutdown 6+" to Color.DEFAULT,
                "Ammo 6+" to Color.DEFAULT,
            ),
            lines,
        )
    }

    @Test
    fun `cooling unit shows worst (current) value as solid`() {
        // current 15: -3 MP, +2 To-Hit, shutdown 4+, ammo 4+ (all worse than at 8) -> worst stays current, solid
        val lines = view.penaltyLines(current = 15, projected = 8)

        assertEquals(
            listOf(
                "-3 MP" to Color.DEFAULT,
                "+2 To-Hit" to Color.DEFAULT,
                "Shutdown 4+" to Color.DEFAULT,
                "Ammo 4+" to Color.DEFAULT,
            ),
            lines,
        )
    }
}
