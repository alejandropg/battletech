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
        weapons = listOf(
            WeaponTargetInfo(0, "AC/20", 58, 20, listOf(), available = true),
            WeaponTargetInfo(1, "ML", 72, 5, listOf(), available = true),
        ),
    )

    private val targetB = TargetInfo(
        unitId = UnitId("hunch"),
        unitName = "Hunchback",
        weapons = listOf(
            WeaponTargetInfo(0, "LRM15", 45, 15, listOf("+1 second"), available = true),
        ),
    )

    private fun renderToString(view: TargetsView, width: Int = 22, height: Int = 30): String {
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
    fun `renders target name with primary tag`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            cursorTargetIndex = 0,
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
            cursorTargetIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains("[S]"))
    }

    @Test
    fun `cursor target name highlighted but arrow on weapon line`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            cursorTargetIndex = 0,
            cursorWeaponIndex = 0,
        )

        val output = renderToString(view)

        // Arrow appears on a weapon line, not suppressed
        assertTrue(output.contains("\u25B6"))
        assertTrue(output.contains("Atlas"))
    }

    @Test
    fun `weapons always show success percentage`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            cursorTargetIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains("58%"))
        assertTrue(output.contains("72%"))
    }

    @Test
    fun `toggled weapons show asterisk`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = mapOf(UnitId("atlas") to setOf(0)),
            primaryTargetId = UnitId("atlas"),
            cursorTargetIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains("[*]"))
    }

    @Test
    fun `no attack entry shown when showNoAttack is true`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 0,
            showNoAttack = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("No Attack"))
    }

    @Test
    fun `no attack entry selected when cursorTargetIndex equals targets size`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 1,   // == targets.size → "No Attack" selected
            showNoAttack = true,
        )

        val output = renderToString(view)

        assertTrue(output.contains("No Attack"))
        assertTrue(output.contains("\u25B6"))
    }

    @Test
    fun `no cursor when cursorTargetIndex is negative (TorsoFacing preview)`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = -1,
        )

        val output = renderToString(view)

        assertTrue(output.contains("Atlas"))
        assertFalse(output.contains("\u25B6"))
    }

    @Test
    fun `handles multiple targets with weapons`() {
        val view = TargetsView(
            targets = listOf(targetA, targetB),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            cursorTargetIndex = 1,
            cursorWeaponIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains("Atlas"))
        assertTrue(output.contains("Hunchback"))
    }

    @Test
    fun `disabled weapon renders with zero percent`() {
        val targetWithDisabled = TargetInfo(
            unitId = UnitId("atlas"),
            unitName = "Atlas",
            weapons = listOf(
                WeaponTargetInfo(0, "AC/20", 58, 20, listOf(), available = true),
                WeaponTargetInfo(1, "LRM15", 0, 15, listOf(), available = false),
            ),
        )
        val view = TargetsView(
            targets = listOf(targetWithDisabled),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 0,
            cursorWeaponIndex = 0,
        )

        val output = renderToString(view)

        // Both weapons rendered
        assertTrue(output.contains("AC/20"))
        assertTrue(output.contains("LRM15"))
        // Disabled weapon shows 0%
        assertTrue(output.contains("0%"))
    }

    @Test
    fun `arrow navigates across target boundary`() {
        // This is a controller-level test; here we just verify rendering with cursor on second target
        val view = TargetsView(
            targets = listOf(targetA, targetB),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 1,
            cursorWeaponIndex = 0,
            showNoAttack = true,
        )

        val output = renderToString(view)

        // Cursor arrow should appear in the Hunchback target section
        assertTrue(output.contains("\u25B6"))
        assertTrue(output.contains("Hunchback"))
    }
}
