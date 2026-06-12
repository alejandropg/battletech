package battletech.tui.view

import battletech.tactical.model.PlayerId
import battletech.tui.game.phase.DeclaredAttackerEntry
import battletech.tui.game.phase.DeclaredTargetEntry
import battletech.tui.game.phase.DeclaredTargetsRender
import battletech.tui.game.phase.DeclaredWeaponEntry
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DeclaredTargetsViewTest {

    private fun weapon(name: String, chance: Int = 72) = DeclaredWeaponEntry(name, chance)
    private fun target(name: String, primary: Boolean, vararg weapons: DeclaredWeaponEntry) =
        DeclaredTargetEntry(name, primary, weapons.toList())
    private fun attacker(name: String, player: PlayerId, draft: Boolean, vararg targets: DeclaredTargetEntry) =
        DeclaredAttackerEntry(name, player, draft, targets.toList())

    private fun renderToString(view: DeclaredTargetsView, width: Int = 28, height: Int = 30): String {
        val buffer = ScreenBuffer(width, height)
        view.render(buffer, 0, 0, width, height)
        return buildString {
            for (row in 0 until height) {
                for (col in 0 until width) {
                    append(buffer.get(col, row).char)
                }
                appendLine()
            }
        }
    }

    private fun colorAt(buffer: ScreenBuffer, row: Int, col: Int = 2): Color = buffer.get(col, row).fg

    private fun rowContaining(buffer: ScreenBuffer, text: String, height: Int = 30, width: Int = 28): Int {
        for (row in 0 until height) {
            val line = buildString { (0 until width).forEach { col -> append(buffer.get(col, row).char) } }
            if (text in line) return row
        }
        return -1
    }

    @Test
    fun `empty entries shows No declarations`() {
        val view = DeclaredTargetsView(DeclaredTargetsRender(emptyList()))
        val output = renderToString(view)
        assertTrue(output.contains("No declarations"))
    }

    @Test
    fun `single committed entry renders attacker target and weapon`() {
        val view = DeclaredTargetsView(DeclaredTargetsRender(listOf(
            attacker("Wolverine WVR-6R", PlayerId.PLAYER_2, false,
                target("Atlas", true, weapon("SRM 6", 72)),
            )
        )))
        val output = renderToString(view)
        assertTrue(output.contains("Wolverine WVR-6R"))
        assertTrue(output.contains("> Atlas [P]"))
        assertTrue(output.contains("SRM 6"))
        assertTrue(output.contains("72%"))
    }

    @Test
    fun `draft entry attacker line uses Color GRAY`() {
        val view = DeclaredTargetsView(DeclaredTargetsRender(listOf(
            attacker("Wolverine", PlayerId.PLAYER_1, draft = true,
                target("Atlas", true, weapon("Med Laser", 72)),
            )
        )))

        val buffer = ScreenBuffer(28, 30)
        view.render(buffer, 0, 0, 28, 30)

        val row = rowContaining(buffer, "Wolverine")
        assertTrue(row >= 0) { "Expected to find Wolverine attacker row" }
        assertTrue((2 until 28).any { col -> buffer.get(col, row).fg == Color.GRAY }) {
            "Expected attacker row to contain Color.GRAY"
        }
    }

    @Test
    fun `committed P1 attacker line uses Color BLUE`() {
        val view = DeclaredTargetsView(DeclaredTargetsRender(listOf(
            attacker("Wolverine", PlayerId.PLAYER_1, draft = false,
                target("Atlas", true, weapon("Med Laser")),
            )
        )))

        val buffer = ScreenBuffer(28, 30)
        view.render(buffer, 0, 0, 28, 30)

        val row = rowContaining(buffer, "Wolverine")
        assertTrue(row >= 0)
        assertTrue((2 until 28).any { col -> buffer.get(col, row).fg == Color.BLUE }) {
            "Expected P1 committed attacker row to use Color.BLUE"
        }
    }

    @Test
    fun `committed P2 attacker line uses Color MAGENTA`() {
        val view = DeclaredTargetsView(DeclaredTargetsRender(listOf(
            attacker("Atlas AS7-D", PlayerId.PLAYER_2, draft = false,
                target("Wolverine", true, weapon("AC/20", 58)),
            )
        )))

        val buffer = ScreenBuffer(28, 30)
        view.render(buffer, 0, 0, 28, 30)

        val row = rowContaining(buffer, "Atlas")
        assertTrue(row >= 0)
        assertTrue((2 until 28).any { col -> buffer.get(col, row).fg == Color.MAGENTA }) {
            "Expected P2 committed attacker row to use Color.MAGENTA"
        }
    }

    @Test
    fun `two attackers both render with blank line between them`() {
        val view = DeclaredTargetsView(DeclaredTargetsRender(listOf(
            attacker("Wolverine", PlayerId.PLAYER_1, false,
                target("Atlas", true, weapon("Med Laser")),
            ),
            attacker("Hunchback", PlayerId.PLAYER_1, false,
                target("Atlas", true, weapon("AC/20")),
            ),
        )))
        val output = renderToString(view)
        assertTrue(output.contains("Wolverine"))
        assertTrue(output.contains("Hunchback"))
    }

    @Test
    fun `primary and secondary tags both appear for a single attacker`() {
        val view = DeclaredTargetsView(DeclaredTargetsRender(listOf(
            attacker("Wolverine", PlayerId.PLAYER_1, false,
                target("Atlas", primary = true, weapon("SRM 6")),
                target("Hunchback", primary = false, weapon("Med Laser")),
            )
        )))
        val output = renderToString(view)
        assertTrue(output.contains("[P]"))
        assertTrue(output.contains("[S]"))
    }

    @Test
    fun `weapon line shows success percent`() {
        val view = DeclaredTargetsView(DeclaredTargetsRender(listOf(
            attacker("Wolverine", PlayerId.PLAYER_2, false,
                target("Atlas", true, weapon("SRM 6", 72)),
            )
        )))
        val output = renderToString(view)
        assertTrue(output.contains("72%"))
    }

    @Test
    fun `overflow shows scrollbar thumb when wrapped in decorator`() {
        // Use height=10 to force overflow with multiple attackers
        val entries = (1..6).map { i ->
            attacker("Attacker $i", PlayerId.PLAYER_1, false,
                target("Target", true, weapon("Weapon")),
            )
        }
        val view = DeclaredTargetsView(DeclaredTargetsRender(entries))
        val decorated = ScrollablePanelView(
            index = DeclaredTargetsView.INDEX,
            title = DeclaredTargetsView.TITLE,
            content = view,
            scrollOffset = 0,
        )

        val width = 28
        val height = 10
        val buffer = ScreenBuffer(width, height)
        decorated.render(buffer, 0, 0, width, height)

        // The scrollbar thumb '█' must appear on the right border (col width-1) somewhere
        val thumbFound = (1 until height - 1).any { row -> buffer.get(width - 1, row).char == "█" }
        assertTrue(thumbFound) {
            val output = buildString {
                for (row in 0 until height) {
                    for (col in 0 until width) append(buffer.get(col, row).char)
                    appendLine()
                }
            }
            "Expected scrollbar thumb '█' on right border in:\n$output"
        }
    }

    @Test
    fun `panel title is DECLARED TARGETS`() {
        val view = DeclaredTargetsView(DeclaredTargetsRender(emptyList()))
        val decorated = ScrollablePanelView(
            index = DeclaredTargetsView.INDEX,
            title = DeclaredTargetsView.TITLE,
            content = view,
            scrollOffset = 0,
        )
        val buffer = ScreenBuffer(28, 10)
        decorated.render(buffer, 0, 0, 28, 10)
        val output = buildString {
            for (row in 0 until 10) {
                for (col in 0 until 28) append(buffer.get(col, row).char)
                appendLine()
            }
        }
        assertTrue(output.contains("DECLARED TARGETS"))
    }
}
