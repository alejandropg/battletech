package battletech.tui.view

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.unit.UnitId
import battletech.tui.game.PanelId
import battletech.tui.screen.Canvas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PanelSlotTest {

    private val realView = object : View {
        override fun render(canvas: Canvas) {
            canvas.writeString(0, 0, "CONTENT")
        }
    }

    @Test
    fun `collapsed slot resolves to a CollapsedPanelView carrying the key and title`() {
        val slot = PanelSlot(PanelId.ATTACK_RESULTS, 7, AttackResultsView.TITLE, collapsed = true) { realView }

        val resolved = resolvePanel(slot)

        assertTrue(resolved is CollapsedPanelView) { "Expected CollapsedPanelView, got $resolved" }
        resolved as CollapsedPanelView
        assertEquals(PanelId.ATTACK_RESULTS.key, resolved.key)
        assertEquals(AttackResultsView.TITLE, resolved.title)
    }

    @Test
    fun `expanded slot wraps content in ScrollableView and renders box plus content`() {
        val slot = PanelSlot(
            key = PanelId.ATTACK_RESULTS,
            width = 34,
            title = AttackResultsView.TITLE,
            collapsed = false,
            scrollOffset = null,
        ) { realView }

        val resolved = resolvePanel(slot)

        // Must be a ScrollableView (internal class — verify via rendering)
        val buffer = render(resolved!!, 34, 10)
        // Box: top-left corner present
        assertEquals("╭", buffer.get(0, 0).char)
        // Content "CONTENT" placed at x+2, y+2 (inside box, past the padding spacer row)
        assertEquals("C", buffer.get(2, 2).char)
        assertEquals("O", buffer.get(3, 2).char)
    }

    @Test
    fun `expanded slot passes scrollOffset into the wrapper`() {
        val contentView = object : View {
            override fun render(canvas: Canvas) {
                for (i in 0 until 20) canvas.writeString(0, i, "row$i")
            }
        }
        val slot = PanelSlot(
            key = PanelId.LOG,
            width = 30,
            title = "T",
            collapsed = false,
            scrollOffset = 5,
        ) { contentView }

        val resolved = resolvePanel(slot)!!
        val buffer = render(resolved, 30, 10)

        // offset=5 → first visible row is row5
        val firstLine = (2 until 8).joinToString("") { buffer.get(it, 2).char }.trimEnd()
        assertEquals("row5", firstLine)
    }

    @Test
    fun `slot with no width resolves to null`() {
        val slot = PanelSlot(PanelId.ATTACK_RESULTS, 0, AttackResultsView.TITLE, collapsed = true) { realView }

        assertNull(resolvePanel(slot))
    }

    @Test
    fun `buildReal is not invoked for a collapsed slot`() {
        var built = false
        val slot = PanelSlot(PanelId.ATTACK_RESULTS, 7, AttackResultsView.TITLE, collapsed = true) {
            built = true
            realView
        }

        resolvePanel(slot)

        assertTrue(!built) { "buildReal must not run when the panel is collapsed" }
    }

    @Test
    fun `expanded slot auto-follows a marked focus row in its content — general, not TargetsView-specific`() {
        // TargetsView marks the cursor's weapon row as focus (see TargetsView.kt); resolvePanel's
        // ScrollableView must scroll it into view with no explicit scrollOffset (null = anchored
        // at the top, which — absent auto-follow — would show weapon 0, not weapon 18).
        val targets = listOf(
            TargetInfo(
                unitId = UnitId("t"),
                unitName = "Target",
                weapons = (0 until 20).map { WeaponTargetInfo(it, "Weapon$it", 8, 50, emptyList()) },
            ),
        )
        val content = TargetsView(
            targets = targets,
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 0,
            cursorWeaponIndex = 18,
        )
        val slot = PanelSlot(
            key = PanelId.TARGETS, width = 30, title = "TARGETS", collapsed = false,
            scrollOffset = null,
        ) { content }

        val resolved = resolvePanel(slot)!!
        val buffer = render(resolved, 30, 12)

        assertTrue(
            buffer.text().contains("Weapon18"),
            "expected the cursor's weapon row (18) scrolled into view:\n${buffer.text()}",
        )
    }

    @Test
    fun `a wheel-scrolled panel stays put while its focus row is unchanged`() {
        // Same TARGETS content as above, but this time the panel reports the focus it already had
        // last render (the weapon cursor hasn't moved) and carries a manual scroll offset. The
        // panel must respect that offset instead of snapping back to the weapon cursor — the
        // panel-side half of the pan-snap-back defect.
        val targets = listOf(
            TargetInfo(
                unitId = UnitId("t"),
                unitName = "Target",
                weapons = (0 until 20).map { WeaponTargetInfo(it, "Weapon$it", 8, 50, emptyList()) },
            ),
        )
        fun content() = TargetsView(
            targets = targets,
            weaponAssignments = emptyMap(),
            primaryTargetId = null,
            cursorTargetIndex = 0,
            cursorWeaponIndex = 18,
        )

        // First render establishes where the focus is.
        val firstSlot = PanelSlot(
            key = PanelId.TARGETS, width = 30, title = "TARGETS", collapsed = false,
            scrollOffset = null,
        ) { content() }
        val first = resolvePanel(firstSlot) as ScrollableView
        render(first, 30, 12)
        val focus = first.state.focus!!

        // Second render: user has wheeled back to the top, focus unchanged.
        val secondSlot = PanelSlot(
            key = PanelId.TARGETS, width = 30, title = "TARGETS", collapsed = false,
            scrollOffset = 0, previousFocus = focus,
        ) { content() }
        val second = resolvePanel(secondSlot) as ScrollableView
        val buffer = render(second, 30, 12)

        assertEquals(0, second.state.offset.y, "wheel-scrolled panel must not snap back to the cursor")
        assertTrue(
            buffer.text().contains("Weapon0"),
            "expected the top of the list after scrolling there:\n${buffer.text()}",
        )
    }

    @Test
    fun `null buildReal result for expanded slot resolves to null`() {
        val slot = PanelSlot(
            key = PanelId.LOG,
            width = 30,
            title = "T",
            collapsed = false,
            scrollOffset = null,
        ) { null }

        assertNull(resolvePanel(slot))
    }
}
