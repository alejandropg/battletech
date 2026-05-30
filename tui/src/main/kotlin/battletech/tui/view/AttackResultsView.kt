package battletech.tui.view

import battletech.tactical.attack.AttackResult
import battletech.tactical.attack.HitLocation
import battletech.tactical.attack.RangeBand
import battletech.tactical.model.PlayerId
import battletech.tui.game.phase.AttackResultsRender
import battletech.tui.screen.Color
import battletech.tui.screen.ScreenBuffer

public class AttackResultsView(private val data: AttackResultsRender) : View {

    public companion object {
        public const val INDEX: Int = 5
        public const val TITLE: String = "ATTACK RESULTS"
    }

    override fun render(buffer: ScreenBuffer, x: Int, y: Int, width: Int, height: Int) {
        buffer.drawBox(x, y, width, height, TITLE, index = INDEX)

        val cx = x + 2
        var cy = y + 2
        val inner = width - 4
        val lastRow = y + height - 1

        val byAttacker = data.results.groupBy { it.attackerId }

        for ((attackerId, attackerResults) in byAttacker) {
            if (cy >= lastRow) break

            val attackerName = data.unitNames[attackerId] ?: attackerId.value
            val attackerColor = playerColor(data.unitOwners[attackerId])
            buffer.writeString(cx, cy, attackerName.take(inner), attackerColor)
            cy++

            val byTarget = attackerResults.groupBy { it.targetId }

            for ((targetId, targetResults) in byTarget) {
                if (cy >= lastRow) break

                val targetName = data.unitNames[targetId] ?: targetId.value
                val targetLine = "  > $targetName"
                buffer.writeString(cx, cy, targetLine.take(inner), Color.WHITE)
                cy++

                for (result in targetResults) {
                    if (cy >= lastRow) break
                    cy = renderWeaponResult(buffer, result, cx, inner, cy, lastRow)
                }
            }
        }
    }

    private fun renderWeaponResult(
        buffer: ScreenBuffer,
        result: AttackResult,
        cx: Int,
        inner: Int,
        startCy: Int,
        lastRow: Int,
    ): Int {
        var cy = startCy

        // Line 1: weapon name + outcome (right-aligned)
        if (cy < lastRow) {
            val left = "    ${result.weaponName}"
            val outcomeText = if (result.hit) "HIT" else "MISS"
            val outcomeColor = if (result.hit) Color.GREEN else Color.RED
            val padding = (inner - left.length - outcomeText.length).coerceAtLeast(1)
            val weaponLine = "$left${" ".repeat(padding)}$outcomeText"
            buffer.writeString(cx, cy, weaponLine.take(inner), Color.WHITE)
            // Overwrite the outcome portion in the correct color
            val outcomeX = cx + inner - outcomeText.length
            if (outcomeX >= cx) {
                buffer.writeString(outcomeX, cy, outcomeText, outcomeColor)
            }
            cy++
        }

        // Line 2: TN breakdown
        if (cy < lastRow) {
            buffer.writeString(cx, cy, buildTnLine(result).take(inner), Color.WHITE)
            cy++
        }

        // Line 3: to-hit dice
        if (cy < lastRow) {
            val toHit = result.toHitRoll
            val line = "     to-hit  ${toHit.d1}+${toHit.d2} = ${toHit.total}"
            buffer.writeString(cx, cy, line.take(inner), Color.WHITE)
            cy++
        }

        // Lines 4-5: location dice + damage (hit only)
        val locRoll = result.locationRoll
        val hitLoc = result.hitLocation
        if (result.hit && locRoll != null && hitLoc != null) {
            if (cy < lastRow) {
                val line = "     loc     ${locRoll.d1}+${locRoll.d2} = ${locRoll.total}"
                buffer.writeString(cx, cy, line.take(inner), Color.WHITE)
                cy++
            }
            if (cy < lastRow) {
                val locationName = hitLocationName(hitLoc)
                val line = "     → $locationName   ${result.damageApplied} dmg"
                buffer.writeString(cx, cy, line.take(inner), Color.WHITE)
                cy++
            }
        }

        return cy
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
