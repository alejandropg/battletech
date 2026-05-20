package battletech

import battletech.strategic.StrategicRules

public fun main() {
    println("BattleTech Rules Engine")
    println("======================")

    val strategic = StrategicRules()

    println("Strategic: Campaign movement for 5 hexes = ${strategic.calculateCampaignMovement(5)}")
}
