package battletech.tui.view

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.RangeBand
import battletech.tactical.model.PlayerId
import battletech.tui.game.PanelId
import battletech.tui.game.phase.AttackResultsRender
import battletech.tui.screen.Color
import battletech.tui.screen.ContentWriter
import battletech.tui.screen.ScreenBuffer

internal class AttackResultsView(private val data: AttackResultsRender) : View {

    companion object {
        val INDEX: Int = PanelId.ATTACK_RESULTS.index
        const val TITLE: String = "ATTACK RESULTS"
        private const val PADDING = 2
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, TITLE, index = INDEX)

        val lastRow = y + height - 1
        val content = ContentWriter(buffer, x + PADDING, y + PADDING, width - (PADDING * 2))

        val byAttacker = data.results.groupBy { it.attackerId }

        for ((attackerId, attackerResults) in byAttacker) {
            if (content.cy >= lastRow) break

            val attackerName = data.unitNames[attackerId] ?: attackerId.value
            val attackerColor = playerColor(data.unitOwners[attackerId])
            content.writeln(attackerName.take(content.width), attackerColor)

            val byTarget = attackerResults.groupBy { it.targetId }

            for ((targetId, targetResults) in byTarget) {
                if (content.cy >= lastRow) break

                val targetName = data.unitNames[targetId] ?: targetId.value
                val targetLine = "  > $targetName"
                content.writeln(targetLine.take(content.width), Color.WHITE)

                for (result in targetResults) {
                    if (content.cy >= lastRow) break
                    renderWeaponResult(content, result, lastRow)
                }
            }
        }
    }

    private fun renderWeaponResult(
        content: ContentWriter,
        result: AttackResult,
        lastRow: Int,
    ) {
        // Line 1: weapon name + outcome (right-aligned)
        if (content.cy < lastRow) {
            val left = "    ${result.weaponName}"
            val outcomeText = if (result.hit) "HIT" else "MISS"
            val outcomeColor = if (result.hit) Color.GREEN else Color.RED
            val padding = (content.width - left.length - outcomeText.length).coerceAtLeast(1)
            val weaponLine = "$left${" ".repeat(padding)}$outcomeText"
            content.writeln(weaponLine.take(content.width), Color.WHITE)
            // Overwrite the outcome portion in the correct color
            val outcomeX = content.x + content.width - outcomeText.length
            if (outcomeX >= content.x) {
                content.buffer.writeString(outcomeX, content.cy - 1, outcomeText, outcomeColor)
            }
        }

        // Line 2: TN breakdown
        if (content.cy < lastRow) {
            content.writeln(buildTnLine(result).take(content.width), Color.WHITE)
        }

        // Line 3: to-hit dice
        if (content.cy < lastRow) {
            val toHit = result.toHitRoll
            val line = "     to-hit  ${toHit.d1}+${toHit.d2} = ${toHit.total}"
            content.writeln(line.take(content.width), Color.WHITE)
        }

        // Lines 4-5: location dice + damage (hit only)
        val locRoll = result.locationRoll
        val hitLoc = result.hitLocation
        if (result.hit && locRoll != null && hitLoc != null) {
            if (content.cy < lastRow) {
                val line = "     loc     ${locRoll.d1}+${locRoll.d2} = ${locRoll.total}"
                content.writeln(line.take(content.width), Color.WHITE)
            }
            if (content.cy < lastRow) {
                val locationName = hitLocationName(hitLoc)
                val line = "     → $locationName   ${result.damageApplied} dmg"
                content.writeln(line.take(content.width), Color.WHITE)
            }
        }
    }

    private fun buildTnLine(result: AttackResult): String {
        val bandStr = when (result.rangeBand) {
            RangeBand.SHORT -> "sht"
            RangeBand.MEDIUM -> "med"
            RangeBand.LONG -> "long"
            RangeBand.OUT_OF_RANGE -> "oor"
        }
        val sb = StringBuilder("     TN G${result.gunnery} +${result.rangeModifier}$bandStr")
        if (result.heatPenalty > 0) sb.append(" +${result.heatPenalty}heat")
        if (result.secondaryPenalty > 0) sb.append(" +${result.secondaryPenalty}sec")
        sb.append(" = ${result.targetNumber}")
        return sb.toString()
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

    private fun playerColor(player: PlayerId?): Color = when (player) {
        PlayerId.PLAYER_1 -> Color.BLUE
        PlayerId.PLAYER_2 -> Color.MAGENTA
        null -> Color.WHITE
    }
}
