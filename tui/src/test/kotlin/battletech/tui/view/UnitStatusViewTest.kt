package battletech.tui.view

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.MechLocation
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.AmmoType
import battletech.tactical.unit.ForeignUnit
import battletech.tactical.unit.HeatSink
import battletech.tactical.unit.HeatSinkType
import battletech.tactical.unit.HeatSource
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.PILOT_DEATH_THRESHOLD
import battletech.tactical.unit.PublicWeapon
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.Weapon
import battletech.tactical.unit.WeaponKind
import battletech.tactical.unit.WeaponModel
import battletech.tactical.unit.WeaponMountId
import battletech.tactical.unit.mechLayout
import battletech.tui.aUnit
import battletech.tui.anArmorLayout
import battletech.tui.hex.ammoIcon
import battletech.tui.hex.destroyedIcon
import battletech.tui.hex.emptyCircleIcon
import battletech.tui.hex.filledCircleIcon
import battletech.tui.hex.infinityIcon
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UnitStatusViewTest {

    /** Render via decorator at (0,0) — pixel-parity regression guard for box/coordinates. */
    private fun renderDecorated(view: UnitStatusView, width: Int = 28, height: Int = 30): ScreenBuffer {
        val decorated = ScrollablePanelView(
            index = UnitStatusView.INDEX,
            title = UnitStatusView.TITLE,
            content = view,
            scrollOffset = 0,
        )
        val buffer = ScreenBuffer(width, height)
        decorated.render(buffer, 0, 0, width, height)
        return buffer
    }

    private fun aForeignUnit(
        name: String = "Hunchback",
        walkingMP: Int = 4,
        runningMP: Int = 6,
        jumpMP: Int = 0,
        weapons: List<PublicWeapon> = listOf(PublicWeapon("AC/20", WeaponMountId(0))),
    ): ForeignUnit = ForeignUnit(
        id = UnitId("u1"),
        owner = PlayerId.PLAYER_2,
        name = name,
        tonnage = 50,
        position = HexCoordinates(0, 0),
        facing = HexDirection.N,
        torsoFacing = HexDirection.N,
        armor = anArmorLayout(),
        walkingMP = walkingMP,
        runningMP = runningMP,
        jumpMP = jumpMP,
        weapons = weapons,
        isProne = false,
        isShutdown = false,
        isDestroyed = false,
        isPilotConscious = true,
        movementThisTurn = MovementThisTurn.Stationary,
    )

    @Test
    fun `renders unit name`() {
        val unit = aUnit(name = "Atlas")
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 14)

        val text = (2 until 7).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("Atlas", text)
    }

    @Test
    fun `renders gunnery and piloting skills`() {
        val unit = aUnit()
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 14)

        val gunnery = (2 until 26).map { buffer.get(it, 6).char }.joinToString("").trim()
        assertEquals("Gunnery  : 4", gunnery)
        val piloting = (2 until 26).map { buffer.get(it, 7).char }.joinToString("").trim()
        assertEquals("Piloting : 5", piloting)
    }

    @Test
    fun `renders undamaged pilot hits track`() {
        val unit = aUnit() // pilotHits = 0
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 14)

        val line = (2 until 26).map { buffer.get(it, 5).char }.joinToString("")
        assertTrue(line.contains("Hits"))
        assertEquals(0, line.split(filledCircleIcon()).size - 1)
        assertEquals(6, line.split(emptyCircleIcon()).size - 1)
    }

    @Test
    fun `renders damaged pilot hits track`() {
        val unit = aUnit().copy(pilotHits = 2)
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 14)

        val line = (2 until 26).map { buffer.get(it, 5).char }.joinToString("")
        assertEquals(2, line.split(filledCircleIcon()).size - 1)
        assertEquals(4, line.split(emptyCircleIcon()).size - 1)
        // First hit dot is red, matching the destroyed-slot convention used elsewhere in this panel.
        val firstDotCol = (2 until 26).first { buffer.get(it, 5).char == filledCircleIcon() }
        assertEquals(Color.RED, buffer.get(firstDotCol, 5).fg)
    }

    @Test
    fun `renders skull in place of final hit dot when pilot dies`() {
        val unit = aUnit().copy(pilotHits = PILOT_DEATH_THRESHOLD)
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 14)

        val line = (2 until 26).map { buffer.get(it, 5).char }.joinToString("")
        assertEquals(1, line.split(destroyedIcon()).size - 1)
        assertEquals(PILOT_DEATH_THRESHOLD - 1, line.split(filledCircleIcon()).size - 1)
        assertEquals(0, line.split(emptyCircleIcon()).size - 1)
        val skullCol = (2 until 26).first { buffer.get(it, 5).char == destroyedIcon() }
        assertEquals(Color.RED, buffer.get(skullCol, 5).fg)
    }

    @Test
    fun `renders heat bar`() {
        val unit = aUnit()
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        val line = (2 until 26).map { buffer.get(it, 14).char }.joinToString("")
        assertTrue(line.contains("[" + "░".repeat(20) + "]30"))
    }

    @Test
    fun `renders current heat label above bar`() {
        val unit = aUnit()
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        val label = (2 until 26).map { buffer.get(it, 13).char }.joinToString("")
        assertTrue(label.contains("Current"))
        val bar = (2 until 26).map { buffer.get(it, 14).char }.joinToString("")
        assertTrue(bar.contains("]30"))
    }

    @Test
    fun `renders STS dissipation bar with suffix`() {
        val unit = aUnit(heatSink = HeatSink(HeatSinkType.STS, 10))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        val line = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(line.contains("[" + "░".repeat(10) + "]STS 10"))
        val oldSinkLine = (2 until 26).map { buffer.get(it, 13).char }.joinToString("")
        assertFalse(oldSinkLine.contains("STS:"))
    }

    @Test
    fun `renders DTS dissipation bar with units and dissipation suffix`() {
        val unit = aUnit(heatSink = HeatSink(HeatSinkType.DTS, 10))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        val line = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(line.contains("]DTS 10(20)"))
    }

    @Test
    fun `renders heat value paired to bar fill without max`() {
        // 15 of 30 -> barWidth=20 -> 10 filled cells. cx=2, anchorCol=12, "15" starts at col 11.
        val unit = aUnit().copy(currentHeat = 15)
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        // last digit sits under the last filled cell (col 12); value row is 15
        assertEquals("1", buffer.get(11, 15).char)
        assertEquals("5", buffer.get(12, 15).char)
    }

    @Test
    fun `renders zero heat value under first bar cell`() {
        val unit = aUnit() // currentHeat = 0
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        // cx=2, "[" prefix -> first cell at col 3
        assertEquals("0", buffer.get(3, 15).char)
    }

    @Test
    fun `renders with no unit selected shows empty`() {
        val view = UnitStatusView(subject = null)
        val buffer = renderDecorated(view, height = 14)

        val text = (2 until 18).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("No unit selected", text.trim())
    }

    @Test
    fun `renders box border`() {
        val view = UnitStatusView(subject = null)
        val buffer = renderDecorated(view, height = 14)

        assertEquals("╭", buffer.get(0, 0).char)
        assertEquals("╮", buffer.get(27, 0).char)
        assertEquals("╰", buffer.get(0, 13).char)
        assertEquals("╯", buffer.get(27, 13).char)
    }

    @Test
    fun `renders walk and run movement points`() {
        val unit = aUnit(walkingMP = 3, runningMP = 5)
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 14)

        val line = (2 until 26).map { buffer.get(it, 10).char }.joinToString("")
        assertTrue(line.contains("Walk"))
        assertTrue(line.contains("Run"))
        assertTrue(line.contains("3"))
        assertTrue(line.contains("5"))
    }

    @Test
    fun `renders jump only when nonzero`() {
        val unit = aUnit()
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 14)

        val jumpRow = (2 until 26).map { buffer.get(it, 11).char }.joinToString("")
        assertFalse(jumpRow.contains("Jump"))
        val heatHeader = (2 until 26).map { buffer.get(it, 12).char }.joinToString("")
        assertTrue(heatHeader.contains("HEAT"))
    }

    @Test
    fun `renders heat bar in red when overheated`() {
        val unit = aUnit().copy(currentHeat = 22)
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        assertEquals(Color.RED, buffer.get(2, 14).fg)
    }

    @Test
    fun `renders armor section header when armor is present`() {
        val unit = aUnit(armor = anArmorLayout())
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 30)

        // Shifted down by: pilot hits line, "Current" label, current bar (2 rows),
        // diss bar (2 rows), "Projected" label, projected bar (2 rows), blank.
        val row22 = (2 until 26).map { buffer.get(it, 22).char }.joinToString("")
        assertTrue(row22.contains("ARMOR"))
    }

    @Test
    fun `renders head armor value in cyan`() {
        val unit = aUnit(armor = anArmorLayout(head = 9))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 30)

        // HD value row: cy=23, "HD: 9" starts at cx+9=11
        val line = (2 until 26).map { buffer.get(it, 23).char }.joinToString("")
        assertTrue(line.contains("HD"))
        assertTrue(line.contains("9"))
        assertEquals(Color.CYAN, buffer.get(11, 23).fg) // 'H' of "HD: 9"
    }

    @Test
    fun `renders center torso armor in bright yellow`() {
        val unit = aUnit(armor = anArmorLayout(centerTorso = 47))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 30)

        // CT row: cy=24, "CT:47" starts at cx+9=11
        val line = (2 until 26).map { buffer.get(it, 24).char }.joinToString("")
        assertTrue(line.contains("CT"))
        assertTrue(line.contains("47"))
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(11, 24).fg) // 'C' of "CT:47"
    }

    @Test
    fun `renders torso rear values in default color`() {
        val unit = aUnit(armor = anArmorLayout(centerTorsoRear = 8))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 30)

        // Rear row: cy=25, CT rear "r: 8" starts at cx+10=12
        val line = (2 until 26).map { buffer.get(it, 25).char }.joinToString("")
        assertTrue(line.contains("r"))
        assertEquals(Color.DEFAULT, buffer.get(12, 25).fg) // 'r' of CT rear
    }

    @Test
    fun `renders arm and leg armor values`() {
        val unit = aUnit(armor = anArmorLayout(leftArm = 34, rightArm = 34, leftLeg = 41, rightLeg = 41))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 30)

        // Arms row: cy=26
        val armsRow = (2 until 26).map { buffer.get(it, 26).char }.joinToString("")
        assertTrue(armsRow.contains("LA"))
        assertTrue(armsRow.contains("RA"))
        // Legs row: cy=27
        val legsRow = (2 until 26).map { buffer.get(it, 27).char }.joinToString("")
        assertTrue(legsRow.contains("LL"))
        assertTrue(legsRow.contains("RL"))
        assertTrue(legsRow.contains("41"))
    }

    @Test
    fun `renders committed heat sources in default color`() {
        val unit = aUnit().copy(heatGeneratedThisTurn = listOf(HeatSource("Running", 2)))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        // Source line under the bar and the heat value line (row 16).
        val line = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(line.contains("Running +2"))
        assertEquals(Color.DEFAULT, buffer.get(2, 16).fg)
    }

    @Test
    fun `renders pending heat preview in gray`() {
        val unit = aUnit()
        val view = UnitStatusView(unit, pendingHeat = listOf(HeatSource("Walking", 1)))
        val buffer = renderDecorated(view, height = 20)

        val line = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(line.contains("Walking +1"))
        assertEquals(Color.GRAY, buffer.get(2, 16).fg)
    }

    @Test
    fun `renders dissipated heat capped at dissipation capacity`() {
        // current 12, +2 running, dissipation 10 -> dissipated = min(14, 10) = 10 -> bar fully filled
        val unit = aUnit().copy(
            currentHeat = 12,
            heatGeneratedThisTurn = listOf(HeatSource("Running", 2)),
        )
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        // one source at row 16, diss bar at row 17
        val dissBar = (2 until 26).map { buffer.get(it, 17).char }.joinToString("")
        assertTrue(dissBar.contains("[" + "█".repeat(10) + "]STS 10"))
        assertEquals("1", buffer.get(11, 18).char)
        assertEquals("0", buffer.get(12, 18).char)
    }

    @Test
    fun `renders dissipated heat from pending preview`() {
        // pending 5, DTS 10 -> dissipation 20, dissipated = min(5, 20) = 5, width 10 -> 2 filled
        val view = UnitStatusView(
            aUnit(heatSink = HeatSink(HeatSinkType.DTS, 10)),
            pendingHeat = listOf(HeatSource("Walking", 5)),
        )
        val buffer = renderDecorated(view, height = 20)

        // pending source at row 16, diss bar at row 17
        val dissBar = (2 until 26).map { buffer.get(it, 17).char }.joinToString("")
        assertTrue(dissBar.contains("[██░░░░░░░░]DTS 10(20)"))
        assertEquals("5", buffer.get(4, 18).char)
    }

    @Test
    fun `renders dissipation bar in red when at capacity`() {
        // same fixture as capped test: dissipated 10 >= 10*0.7 -> RED
        val unit = aUnit().copy(
            currentHeat = 12,
            heatGeneratedThisTurn = listOf(HeatSource("Running", 2)),
        )
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        assertEquals(Color.RED, buffer.get(2, 17).fg)
    }

    @Test
    fun `renders zero dissipation heat sink without error`() {
        val unit = aUnit(heatSink = HeatSink(HeatSinkType.STS, 0))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 20)

        val dissBar = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(dissBar.contains("[" + "░".repeat(10) + "]STS 0"))
    }

    @Test
    fun `renders projected end-of-turn heat`() {
        // current 12, +2 running, dissipates 10 -> projected 4
        val unit = aUnit().copy(currentHeat = 12, heatGeneratedThisTurn = listOf(HeatSource("Running", 2)))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 23)

        // "Current"(13), current bar(14), current value(15), one source(16), diss bar(17),
        // diss value(18), "Projected"(19), projected bar(20), projected value(21)
        val projectedLabel = (2 until 26).map { buffer.get(it, 19).char }.joinToString("")
        assertTrue(projectedLabel.contains("Projected"))
        val projectedValueRow = (2 until 26).map { buffer.get(it, 21).char }.joinToString("")
        assertTrue(projectedValueRow.contains("4"))
    }

    private fun rowContaining(buffer: ScreenBuffer, height: Int, text: String): Int {
        for (row in 0 until height) {
            val line = (2 until 26).map { buffer.get(it, row).char }.joinToString("")
            if (line.contains(text)) return row
        }
        error("No row contains \"$text\"")
    }

    @Test
    fun `renders ammo count with ammunition icon right-aligned`() {
        val weapon = Weapon(
            model = WeaponModel(
                name = "AC/20", damage = 20, heat = 7,
                shortRange = 3, mediumRange = 6, longRange = 9, kind = WeaponKind.Ballistic(AmmoType.AC20),
            ),
            mountId = WeaponMountId(0),
            location = MechLocation.RIGHT_TORSO,
        )
        val layout = mechLayout { ammo(MechLocation.RIGHT_TORSO, AmmoType.AC20, 1) }.layout
        val unit = aUnit(weapons = listOf(weapon), criticalLayout = layout)
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 60)

        val row = rowContaining(buffer, 60, "AC/20")
        val line = (2 until 26).map { buffer.get(it, row).char }.joinToString("")
        assertTrue(line.contains("5"))
        assertEquals(ammoIcon(), buffer.get(25, row).char)
    }

    @Test
    fun `renders all-intact critical hit dots as empty circles`() {
        val unit = aUnit(armor = anArmorLayout())
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 40)

        val row = rowContaining(buffer, 40, "Engine")
        val line = (2 until 26).map { buffer.get(it, row).char }.joinToString("")
        assertEquals(3, line.split(emptyCircleIcon()).size - 1)
        assertEquals(0, line.split(filledCircleIcon()).size - 1)
    }

    @Test
    fun `renders destroyed engine crits as filled red dots`() {
        val unit = aUnit(armor = anArmorLayout()).copy(
            criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(0, 1)),
        )
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 40)

        val row = rowContaining(buffer, 40, "Engine")
        val line = (2 until 26).map { buffer.get(it, row).char }.joinToString("")
        val filledCount = line.split(filledCircleIcon()).size - 1
        assertEquals(2, filledCount)

        // First dot column should be red (destroyed slot 0 is an Engine slot in CENTER_TORSO).
        val firstDotCol = (2 until 26).first { buffer.get(it, row).char == filledCircleIcon() }
        assertEquals(Color.RED, buffer.get(firstDotCol, row).fg)
    }

    @Test
    fun `renders destroyed gyro crits independently of engine crits`() {
        val unit = aUnit(armor = anArmorLayout()).copy(
            // CENTER_TORSO indices 3,4,5,6 are Gyro slots per the standard framework.
            criticalHits = mapOf(MechLocation.CENTER_TORSO to setOf(3)),
        )
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 40)

        val engineRow = rowContaining(buffer, 40, "Engine")
        val engineLine = (2 until 26).map { buffer.get(it, engineRow).char }.joinToString("")
        assertEquals(0, engineLine.split(filledCircleIcon()).size - 1)

        val gyroRow = rowContaining(buffer, 40, "Gyro")
        val gyroLine = (2 until 26).map { buffer.get(it, gyroRow).char }.joinToString("")
        assertEquals(1, gyroLine.split(filledCircleIcon()).size - 1)
    }

    @Test
    fun `renders infinity icon right-aligned for weapons without ammo`() {
        val weapon = Weapon(
            model = WeaponModel(
                name = "Medium Laser", damage = 5, heat = 3,
                shortRange = 3, mediumRange = 6, longRange = 9, kind = WeaponKind.Energy,
            ),
            mountId = WeaponMountId(0),
            location = MechLocation.CENTER_TORSO,
        )
        val unit = aUnit(weapons = listOf(weapon))
        val view = UnitStatusView(unit)
        val buffer = renderDecorated(view, height = 60)

        val row = rowContaining(buffer, 60, "Medium Laser")
        val line = (2 until 26).map { buffer.get(it, row).char }.joinToString("")
        assertFalse(line.contains("["))
        assertEquals(infinityIcon(), buffer.get(25, row).char)
    }

    @Test
    fun `renders public subject unit name`() {
        val view = UnitStatusView(aForeignUnit(name = "Hunchback"))
        val buffer = renderDecorated(view)

        val line = (2 until 11).map { buffer.get(it, 2).char }.joinToString("")
        assertEquals("Hunchback", line)
        assertEquals(Color.BRIGHT_YELLOW, buffer.get(2, 2).fg)
    }

    @Test
    fun `renders public subject MOVEMENT section with walk and run values`() {
        val view = UnitStatusView(aForeignUnit(walkingMP = 4, runningMP = 6))
        val buffer = renderDecorated(view)

        val headerRow = (2 until 26).map { buffer.get(it, 4).char }.joinToString("")
        assertTrue(headerRow.contains("MOVEMENT"))
        val walkRunRow = (2 until 26).map { buffer.get(it, 5).char }.joinToString("")
        assertTrue(walkRunRow.contains("Walk"))
        assertTrue(walkRunRow.contains("Run"))
        assertTrue(walkRunRow.contains("4"))
        assertTrue(walkRunRow.contains("6"))
    }

    @Test
    fun `renders public subject jump movement points only when nonzero`() {
        val viewWithoutJump = UnitStatusView(aForeignUnit(jumpMP = 0))
        val bufferWithoutJump = renderDecorated(viewWithoutJump)
        val jumpRow = (2 until 26).map { bufferWithoutJump.get(it, 6).char }.joinToString("")
        assertFalse(jumpRow.contains("Jump"))

        val viewWithJump = UnitStatusView(aForeignUnit(jumpMP = 5))
        val bufferWithJump = renderDecorated(viewWithJump)
        val jumpRowPresent = (2 until 26).map { bufferWithJump.get(it, 6).char }.joinToString("")
        assertTrue(jumpRowPresent.contains("Jump"))
        assertTrue(jumpRowPresent.contains("5"))
    }

    @Test
    fun `renders public subject ARMOR section with HD CT and LL values`() {
        val view = UnitStatusView(aForeignUnit())
        val buffer = renderDecorated(view)

        val armorHeader = (2 until 26).map { buffer.get(it, 7).char }.joinToString("")
        assertTrue(armorHeader.contains("ARMOR"))
        val hdRow = (2 until 26).map { buffer.get(it, 8).char }.joinToString("")
        assertTrue(hdRow.contains("HD"))
        assertTrue(hdRow.contains("9"))
        val ctRow = (2 until 26).map { buffer.get(it, 9).char }.joinToString("")
        assertTrue(ctRow.contains("CT"))
        assertTrue(ctRow.contains("47"))
        val llRow = (2 until 26).map { buffer.get(it, 12).char }.joinToString("")
        assertTrue(llRow.contains("LL"))
        assertTrue(llRow.contains("41"))
    }

    @Test
    fun `renders public subject WEAPONS section with weapon names`() {
        val view = UnitStatusView(
            aForeignUnit(weapons = listOf(PublicWeapon("AC/20", WeaponMountId(0)), PublicWeapon("Medium Laser", WeaponMountId(1)))),
        )
        val buffer = renderDecorated(view)

        val weaponsHeader = (2 until 26).map { buffer.get(it, 14).char }.joinToString("")
        assertTrue(weaponsHeader.contains("WEAPONS"))
        val weapon1Row = (2 until 26).map { buffer.get(it, 15).char }.joinToString("")
        assertTrue(weapon1Row.contains("AC/20"))
        val weapon2Row = (2 until 26).map { buffer.get(it, 16).char }.joinToString("")
        assertTrue(weapon2Row.contains("Medium Laser"))
    }

    @Test
    fun `does not render private-only sections for public subject`() {
        val view = UnitStatusView(aForeignUnit())
        val buffer = renderDecorated(view, height = 30)

        val allText = (0 until 30).flatMap { row ->
            (0 until 28).map { col -> buffer.get(col, row).char }
        }.joinToString("")
        assertFalse(allText.contains("PILOT"))
        assertFalse(allText.contains("Gunnery"))
        assertFalse(allText.contains("HEAT"))
        assertFalse(allText.contains("Internal Structure"))
        assertFalse(allText.contains("Critical hit points"))
    }
}
