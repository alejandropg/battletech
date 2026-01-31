package battletech

import battletech.strategic.StrategicRules
import battletech.tactical.TacticalRules

fun main() {
    println("BattleTech Rules Engine")
    println("======================")

    val strategic = StrategicRules()
    val tactical = TacticalRules()

    println("Strategic: Campaign movement for 5 hexes = ${strategic.calculateCampaignMovement(5)}")
    println("Tactical: To-hit number (Gunnery 4, Range 3) = ${tactical.calculateToHit(4, 3)}")
}
