package battletech.tactical.movement

import battletech.tactical.model.Hex
import battletech.tactical.model.Terrain
import kotlin.math.max

public object MovementCost {

    public fun terrainCost(terrain: Terrain): Int = when (terrain) {
        Terrain.CLEAR -> 1
        Terrain.LIGHT_WOODS -> 2
        Terrain.HEAVY_WOODS -> 3
        Terrain.WATER -> 2
    }

    public fun enterHexCost(from: Hex, to: Hex): Int {
        val elevationCost = max(0, to.elevation - from.elevation)
        return terrainCost(to.terrain) + elevationCost
    }
}
