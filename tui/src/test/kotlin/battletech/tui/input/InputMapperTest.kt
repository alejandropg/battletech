package battletech.tui.input

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.input.MouseEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class InputMapperTest {

    private fun key(key: String, ctrl: Boolean = false): KeyboardEvent =
        KeyboardEvent(key, ctrl = ctrl, alt = false)

    @Nested
    inner class IsQuitTest {
        @Test
        fun `q is quit`() {
            assertTrue(InputMapper.isQuit(key("q")))
        }

        @Test
        fun `ctrl+c is quit`() {
            assertTrue(InputMapper.isQuit(key("c", ctrl = true)))
        }

        @Test
        fun `other keys are not quit`() {
            assertFalse(InputMapper.isQuit(key("ArrowUp")))
            assertFalse(InputMapper.isQuit(key("Enter")))
        }
    }

    @Nested
    inner class MapIdlePhaseStateEventTest {
        @Test
        fun `arrow up maps to MoveCursor north`() {
            assertEquals(IdleAction.MoveCursor(HexDirection.N), InputMapper.mapIdleEvent(key("ArrowUp")))
        }

        @Test
        fun `arrow down maps to MoveCursor south`() {
            assertEquals(IdleAction.MoveCursor(HexDirection.S), InputMapper.mapIdleEvent(key("ArrowDown")))
        }

        @Test
        fun `arrow right maps to MoveCursor southeast`() {
            assertEquals(IdleAction.MoveCursor(HexDirection.SE), InputMapper.mapIdleEvent(key("ArrowRight")))
        }

        @Test
        fun `arrow left maps to MoveCursor northwest`() {
            assertEquals(IdleAction.MoveCursor(HexDirection.NW), InputMapper.mapIdleEvent(key("ArrowLeft")))
        }

        @Test
        fun `enter maps to SelectUnit`() {
            assertEquals(IdleAction.SelectUnit, InputMapper.mapIdleEvent(key("Enter")))
        }

        @Test
        fun `tab maps to CycleUnit`() {
            assertEquals(IdleAction.CycleUnit, InputMapper.mapIdleEvent(key("Tab")))
        }

        @Test
        fun `c maps to CommitDeclarations`() {
            assertEquals(IdleAction.CommitDeclarations, InputMapper.mapIdleEvent(key("c")))
        }

        @Test
        fun `unknown key returns null`() {
            assertNull(InputMapper.mapIdleEvent(key("F12")))
        }
    }

    @Nested
    inner class MapBrowsingEventTest {
        @Test
        fun `arrow up maps to MoveCursor north`() {
            assertEquals(BrowsingAction.MoveCursor(HexDirection.N), InputMapper.mapBrowsingEvent(key("ArrowUp")))
        }

        @Test
        fun `arrow down maps to MoveCursor south`() {
            assertEquals(BrowsingAction.MoveCursor(HexDirection.S), InputMapper.mapBrowsingEvent(key("ArrowDown")))
        }

        @Test
        fun `arrow right maps to MoveCursor southeast`() {
            assertEquals(BrowsingAction.MoveCursor(HexDirection.SE), InputMapper.mapBrowsingEvent(key("ArrowRight")))
        }

        @Test
        fun `arrow left maps to MoveCursor northwest`() {
            assertEquals(BrowsingAction.MoveCursor(HexDirection.NW), InputMapper.mapBrowsingEvent(key("ArrowLeft")))
        }

        @Test
        fun `enter maps to ConfirmPath`() {
            assertEquals(BrowsingAction.ConfirmPath, InputMapper.mapBrowsingEvent(key("Enter")))
        }

        @Test
        fun `escape maps to Cancel`() {
            assertEquals(BrowsingAction.Cancel, InputMapper.mapBrowsingEvent(key("Escape")))
        }

        @Test
        fun `tab maps to CycleMode`() {
            assertEquals(BrowsingAction.CycleMode, InputMapper.mapBrowsingEvent(key("Tab")))
        }

        @Test
        fun `number keys 1-6 map to SelectFacing`() {
            for (n in 1..6) {
                assertEquals(BrowsingAction.SelectFacing(n), InputMapper.mapBrowsingEvent(key("$n")))
            }
        }

        @Test
        fun `number keys 7-9 return null`() {
            for (n in 7..9) {
                assertNull(InputMapper.mapBrowsingEvent(key("$n")))
            }
        }

        @Test
        fun `unknown key returns null`() {
            assertNull(InputMapper.mapBrowsingEvent(key("F12")))
        }
    }

    @Nested
    inner class MapFacingEventTest {
        @Test
        fun `number keys 1-6 map to SelectFacing`() {
            for (n in 1..6) {
                assertEquals(FacingAction.SelectFacing(n), InputMapper.mapFacingEvent(key("$n")))
            }
        }

        @Test
        fun `escape maps to Cancel`() {
            assertEquals(FacingAction.Cancel, InputMapper.mapFacingEvent(key("Escape")))
        }

        @Test
        fun `arrow keys return null`() {
            assertNull(InputMapper.mapFacingEvent(key("ArrowUp")))
            assertNull(InputMapper.mapFacingEvent(key("ArrowDown")))
        }

        @Test
        fun `unknown key returns null`() {
            assertNull(InputMapper.mapFacingEvent(key("Enter")))
        }
    }

    @Nested
    inner class MapAttackPhaseStateEventTest {
        @Test
        fun `arrow right maps to TwistTorso clockwise`() {
            assertEquals(AttackAction.TwistTorso(clockwise = true), InputMapper.mapAttackEvent(key("ArrowRight")))
        }

        @Test
        fun `arrow left maps to TwistTorso counterclockwise`() {
            assertEquals(AttackAction.TwistTorso(clockwise = false), InputMapper.mapAttackEvent(key("ArrowLeft")))
        }

        @Test
        fun `arrow up maps to NavigateWeapons -1`() {
            assertEquals(AttackAction.NavigateWeapons(delta = -1), InputMapper.mapAttackEvent(key("ArrowUp")))
        }

        @Test
        fun `arrow down maps to NavigateWeapons +1`() {
            assertEquals(AttackAction.NavigateWeapons(delta = 1), InputMapper.mapAttackEvent(key("ArrowDown")))
        }

        @Test
        fun `space maps to ToggleWeapon`() {
            assertEquals(AttackAction.ToggleWeapon, InputMapper.mapAttackEvent(key(" ")))
        }

        @Test
        fun `tab maps to NextAttacker`() {
            assertEquals(AttackAction.NextAttacker, InputMapper.mapAttackEvent(key("Tab")))
        }

        @Test
        fun `enter maps to Confirm`() {
            assertEquals(AttackAction.Confirm, InputMapper.mapAttackEvent(key("Enter")))
        }

        @Test
        fun `escape maps to Cancel`() {
            assertEquals(AttackAction.Cancel, InputMapper.mapAttackEvent(key("Escape")))
        }

        @Test
        fun `unknown key returns null`() {
            assertNull(InputMapper.mapAttackEvent(key("q")))
        }
    }

    @Nested
    inner class MapMouseToHexTest {
        @Test
        fun `left click maps to hex coordinates`() {
            val event = MouseEvent(x = 5, y = 3, left = true)

            val result = InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)

            assertEquals(HexCoordinates(0, 0), result)
        }

        @Test
        fun `non-left click returns null`() {
            val event = MouseEvent(x = 5, y = 3)

            assertNull(InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2))
        }

        @Test
        fun `right click returns null`() {
            val event = MouseEvent(x = 5, y = 3, right = true)

            assertNull(InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2))
        }

        @Test
        fun `click in margin returns null`() {
            val event = MouseEvent(x = 1, y = 1, left = true)

            assertNull(InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2))
        }

        @Test
        fun `left click at hex 1,0 maps correctly`() {
            val event = MouseEvent(x = 13, y = 5, left = true)

            val result = InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)

            assertEquals(HexCoordinates(1, 0), result)
        }

        @Test
        fun `left click at hex 2,1 maps correctly`() {
            val event = MouseEvent(x = 21, y = 7, left = true)

            val result = InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)

            assertEquals(HexCoordinates(2, 1), result)
        }
    }
}
