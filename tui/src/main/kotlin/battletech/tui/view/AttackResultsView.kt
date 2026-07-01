package battletech.tui.view

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.toHitBreakdownLabels
import battletech.tactical.dice.twoD6AtLeastProbability
import battletech.tui.game.PanelId
import battletech.tui.game.phase.AttackResultsRender
import battletech.tui.hex.targetIcon
import battletech.tui.screen.CellWidth
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer

internal class AttackResultsView(private val data: AttackResultsRender) : View {

    companion object {
        val INDEX: Int = PanelId.ATTACK_RESULTS.index
        const val TITLE: String = "ATTACK RESULTS"
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        // One blank row for pixel parity with the decorator's y+1 inner-content start
        val content = ContentWriter(buffer, x, y + 1, width)

        val byAttacker = data.results.groupBy { it.attackerId }

        for ((attackerId, attackerResults) in byAttacker) {
            val attackerColor = playerColor(data.unitOwners[attackerId])
            content.writeln(attackerId.value, attackerColor)

            val byTarget = attackerResults.groupBy { it.targetId }

            for ((targetId, targetResults) in byTarget) {
                val targetLine = "${targetIcon()} ${targetId.value}"
                content.writeln(targetLine, Color.WHITE)

                for (result in targetResults) {
                    renderWeaponResult(content, result)
                }
            }
        }
    }

    private fun renderWeaponResult(content: ContentWriter, result: AttackResult) {
        // Block 1: unified hit widget (weapon name, TN, success %, modifiers)
        val successChance = twoD6AtLeastProbability(result.targetNumber)
        WeaponHitWidget.draw(content, "  ${result.weaponName}", result.targetNumber, successChance, toHitBreakdownLabels(result.gunnery, result.modifiers), Color.WHITE)

        // Block 2: raw roll + outcome (right-aligned, outcome overwritten in color)
        val toHit = result.toHitRoll
        val outcomeText = if (result.hit) "HIT" else "MISS"
        val outcomeColor = if (result.hit) Color.GREEN else Color.RED
        val rollLine = "  roll  ${diceRollLabel(toHit)}"
        val padding = (content.width - CellWidth.of(rollLine) - outcomeText.length).coerceAtLeast(1)
        content.writeln("$rollLine${" ".repeat(padding)}$outcomeText", Color.WHITE)
        val outcomeX = content.x + content.width - outcomeText.length
        if (outcomeX >= content.x) content.buffer.writeString(outcomeX, content.cy - 1, outcomeText, outcomeColor)

        // Block 3: location + damage (hit only)
        val locRoll = result.locationRoll
        val hitLoc = result.hitLocation
        if (result.hit && locRoll != null && hitLoc != null) {
            val locationName = hitLocationName(hitLoc)
            content.writeln("   → $locationName   ${result.damageApplied} dmg", Color.WHITE)
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

}
