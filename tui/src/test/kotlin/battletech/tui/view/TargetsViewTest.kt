package battletech.tui.view

import battletech.tactical.attack.ToHitBase
import battletech.tactical.attack.ToHitBreakdown
import battletech.tactical.attack.ToHitFactor
import battletech.tactical.attack.ToHitModifier
import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.unit.UnitId
import battletech.tui.icon.diceRoll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tenter.screen.ChromeRole
import tenter.view.line
import tenter.view.render
import tenter.view.text
import tenter.widget.CheckState
import tenter.widget.checkboxIcon

internal class TargetsViewTest {

    private val targetA = TargetInfo(
        unitId = UnitId("atlas"),
        unitName = "Atlas",
        weapons = listOf(
            available(0, "AC/20", 20, skill = 7),
            available(1, "Medium Laser", 5, skill = 6),
        ),
    )

    private val targetB = TargetInfo(
        unitId = UnitId("hunch"),
        unitName = "Hunchback",
        weapons = listOf(
            available(0, "LRM15", 15, skill = 7, modifiers = listOf(ToHitModifier(ToHitFactor.SECONDARY_TARGET, "second", 1))),
        ),
    )

    private fun available(
        weaponIndex: Int,
        weaponName: String,
        damage: Int,
        skill: Int,
        modifiers: List<ToHitModifier> = emptyList(),
    ) = WeaponTargetInfo.Available(
        weaponIndex = weaponIndex,
        weaponName = weaponName,
        damage = damage,
        toHit = ToHitBreakdown(ToHitBase.GUNNERY, skill, modifiers),
    )

    private fun renderToString(view: TargetsView, width: Int = 28, height: Int = 30): String =
        render(view, width, height).text()

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
    fun `target line uses the canonical id- name format`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            cursorTargetIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains("atlas: Atlas")) { "Expected id-and-name target line: $output" }
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
    fun `weapon row shows needed dice roll before success percentage`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            cursorTargetIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains("${diceRoll()}7 58%"))
        assertTrue(output.contains("${diceRoll()}6 72%"))
    }

    @Test
    fun `toggled weapons show checked checkbox`() {
        val view = TargetsView(
            targets = listOf(targetA),
            weaponAssignments = mapOf(UnitId("atlas") to setOf(0)),
            primaryTargetId = UnitId("atlas"),
            cursorTargetIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains(checkboxIcon(CheckState.CHECKED)))
    }

    @Test
    fun `empty targets shows No targets message`() {
        val view = TargetsView(
            targets = emptyList(),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains("No targets"))
    }

    @Test
    fun `handles multiple targets with weapons`() {
        val view = TargetsView(
            targets = listOf(targetA, targetB),
            weaponAssignments = emptyMap(),
            primaryTargetId = UnitId("atlas"),
            cursorTargetIndex = 1,
        )

        val output = renderToString(view)

        assertTrue(output.contains("Atlas"))
        assertTrue(output.contains("Hunchback"))
    }

    @Test
    fun `unavailable weapon renders no target number or hit chance`() {
        val targetWithUnavailable = TargetInfo(
            unitId = UnitId("atlas"),
            unitName = "Atlas",
            weapons = listOf(
                available(0, "AC/20", 20, skill = 7),
                WeaponTargetInfo.Unavailable(weaponIndex = 1, weaponName = "LRM15", damage = 15),
            ),
        )
        val view = TargetsView(
            targets = listOf(targetWithUnavailable),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 0,
        )

        // Both weapons rendered
        val width = 28
        val height = 30
        val buffer = render(view, width, height)
        assertTrue(buffer.text().contains("AC/20"))
        assertTrue(buffer.text().contains("LRM15"))

        // Unavailable weapon's own row carries no target number or hit chance — a sentinel like
        // "⚄13 0%" would assert a falsehood (there is no to-hit math to show) rather than
        // withhold a truth. Row-scoped: a global check would be fooled by AC/20's own "58%".
        val lrmRow = (0 until height).first { row -> "LRM15" in buffer.line(row) }
        val lrmLine = buffer.line(lrmRow)
        assertTrue("%" !in lrmLine) { "Expected no hit chance on the unavailable weapon's row: $lrmLine" }
        assertTrue("13" !in lrmLine) { "Expected no target number sentinel on the unavailable weapon's row: $lrmLine" }

        // Disabled weapon row is rendered in gray
        val rowColors = (0 until width).map { col -> buffer.get(col, lrmRow).style.fg }.toSet()
        assertTrue(rowColors.contains(ChromeRole.DISABLED)) { "Expected disabled weapon row to use ChromeRole.DISABLED, got: $rowColors" }
    }

    @Test
    fun `each weapon renders its own modifiers`() {
        val targetWithDifferingMods = TargetInfo(
            unitId = UnitId("atlas"),
            unitName = "Atlas",
            weapons = listOf(
                available(0, "AC/20", 20, skill = 5, modifiers = listOf(ToHitModifier(ToHitFactor.RANGE, "med", 2))),
                available(1, "Medium Laser", 5, skill = 2, modifiers = listOf(ToHitModifier(ToHitFactor.RANGE, "long", 4))),
            ),
        )
        val view = TargetsView(
            targets = listOf(targetWithDifferingMods),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 0,
        )

        val output = renderToString(view)

        assertTrue(output.contains("+2 med"))
        assertTrue(output.contains("+4 long"))
    }

    @Test
    fun `arrow navigates across target boundary`() {
        // This is a controller-level test; here we just verify rendering with cursor on second target
        val view = TargetsView(
            targets = listOf(targetA, targetB),
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 1,
        )

        val output = renderToString(view)

        // Cursor arrow should appear in the Hunchback target section
        assertTrue(output.contains("\u25B6"))
        assertTrue(output.contains("Hunchback"))
    }
}
