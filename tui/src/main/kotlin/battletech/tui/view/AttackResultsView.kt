package battletech.tui.view

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.displayLabels
import battletech.tactical.attack.toHitBreakdownLabels
import battletech.tactical.dice.twoD6AtLeastProbability
import battletech.tui.game.phase.AttackResultsRender
import battletech.tui.hex.attackOutcomeIcon
import battletech.tui.hex.targetIcon
import tenter.screen.Canvas
import tenter.screen.Cell
import tenter.screen.UiRole
import tenter.view.TextCursor
import tenter.widget.ValueRow
import tenter.view.View

internal class AttackResultsView(private val data: AttackResultsRender) : View {

    override fun render(canvas: Canvas) {
        val content = TextCursor(canvas)

        val byAttacker = data.results.groupBy { it.attackerId }

        for ((attackerId, attackerResults) in byAttacker) {
            val attackerColor = playerColor(data.units.byId(attackerId).owner)
            content.writeLine(attackerId.value, Cell.Style(attackerColor))

            val byTarget = attackerResults.groupBy { it.targetId }

            for ((targetId, targetResults) in byTarget) {
                val targetLine = "${targetIcon()} ${targetId.value}"
                content.writeLine(targetLine, TEXT_PRIMARY_STYLE)

                for (result in targetResults) {
                    renderWeaponResult(content, result)
                }
            }
        }
    }

    private fun renderWeaponResult(content: TextCursor, result: AttackResult) {
        // Block 1: unified hit widget (weapon name, TN, success %, modifiers)
        val successChance = twoD6AtLeastProbability(result.targetNumber)
        // The TN and modifier breakdown are both observable (announced at the table), so
        // AttackResult itself is never redacted (see GameEvent.redactFor's KDoc) — but the
        // explicit "+N gunnery" label is still dropped for a foreign attacker so the skill
        // number isn't handed over for free. This is NOT a guarantee: targetNumber == gunnery
        // + sum(modifiers), so gunnery stays derivable by subtraction from what's shown here.
        val isOwnAttacker = data.units.byId(result.attackerId).owner == data.viewer
        val breakdown = if (isOwnAttacker) toHitBreakdownLabels(result.gunnery, result.modifiers) else result.modifiers.displayLabels()
        ValueRow.draw(content, "  ${result.weaponName}", hitChanceLabel(result.targetNumber, successChance), breakdown, UiRole.TEXT_PRIMARY)

        // Block 2: raw roll (left) + outcome (right-aligned, in its own color)
        val hit = result is AttackResult.Hit
        val toHit = result.toHitRoll
        val outcomeText = "${if (hit) "HIT" else "MISS"} ${attackOutcomeIcon(hit)}"
        val outcomeColor = if (hit) UiRole.SUCCESS else UiRole.DANGER
        val rollLine = "   ${diceRollLabel(toHit)}"
        content.writeRow(rollLine, outcomeText, TEXT_PRIMARY_STYLE, Cell.Style(outcomeColor))

        // Block 3: location + damage (hit only)
        if (result is AttackResult.ClusterHit) {
            val total = result.locationHits.sumOf { it.damage }
            content.writeRow("   ${result.missilesHit} missiles", "$total dmg", TEXT_PRIMARY_STYLE)
            val byLocation = LinkedHashMap<HitLocation, Int>()
            for (locationHit in result.locationHits) {
                byLocation.merge(locationHit.location, locationHit.damage, Int::plus)
            }
            for ((loc, dmg) in byLocation) {
                content.writeRow("   → ${hitLocationName(loc)}", "$dmg dmg", TEXT_PRIMARY_STYLE)
            }
        } else if (result is AttackResult.SingleHit) {
            val hitLoc = result.locationHits.first().location
            content.writeLine("   → ${hitLocationName(hitLoc)}   ${result.damageApplied} dmg", TEXT_PRIMARY_STYLE)
        }
    }

    private fun hitLocationName(location: HitLocation): String = when (location) {
        HitLocation.HEAD -> "Head"
        HitLocation.CENTER_TORSO -> "Center Torso"
        HitLocation.LEFT_TORSO -> "Left Torso"
        HitLocation.RIGHT_TORSO -> "Right Torso"
        HitLocation.LEFT_ARM -> "Left Arm"
        HitLocation.RIGHT_ARM -> "Right Arm"
        HitLocation.LEFT_LEG -> "Left Leg"
        HitLocation.RIGHT_LEG -> "Right Leg"
    }

    internal companion object {
        const val TITLE: String = "ATTACK RESULTS"

        private val TEXT_PRIMARY_STYLE = Cell.Style(UiRole.TEXT_PRIMARY)
    }
}
