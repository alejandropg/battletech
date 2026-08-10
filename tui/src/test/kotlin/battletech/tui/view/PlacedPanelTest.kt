package battletech.tui.view

import battletech.tactical.attack.weapon.TargetInfo
import battletech.tactical.attack.weapon.WeaponTargetInfo
import battletech.tactical.model.HexCoordinates
import battletech.tactical.unit.UnitId
import battletech.tui.aGameState
import battletech.tui.game.AppState
import battletech.tui.game.PanelId
import battletech.tui.game.phase.MovementPhase
import battletech.tui.screen.Canvas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PlacedPanelTest {

    // build lambdas below all ignore their PanelInputs argument, so this stand-in never needs to
    // be a realistic frame — only a valid one to pass through.
    private val frame = PanelInputs(AppState(gameState = aGameState(), phase = MovementPhase.SelectingUnit, cursor = HexCoordinates(0, 0)))

    private val realView = object : View {
        override fun render(canvas: Canvas) {
            canvas.writeString(0, 0, "CONTENT")
        }
    }

    private fun placed(
        id: PanelId = PanelId.ATTACK_RESULTS,
        width: Int = 30,
        title: String = "T",
        collapsed: Boolean = false,
        build: (PanelInputs) -> View? = { realView },
    ) = PlacedPanel(Panel(id, title, width, build = build), x = 0, width = width, collapsed = collapsed)

    @Test
    fun `collapsed panel resolves to a CollapsedPanelView carrying the key and title`() {
        val slot = placed(id = PanelId.ATTACK_RESULTS, width = 7, title = AttackResultsView.TITLE, collapsed = true)

        val resolved = slot.pane(frame, scrollOffset = null, previousFocus = null)

        assertTrue(resolved is CollapsedPanelView) { "Expected CollapsedPanelView, got $resolved" }
        resolved as CollapsedPanelView
        assertEquals(PanelId.ATTACK_RESULTS.key, resolved.key)
        assertEquals(AttackResultsView.TITLE, resolved.title)
    }

    @Test
    fun `expanded panel wraps content in ScrollableView and renders box plus content`() {
        val slot = placed(id = PanelId.ATTACK_RESULTS, width = 34, title = AttackResultsView.TITLE)

        val resolved = slot.pane(frame, scrollOffset = null, previousFocus = null)

        // Must be a ScrollableView (internal class — verify via rendering)
        val buffer = render(resolved!!, 34, 10)
        // Box: top-left corner present
        assertEquals("╭", buffer.get(0, 0).char)
        // Content "CONTENT" placed at x+2, y+2 (inside box, past the padding spacer row)
        assertEquals("C", buffer.get(2, 2).char)
        assertEquals("O", buffer.get(3, 2).char)
    }

    @Test
    fun `expanded panel passes scrollOffset into the wrapper`() {
        val contentView = object : View {
            override fun render(canvas: Canvas) {
                for (i in 0 until 20) canvas.writeString(0, i, "row$i")
            }
        }
        val slot = placed(id = PanelId.LOG, width = 30, title = "T") { contentView }

        val resolved = slot.pane(frame, scrollOffset = 5, previousFocus = null)!!
        val buffer = render(resolved, 30, 10)

        // offset=5 → first visible row is row5
        val firstLine = (2 until 8).joinToString("") { buffer.get(it, 2).char }.trimEnd()
        assertEquals("row5", firstLine)
    }

    @Test
    fun `panel with no width resolves to null`() {
        val slot = placed(id = PanelId.ATTACK_RESULTS, width = 0, collapsed = true)

        assertNull(slot.pane(frame, scrollOffset = null, previousFocus = null))
    }

    @Test
    fun `panel build is not invoked for a collapsed slot`() {
        var built = false
        val slot = placed(id = PanelId.ATTACK_RESULTS, width = 7, collapsed = true) {
            built = true
            realView
        }

        slot.pane(frame, scrollOffset = null, previousFocus = null)

        assertTrue(!built) { "Panel.build must not run when the panel is collapsed" }
    }

    @Test
    fun `expanded panel auto-follows a marked focus row in its content — general, not TargetsView-specific`() {
        // TargetsView marks the cursor's weapon row as focus (see TargetsView.kt); pane()'s
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
        val slot = placed(id = PanelId.TARGETS, width = 30, title = "TARGETS") { content }

        val resolved = slot.pane(frame, scrollOffset = null, previousFocus = null)!!
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
        val firstSlot = placed(id = PanelId.TARGETS, width = 30, title = "TARGETS") { content() }
        val first = firstSlot.pane(frame, scrollOffset = null, previousFocus = null) as ScrollableView
        render(first, 30, 12)
        val focus = first.scroll.focus!!

        // Second render: user has wheeled back to the top, focus unchanged.
        val secondSlot = placed(id = PanelId.TARGETS, width = 30, title = "TARGETS") { content() }
        val second = secondSlot.pane(frame, scrollOffset = 0, previousFocus = focus) as ScrollableView
        val buffer = render(second, 30, 12)

        assertEquals(0, second.scroll.offset.y, "wheel-scrolled panel must not snap back to the cursor")
        assertTrue(
            buffer.text().contains("Weapon0"),
            "expected the top of the list after scrolling there:\n${buffer.text()}",
        )
    }

    @Test
    fun `null Panel-build result for expanded slot resolves to null`() {
        val slot = placed(id = PanelId.LOG, width = 30, title = "T") { null }

        assertNull(slot.pane(frame, scrollOffset = null, previousFocus = null))
    }
}
