package battletech.tui.hex

import battletech.tactical.model.HexDirection
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class UnitRendererTest {

    private val ICON_FACING_N  = String(Character.toChars(0xF09C7))
    private val ICON_FACING_NE = String(Character.toChars(0xF09C5))
    private val ICON_FACING_SE = String(Character.toChars(0xF09B9))
    private val ICON_FACING_S  = String(Character.toChars(0xF09BF))
    private val ICON_FACING_SW = String(Character.toChars(0xF09B7))
    private val ICON_FACING_NW = String(Character.toChars(0xF09C3))

    private val ICON_TORSO_N  = String(Character.toChars(0xF005D))
    private val ICON_TORSO_NE = String(Character.toChars(0xF005C))
    private val ICON_TORSO_SE = String(Character.toChars(0xF0043))
    private val ICON_TORSO_S  = String(Character.toChars(0xF0045))
    private val ICON_TORSO_SW = String(Character.toChars(0xF0042))
    private val ICON_TORSO_NW = String(Character.toChars(0xF005B))

    private val ICON_SKULL = String(Character.toChars(0xF068C))

    @Test
    fun `renders unit id at hex center`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.N, Color.CYAN)

        assertEquals("A", buffer.get(4, 3).char)
        assertEquals("1", buffer.get(5, 3).char)
        assertEquals(Color.CYAN, buffer.get(4, 3).style.fg)
        assertEquals(Color.CYAN, buffer.get(5, 3).style.fg)
    }

    @Test
    fun `renders only the chars present when id is shorter than 2 chars`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A", HexDirection.N, Color.CYAN)

        assertEquals("A", buffer.get(4, 3).char)
        assertEquals(" ", buffer.get(5, 3).char)
    }

    @Test
    fun `takes at most 2 chars from a longer id`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "ABC", HexDirection.N, Color.CYAN)

        assertEquals("A", buffer.get(4, 3).char)
        assertEquals("B", buffer.get(5, 3).char)
        assertEquals(" ", buffer.get(6, 3).char)
    }

    @Test
    fun `renders skull marker left of id when destroyed and id row is not crowded`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.N, Color.CYAN, isDestroyed = true)

        assertEquals("A", buffer.get(4, 3).char)
        assertEquals("1", buffer.get(5, 3).char)
        assertEquals(ICON_SKULL, buffer.get(3, 3).char)
        assertEquals(Color.CYAN, buffer.get(3, 3).style.fg)
    }

    @Test
    fun `renders north facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.N, Color.CYAN)

        assertEquals(ICON_FACING_N, buffer.get(4, 2).char)
    }

    @Test
    fun `renders south facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.S, Color.CYAN)

        assertEquals(ICON_FACING_S, buffer.get(4, 3).char)
        assertEquals("A", buffer.get(4, 2).char)
        assertEquals("1", buffer.get(5, 2).char)
    }

    @Test
    fun `renders northeast facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.NE, Color.CYAN)

        assertEquals(ICON_FACING_NE, buffer.get(5, 2).char)
    }

    @Test
    fun `renders southeast facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.SE, Color.CYAN)

        assertEquals(ICON_FACING_SE, buffer.get(5, 3).char)
        assertEquals("A", buffer.get(4, 2).char)
    }

    @Test
    fun `renders southwest facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.SW, Color.CYAN)

        assertEquals(ICON_FACING_SW, buffer.get(3, 3).char)
        assertEquals("A", buffer.get(4, 2).char)
    }

    @Test
    fun `renders northwest facing arrow`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.NW, Color.CYAN)

        assertEquals(ICON_FACING_NW, buffer.get(3, 2).char)
    }

    @Test
    fun `no torso arrow when torso equals leg facing`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.N, Color.CYAN, torsoFacing = HexDirection.N)

        // No extra arrow should be rendered - just the normal facing arrow at (4,2)
        assertEquals(ICON_FACING_N, buffer.get(4, 2).char)
        // Position 5,2 should be empty (no torso arrow)
        assertEquals(" ", buffer.get(5, 2).char)
    }

    @Test
    fun `clockwise twist with N legs places torso arrow at NE slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.N, Color.CYAN, torsoFacing = HexDirection.NE)

        assertEquals(ICON_FACING_N, buffer.get(4, 2).char)
        assertEquals(ICON_TORSO_NE, buffer.get(5, 2).char)
    }

    @Test
    fun `counterclockwise twist with N legs places torso arrow at NW slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.N, Color.CYAN, torsoFacing = HexDirection.NW)

        assertEquals(ICON_FACING_N, buffer.get(4, 2).char)
        assertEquals(ICON_TORSO_NW, buffer.get(3, 2).char)
    }

    @Test
    fun `clockwise twist with NE legs places torso arrow at SE slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.NE, Color.CYAN, torsoFacing = HexDirection.SE)

        assertEquals(ICON_FACING_NE, buffer.get(5, 2).char)
        assertEquals(ICON_TORSO_SE, buffer.get(5, 3).char)
    }

    @Test
    fun `counterclockwise twist with NE legs places torso arrow at N slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.NE, Color.CYAN, torsoFacing = HexDirection.N)

        assertEquals(ICON_FACING_NE, buffer.get(5, 2).char)
        assertEquals(ICON_TORSO_N, buffer.get(4, 2).char)
    }

    @Test
    fun `clockwise twist with SE legs places torso arrow at S slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.SE, Color.CYAN, torsoFacing = HexDirection.S)

        assertEquals(ICON_FACING_SE, buffer.get(5, 3).char)
        assertEquals(ICON_TORSO_S, buffer.get(4, 3).char)
    }

    @Test
    fun `counterclockwise twist with SE legs places torso arrow at NE slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.SE, Color.CYAN, torsoFacing = HexDirection.NE)

        assertEquals(ICON_FACING_SE, buffer.get(5, 3).char)
        assertEquals(ICON_TORSO_NE, buffer.get(5, 2).char)
    }

    @Test
    fun `clockwise twist with S legs places torso arrow at SW slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.S, Color.CYAN, torsoFacing = HexDirection.SW)

        assertEquals(ICON_FACING_S, buffer.get(4, 3).char)
        assertEquals(ICON_TORSO_SW, buffer.get(3, 3).char)
    }

    @Test
    fun `counterclockwise twist with S legs places torso arrow at SE slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.S, Color.CYAN, torsoFacing = HexDirection.SE)

        assertEquals(ICON_FACING_S, buffer.get(4, 3).char)
        assertEquals(ICON_TORSO_SE, buffer.get(5, 3).char)
    }

    @Test
    fun `clockwise twist with SW legs places torso arrow at NW slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.SW, Color.CYAN, torsoFacing = HexDirection.NW)

        assertEquals(ICON_FACING_SW, buffer.get(3, 3).char)
        assertEquals(ICON_TORSO_NW, buffer.get(3, 2).char)
    }

    @Test
    fun `counterclockwise twist with SW legs places torso arrow at S slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.SW, Color.CYAN, torsoFacing = HexDirection.S)

        assertEquals(ICON_FACING_SW, buffer.get(3, 3).char)
        assertEquals(ICON_TORSO_S, buffer.get(4, 3).char)
    }

    @Test
    fun `clockwise twist with NW legs places torso arrow at N slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.NW, Color.CYAN, torsoFacing = HexDirection.N)

        assertEquals(ICON_FACING_NW, buffer.get(3, 2).char)
        assertEquals(ICON_TORSO_N, buffer.get(4, 2).char)
    }

    @Test
    fun `counterclockwise twist with NW legs places torso arrow at SW slot`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.NW, Color.CYAN, torsoFacing = HexDirection.SW)

        assertEquals(ICON_FACING_NW, buffer.get(3, 2).char)
        assertEquals(ICON_TORSO_SW, buffer.get(3, 3).char)
    }

    // --- id-shift edge case: torso arrow lands in the id row at column x+3 --------------------
    //
    // Facing NW: id row is y+3 (facing NW is not "south"), arrow row is y+2.
    // Torso SW: SW is "south" so its own row is y+3 (== id row), and its column offset is 3
    // (x+3). The id's default columns are now x+4/x+5, so a torso arrow at x+3 no longer
    // collides with it — no shift needed, the id stays at its default columns.

    @Test
    fun `id does not shift when facing NW and torso SW puts the torso arrow at x+3 in the id row`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.NW, Color.CYAN, torsoFacing = HexDirection.SW)

        // Torso arrow unaffected, still at its own slot (x+3, id row).
        assertEquals(ICON_TORSO_SW, buffer.get(3, 3).char)
        // Facing arrow unaffected.
        assertEquals(ICON_FACING_NW, buffer.get(3, 2).char)
        // Id stays at its default columns x+4/x+5 — x+3 doesn't collide with them.
        assertEquals("A", buffer.get(4, 3).char)
        assertEquals("1", buffer.get(5, 3).char)
    }

    @Test
    fun `id does not shift when facing SW and torso NW puts the torso arrow at x+3 in the id row`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.SW, Color.CYAN, torsoFacing = HexDirection.NW)

        // Facing SW: id row is y+2 (SW is "south" -> arrow at y+3, id at y+2).
        // Torso NW: not "south" -> torso row y+2 (== id row), offset 3 (x+3) — no collision
        // with the id's default columns x+4/x+5, so no shift.
        assertEquals(ICON_TORSO_NW, buffer.get(3, 2).char)
        assertEquals(ICON_FACING_SW, buffer.get(3, 3).char)
        assertEquals("A", buffer.get(4, 2).char)
        assertEquals("1", buffer.get(5, 2).char)
    }

    @Test
    fun `id shifts left when facing SE and torso NE puts the torso arrow at x+5 in the id row`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.SE, Color.CYAN, torsoFacing = HexDirection.NE)

        // Facing SE: id row is y+2 (SE is "south" -> arrow at y+3, id at y+2).
        // Torso NE: not "south" -> torso row y+2 (== id row), offset 5 (x+5) — collides with
        // the id's default columns (x+4/x+5), so the id shifts left to x+3/x+4 instead.
        assertEquals(ICON_TORSO_NE, buffer.get(5, 2).char)
        assertEquals(ICON_FACING_SE, buffer.get(5, 3).char)
        assertEquals("A", buffer.get(3, 2).char)
        assertEquals("1", buffer.get(4, 2).char)
    }

    // --- skull relocation --------------------------------------------------------------------

    @Test
    fun `destroyed unit with a torso arrow at x+3 in the id row puts the skull in the arrow row`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(
            buffer, 0, 0, "A1", HexDirection.NW, Color.CYAN,
            torsoFacing = HexDirection.SW, isDestroyed = true,
        )

        // Id row (y+3) is fully occupied by the id at its default columns (x+4,x+5) and the
        // torso arrow (x+3) — no shift needed, but the row is still crowded.
        assertEquals(ICON_TORSO_SW, buffer.get(3, 3).char)
        assertEquals("A", buffer.get(4, 3).char)
        assertEquals("1", buffer.get(5, 3).char)
        // Skull relocates to the arrow row, avoiding the facing arrow's column (x+3).
        assertEquals(ICON_FACING_NW, buffer.get(3, 2).char)
        assertEquals(ICON_SKULL, buffer.get(4, 2).char)
    }

    @Test
    fun `destroyed unit whose torso arrow lands in the id row at x+5 still relocates the skull`() {
        val buffer = ScreenBuffer(10, 6)

        // Facing NE + torso SE: torso row (SE is "south" -> 3) equals the id row (NE is not
        // "south" -> id row 3) at column x+5, which collides with the id's default columns
        // (x+4/x+5), so the id shifts left to x+3/x+4 — yet the id row is still crowded (id +
        // torso arrow fill x+3..x+5), so the skull must still relocate to the arrow row rather
        // than collide with the torso arrow.
        UnitRenderer.render(
            buffer, 0, 0, "A1", HexDirection.NE, Color.CYAN,
            torsoFacing = HexDirection.SE, isDestroyed = true,
        )

        assertEquals("A", buffer.get(3, 3).char)
        assertEquals("1", buffer.get(4, 3).char)
        assertEquals(ICON_TORSO_SE, buffer.get(5, 3).char)
        // Facing arrow at x+5 in the arrow row; skull prefers x+4 there, which is free.
        assertEquals(ICON_FACING_NE, buffer.get(5, 2).char)
        assertEquals(ICON_SKULL, buffer.get(4, 2).char)
    }

    @Test
    fun `destroyed unit with an uncrowded id row puts the skull left of the id`() {
        val buffer = ScreenBuffer(10, 6)

        UnitRenderer.render(buffer, 0, 0, "A1", HexDirection.N, Color.CYAN, isDestroyed = true)

        assertEquals("A", buffer.get(4, 3).char)
        assertEquals("1", buffer.get(5, 3).char)
        assertEquals(ICON_SKULL, buffer.get(3, 3).char)
        assertEquals(ICON_FACING_N, buffer.get(4, 2).char)
    }
}
