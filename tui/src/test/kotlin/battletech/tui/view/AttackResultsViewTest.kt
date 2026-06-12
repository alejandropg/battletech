package battletech.tui.view

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.RangeBand
import battletech.tactical.dice.DiceRoll
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.UnitId
import battletech.tui.game.phase.AttackResultsRender
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
    )

    private fun aMissResult(
        weaponName: String = "PPC",
        gunnery: Int = 4,
        rangeModifier: Int = 0,
        rangeBand: RangeBand = RangeBand.SHORT,
        heatPenalty: Int = 0,
        secondaryPenalty: Int = 0,
        toHitRoll: DiceRoll = DiceRoll(1, 1),
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
    )

    private fun makeView(results: List<AttackResult>): AttackResultsView {
        val render = AttackResultsRender(
            results = results,
            unitNames = mapOf(attackerId to "Atlas", targetId to "Hunchback"),
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
    fun `miss renders weapon name and MISS but no location lines`() {
        val output = renderToString(listOf(aMissResult()))
        assertTrue(output.contains("PPC")) { "Expected weapon name in output" }
        assertTrue(output.contains("MISS")) { "Expected MISS outcome" }
        assertTrue(output.contains("to-hit")) { "Expected to-hit line" }
        assertFalse(output.contains("loc")) { "Expected no loc line for miss: $output" }
    }

    @Test
    fun `hit renders all five lines including location and damage`() {
        val output = renderToString(listOf(aHitResult()))
        assertTrue(output.contains("Med Laser")) { "Expected weapon name" }
        assertTrue(output.contains("HIT")) { "Expected HIT outcome" }
        assertTrue(output.contains("to-hit")) { "Expected to-hit line" }
        assertTrue(output.contains("loc")) { "Expected loc line for hit" }
        assertTrue(output.contains("Center Torso")) { "Expected hit location name" }
        assertTrue(output.contains("5 dmg")) { "Expected damage amount" }
    }

    @Test
    fun `TN line always includes range band for short range`() {
        val output = renderToString(listOf(aHitResult(rangeModifier = 0, rangeBand = RangeBand.SHORT)))
        assertTrue(output.contains("+0sht")) { "Expected +0sht range band: $output" }
    }

    @Test
    fun `TN line shows medium range band`() {
        val output = renderToString(listOf(aMissResult(rangeModifier = 2, rangeBand = RangeBand.MEDIUM)))
        assertTrue(output.contains("+2med")) { "Expected +2med range band: $output" }
    }

    @Test
    fun `TN line shows long range band`() {
        val output = renderToString(listOf(aMissResult(rangeModifier = 4, rangeBand = RangeBand.LONG)))
        assertTrue(output.contains("+4long")) { "Expected +4long range band: $output" }
    }

    @Test
    fun `TN line omits heat when heat penalty is zero`() {
        val output = renderToString(listOf(aHitResult(heatPenalty = 0)))
        assertFalse(output.contains("heat")) { "Expected no heat modifier when penalty is 0: $output" }
    }

    @Test
    fun `TN line includes heat when heat penalty is non-zero`() {
        val output = renderToString(listOf(aHitResult(heatPenalty = 2)))
        assertTrue(output.contains("+2heat")) { "Expected +2heat in TN line: $output" }
    }

    @Test
    fun `TN line includes secondary penalty when non-zero`() {
        val output = renderToString(listOf(aMissResult(secondaryPenalty = 1)))
        assertTrue(output.contains("+1sec")) { "Expected +1sec in TN line: $output" }
    }

    @Test
    fun `to-hit dice line shows both faces and total`() {
        val output = renderToString(listOf(aHitResult(toHitRoll = DiceRoll(4, 5))))
        assertTrue(output.contains("4+5")) { "Expected 4+5 in to-hit line: $output" }
        assertTrue(output.contains("= 9")) { "Expected = 9 total: $output" }
    }

    @Test
    fun `loc dice line shows both faces and total on hit`() {
        val output = renderToString(listOf(aHitResult(locationRoll = DiceRoll(3, 4))))
        assertTrue(output.contains("3+4")) { "Expected 3+4 in loc line: $output" }
        assertTrue(output.contains("= 7")) { "Expected = 7 total in loc line: $output" }
    }

    @Test
    fun `panel title is ATTACK RESULTS`() {
        val output = renderToString(listOf(aHitResult()))
        assertTrue(output.contains("ATTACK RESULTS")) { "Expected panel title ATTACK RESULTS" }
    }

    @Test
    fun `attacker name appears in output`() {
        val output = renderToString(listOf(aHitResult()))
        assertTrue(output.contains("Atlas")) { "Expected attacker name Atlas" }
    }

    @Test
    fun `target name appears as target line`() {
        val output = renderToString(listOf(aHitResult()))
        assertTrue(output.contains("> Hunchback")) { "Expected target line with Hunchback: $output" }
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
