package battletech.tui.view

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.RangeBand
import battletech.tactical.attack.ToHitModifier
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId
import battletech.tui.game.phase.AttackResultsRender
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
        damageApplied: Int = 5,
        modifiers: List<ToHitModifier> = emptyList(),
    ): AttackResult = AttackResult(
        attackerId = attackerId,
        targetId = targetId,
        weaponName = weaponName,
        hit = true,
        hitLocation = hitLocation,
        damageApplied = damageApplied,
        targetNumber = gunnery + rangeModifier + heatPenalty + secondaryPenalty,
        roll = toHitRoll.total,
        toHitRoll = toHitRoll,
        locationRoll = locationRoll,
        gunnery = gunnery,
        rangeModifier = rangeModifier,
        rangeBand = rangeBand,
        heatPenalty = heatPenalty,
        secondaryPenalty = secondaryPenalty,
        modifiers = modifiers,
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
    ): AttackResult = AttackResult(
        attackerId = attackerId,
        targetId = targetId,
        weaponName = weaponName,
        hit = false,
        hitLocation = null,
        damageApplied = 0,
        targetNumber = gunnery + rangeModifier + heatPenalty + secondaryPenalty,
        roll = toHitRoll.total,
        toHitRoll = toHitRoll,
        locationRoll = null,
        gunnery = gunnery,
        rangeModifier = rangeModifier,
        rangeBand = rangeBand,
        heatPenalty = heatPenalty,
        secondaryPenalty = secondaryPenalty,
        modifiers = modifiers,
    )

    private fun makeView(results: List<AttackResult>): AttackResultsView {
        val render = AttackResultsRender(
            results = results,
            unitOwners = mapOf(attackerId to PlayerId.PLAYER_1, targetId to PlayerId.PLAYER_2),
        )
        return AttackResultsView(render)
    }

    /** Renders via the decorator — pixel-parity regression guard for box/title/coordinates. */
    private fun renderToString(results: List<AttackResult>, width: Int = 34, height: Int = 30): String {
        val view = makeView(results)
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
            listOf(aHitResult(modifiers = listOf(ToHitModifier("med", 2), ToHitModifier("heat", 1)))),
        )
        assertTrue(output.contains("+2 med")) { "Expected +2 med modifier line: $output" }
        assertTrue(output.contains("+1 heat")) { "Expected +1 heat modifier line: $output" }
    }

    @Test
    fun `breakdown includes the gunnery base line`() {
        val output = renderToString(listOf(aHitResult(gunnery = 4)))
        assertTrue(output.contains("+4 gunnery")) { "Expected gunnery base line in output: $output" }
    }

    @Test
    fun `zero-amount modifiers are omitted from the list`() {
        val output = renderToString(
            listOf(aHitResult(modifiers = listOf(ToHitModifier("med", 2), ToHitModifier("heat", 0)))),
        )
        assertTrue(output.contains("+2 med")) { "Expected non-zero modifier shown: $output" }
        assertFalse(output.contains("heat")) { "Expected zero-amount modifier omitted: $output" }
    }

    @Test
    fun `roll line shows both faces and total`() {
        val output = renderToString(listOf(aHitResult(toHitRoll = DiceRoll(4, 5))))
        assertTrue(output.contains("4+5")) { "Expected 4+5 in roll line: $output" }
        assertTrue(output.contains("= 9")) { "Expected = 9 total: $output" }
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
}
