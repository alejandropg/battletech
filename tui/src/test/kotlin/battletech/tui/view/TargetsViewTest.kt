package battletech.tui.view

import battletech.tactical.action.UnitId
import battletech.tui.game.TargetInfo
import battletech.tui.game.WeaponTargetInfo
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TargetsViewTest {

    private val targetA = TargetInfo(
        unitId = UnitId("atlas"),
        unitName = "Atlas",
        eligibleWeapons = listOf(
            WeaponTargetInfo(0, "AC/20", 58, 20, listOf()),
            WeaponTargetInfo(1, "ML", 72, 5, listOf()),
        ),
    )

    private val targetB = TargetInfo(
        unitId = UnitId("hunch"),
        unitName = "Hunchback",
        eligibleWeapons = listOf(
            WeaponTargetInfo(0, "LRM15", 45, 15, listOf("+1 second")),
        ),
    )

    private fun renderToString(view: TargetsView, width: Int = 22, height: Int = 20): String {
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

    @Test
    fun `renders target name with primary tag in WeaponAssignment mode`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            selectedTargetIndex = 0,
            showWeapons = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("Atlas"))
        assertTrue(output.contains("[P]"))
    }

    @Test
    fun `renders secondary tag for non-primary target`() {
        val view = TargetsView(
            targets = listOf(targetA, targetB),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            selectedTargetIndex = 0,
            showWeapons = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("[S]"))
    }

    @Test
    fun `selected target has arrow indicator`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            selectedTargetIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains("\u25B6"))
    }

    @Test
    fun `weapons show success percentage when showWeapons is true`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            selectedTargetIndex = 0,
            showWeapons = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("58%"))
        assertTrue(output.contains("72%"))
    }

    @Test
    fun `weapons hidden when showWeapons is false`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            selectedTargetIndex = 0,
            showWeapons = false,
        )

        val output = renderToString(view)

        assertTrue(output.contains("Atlas"))
        assertFalse(output.contains("58%"))
    }

    @Test
    fun `toggled weapons show asterisk`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = mapOf(UnitId("atlas") to setOf(0)),
            primaryTargetId = UnitId("atlas"),
            selectedTargetIndex = 0,
            showWeapons = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("[*]"))
    }

    @Test
    fun `target browsing mode shows cursor without weapon details`() {
        val view = TargetsView(
            targets = listOf(targetA, targetB),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            selectedTargetIndex = 0,
            showWeapons = false,
            showNoAttack = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("Atlas"))
        assertTrue(output.contains("Hunchback"))
        assertTrue(output.contains("\u25B6"))
        assertFalse(output.contains("[P]"))
        assertFalse(output.contains("[S]"))
        assertFalse(output.contains("58%"))
    }

    @Test
    fun `no attack entry shown when showNoAttack is true`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            selectedTargetIndex = 0,
            showWeapons = false,
            showNoAttack = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("No Attack"))
    }

    @Test
    fun `no attack entry selected when selectedTargetIndex equals targets size`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            selectedTargetIndex = 1,   // == targets.size → "No Attack" selected
            showWeapons = false,
            showNoAttack = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("No Attack"))
        assertTrue(output.contains("\u25B6"))
    }

    @Test
    fun `no cursor when selectedTargetIndex is negative (TorsoFacing preview)`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            selectedTargetIndex = -1,
            showWeapons = false,
        )

        val output = renderToString(view)

        assertTrue(output.contains("Atlas"))
        assertFalse(output.contains("\u25B6"))
    }

    @Test
    fun `handles multiple targets`() {
        val view = TargetsView(
            targets = listOf(targetA, targetB),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            selectedTargetIndex = 1,
            showWeapons = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("Atlas"))
        assertTrue(output.contains("Hunchback"))
    }
}
