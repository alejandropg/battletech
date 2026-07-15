package battletech.tui.view

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.LocationHit
import battletech.tactical.attack.RangeBand
import battletech.tactical.attack.ToHitFactor
import battletech.tactical.attack.ToHitModifier
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId
import battletech.tui.game.phase.AttackResultsRender
import battletech.tui.hex.diceIcon
import battletech.tui.hex.targetIcon
import battletech.tui.screen.ScreenBuffer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AttackResultsViewTest {

    private val attackerId = UnitId("attacker")
    private val targetId = UnitId("target")

    private fun aHitResult(
        weaponName: String = "Med Laser",
        gunnery: Int = 4,
        rangeModifier: Int = 0,
        rangeBand: RangeBand = RangeBand.SHORT,
        heatPenalty: Int = 0,
        secondaryPenalty: Int = 0,
        toHitRoll: DiceRoll = DiceRoll(4, 5),
        locationRoll: DiceRoll = DiceRoll(3, 4),
        hitLocation: HitLocation = HitLocation.CENTER_TORSO,
        damage: Int = 5,
        modifiers: List<ToHitModifier> = emptyList(),
    ): AttackResult = AttackResult.SingleHit(
        attackerId = attackerId,
        targetId = targetId,
        weaponName = weaponName,
        targetNumber = gunnery + rangeModifier + heatPenalty + secondaryPenalty,
        toHitRoll = toHitRoll,
        gunnery = gunnery,
        rangeBand = rangeBand,
        modifiers = modifiers,
        locationHits = listOf(LocationHit(hitLocation, damage, locationRoll)),
    )

    private fun aMissResult(
        weaponName: String = "PPC",
        gunnery: Int = 4,
        rangeModifier: Int = 0,
        rangeBand: RangeBand = RangeBand.SHORT,
        heatPenalty: Int = 0,
        secondaryPenalty: Int = 0,
        toHitRoll: DiceRoll = DiceRoll(1, 1),
        modifiers: List<ToHitModifier> = emptyList(),
    ): AttackResult = AttackResult.Miss(
        attackerId = attackerId,
        targetId = targetId,
        weaponName = weaponName,
        targetNumber = gunnery + rangeModifier + heatPenalty + secondaryPenalty,
        toHitRoll = toHitRoll,
        gunnery = gunnery,
        rangeBand = rangeBand,
        modifiers = modifiers,
    )

    private fun aClusterHitResult(
        weaponName: String = "LRM 20",
        gunnery: Int = 4,
        rangeModifier: Int = 0,
        rangeBand: RangeBand = RangeBand.SHORT,
        heatPenalty: Int = 0,
        secondaryPenalty: Int = 0,
        toHitRoll: DiceRoll = DiceRoll(4, 5),
        missilesHit: Int = 16,
        locationHits: List<LocationHit> = listOf(
            LocationHit(HitLocation.CENTER_TORSO, 5, DiceRoll(3, 4)),
            LocationHit(HitLocation.RIGHT_TORSO, 5, DiceRoll(3, 4)),
            LocationHit(HitLocation.CENTER_TORSO, 5, DiceRoll(3, 4)),
            LocationHit(HitLocation.LEFT_ARM, 1, DiceRoll(3, 4)),
        ),
        modifiers: List<ToHitModifier> = emptyList(),
    ): AttackResult = AttackResult.ClusterHit(
        attackerId = attackerId,
        targetId = targetId,
        weaponName = weaponName,
        targetNumber = gunnery + rangeModifier + heatPenalty + secondaryPenalty,
        toHitRoll = toHitRoll,
        gunnery = gunnery,
        rangeBand = rangeBand,
        modifiers = modifiers,
        locationHits = locationHits,
        missilesHit = missilesHit,
    )

    private fun makeView(results: List<AttackResult>, viewer: PlayerId? = PlayerId.PLAYER_1): AttackResultsView {
        val render = AttackResultsRender(
            results = results,
            unitOwners = mapOf(attackerId to PlayerId.PLAYER_1, targetId to PlayerId.PLAYER_2),
            viewer = viewer,
        )
        return AttackResultsView(render)
    }

    /** Renders via the decorator — pixel-parity regression guard for box/title/coordinates. */
    private fun renderToString(results: List<AttackResult>, width: Int = 34, height: Int = 30, viewer: PlayerId? = PlayerId.PLAYER_1): String {
        val view = makeView(results, viewer)
        val decorated = ScrollablePanelView(
            index = AttackResultsView.INDEX,
            title = AttackResultsView.TITLE,
            content = view,
            scrollOffset = 0,
        )
        val buffer = ScreenBuffer(width, height)
        decorated.render(buffer, 0, 0, width, height)
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
    fun `miss renders weapon name and MISS but no location or damage`() {
        val output = renderToString(listOf(aMissResult()))
        assertTrue(output.contains("PPC")) { "Expected weapon name in output" }
        assertTrue(output.contains("MISS")) { "Expected MISS outcome" }
        assertFalse(output.contains("dmg")) { "Expected no damage line for miss: $output" }
        assertFalse(output.contains("→")) { "Expected no location line for miss: $output" }
    }

    @Test
    fun `hit renders weapon name, HIT, location and damage`() {
        val output = renderToString(listOf(aHitResult()))
        assertTrue(output.contains("Med Laser")) { "Expected weapon name" }
        assertTrue(output.contains("HIT")) { "Expected HIT outcome" }
        assertTrue(output.contains("Center Torso")) { "Expected hit location name" }
        assertTrue(output.contains("5 dmg")) { "Expected damage amount" }
    }

    @Test
    fun `weapon line shows target number and success percent`() {
        // gunnery 4, no modifiers -> TN 4 -> 92% on 2d6
        val output = renderToString(listOf(aHitResult(gunnery = 4, rangeModifier = 0)))
        assertTrue(output.contains("92%")) { "Expected success percent for TN 4: $output" }
    }

    @Test
    fun `modifiers render one per line under the weapon`() {
        val output = renderToString(
            listOf(
                aHitResult(
                    modifiers = listOf(
                        ToHitModifier(ToHitFactor.RANGE, "med", 2),
                        ToHitModifier(ToHitFactor.HEAT, "heat", 1),
                    ),
                ),
            ),
        )
        assertTrue(output.contains("+2 med")) { "Expected +2 med modifier line: $output" }
        assertTrue(output.contains("+1 heat")) { "Expected +1 heat modifier line: $output" }
    }

    @Test
    fun `breakdown includes the gunnery base line for the viewer's own attacker`() {
        val output = renderToString(listOf(aHitResult(gunnery = 4)), viewer = PlayerId.PLAYER_1)
        assertTrue(output.contains("+4 gunnery")) { "Expected gunnery base line in output: $output" }
    }

    @Test
    fun `breakdown omits the gunnery base line for a foreign attacker, though it stays derivable from TN and modifiers`() {
        // attackerId is owned by PLAYER_1 (see makeView); viewing as PLAYER_2 makes it foreign.
        val output = renderToString(
            listOf(aHitResult(gunnery = 4, modifiers = listOf(ToHitModifier(ToHitFactor.RANGE, "med", 2)))),
            viewer = PlayerId.PLAYER_2,
        )
        assertFalse(output.contains("gunnery")) { "Expected no gunnery label for a foreign attacker: $output" }
        assertTrue(output.contains("+2 med")) { "Modifiers stay visible for a foreign attacker: $output" }
    }

    @Test
    fun `zero-amount modifiers are omitted from the list`() {
        val output = renderToString(
            listOf(
                aHitResult(
                    modifiers = listOf(
                        ToHitModifier(ToHitFactor.RANGE, "med", 2),
                        ToHitModifier(ToHitFactor.HEAT, "heat", 0),
                    ),
                ),
            ),
        )
        assertTrue(output.contains("+2 med")) { "Expected non-zero modifier shown: $output" }
        assertFalse(output.contains("heat")) { "Expected zero-amount modifier omitted: $output" }
    }

    @Test
    fun `roll line shows both faces and total`() {
        val output = renderToString(listOf(aHitResult(toHitRoll = DiceRoll(4, 5))))
        assertTrue(output.contains("${diceIcon(4)}+${diceIcon(5)}=9")) { "Expected dice icons and total in roll line: $output" }
    }

    @Test
    fun `panel title is ATTACK RESULTS`() {
        val output = renderToString(listOf(aHitResult()))
        assertTrue(output.contains("ATTACK RESULTS")) { "Expected panel title ATTACK RESULTS" }
    }

    @Test
    fun `attacker id appears in output`() {
        val output = renderToString(listOf(aHitResult()))
        assertTrue(output.contains("attacker")) { "Expected attacker id in output" }
    }

    @Test
    fun `target id appears as target line`() {
        val output = renderToString(listOf(aHitResult()))
        assertTrue(output.contains("${targetIcon()} target")) { "Expected target line with target id: $output" }
    }

    @Test
    fun `scrolled view shows later lines when content overflows`() {
        // Build enough results to exceed a small panel height
        val results = (1..5).map { i ->
            aHitResult(weaponName = "Weapon$i")
        }
        val view = makeView(results)
        val width = 34
        val height = 10
        val decorated = ScrollablePanelView(
            index = AttackResultsView.INDEX,
            title = AttackResultsView.TITLE,
            content = view,
            scrollOffset = 15,
        )
        val buffer = ScreenBuffer(width, height)
        decorated.render(buffer, 0, 0, width, height)
        val output = buildString {
            for (row in 0 until height) {
                for (col in 0 until width) append(buffer.get(col, row).char)
                appendLine()
            }
        }
        // With offset=15, lines before row 15 are scrolled away; later weapon entries are visible
        assertTrue(output.contains("Weapon")) { "Expected weapon lines after scroll: $output" }
    }

    @Test
    fun `cluster hit renders missiles summary header with total damage`() {
        val output = renderToString(listOf(aClusterHitResult()))
        assertTrue(output.contains("16 missiles")) { "Expected missiles summary header: $output" }
        assertTrue(output.contains("16 dmg")) { "Expected total damage: $output" }
    }

    @Test
    fun `cluster hit aggregates repeated locations and lists distinct ones separately`() {
        val output = renderToString(listOf(aClusterHitResult()))
        assertTrue(output.contains("Center Torso")) { "Expected Center Torso location line: $output" }
        assertTrue(output.contains("10 dmg")) { "Expected aggregated Center Torso damage: $output" }
        assertTrue(output.contains("Left Arm")) { "Expected Left Arm location line: $output" }
        assertTrue(output.contains("1 dmg")) { "Expected Left Arm damage: $output" }
        assertTrue(output.contains("Right Torso")) { "Expected Right Torso location line: $output" }
        assertTrue(output.contains("5 dmg")) { "Expected Right Torso damage: $output" }
    }

    @Test
    fun `single-location hit output remains unchanged`() {
        val output = renderToString(listOf(aHitResult()))
        assertTrue(output.contains("Center Torso")) { "Expected hit location name" }
        assertTrue(output.contains("5 dmg")) { "Expected damage amount" }
        assertFalse(output.contains("missiles")) { "Expected no missiles summary for single-location hit: $output" }
    }
}
