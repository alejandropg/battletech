package battletech.tactical.model

import battletech.tactical.action.PlayerId
import battletech.tactical.action.UnitId

public class GameStateFactory {

    public fun sampleGameState(): GameState {
        val hexes = mutableMapOf<HexCoordinates, Hex>()
        for (col in 0..9) {
            for (row in 0..9) {
                val coords = HexCoordinates(col, row)
                val terrain = when {
                    col == 3 && row in 2..5 -> Terrain.LIGHT_WOODS
                    col == 4 && row in 3..4 -> Terrain.HEAVY_WOODS
                    col == 6 && row in 1..3 -> Terrain.WATER
                    else -> Terrain.CLEAR
                }
                val elevation = if (col == 5 && row in 2..4) 2 else 0
                hexes[coords] = Hex(coords, terrain, elevation)
            }
        }

        val units = listOf(
            MechModels["AS7-D"].createUnit(
                id = UnitId("atlas"),
                owner = PlayerId.PLAYER_1,
                position = HexCoordinates(1, 1),
                facing = HexDirection.SE
            ),
            MechModels["HBK-4G"].createUnit(
                id = UnitId("hunchback"),
                owner = PlayerId.PLAYER_1,
                position = HexCoordinates(2, 3)
            ),
            MechModels["WVR-6R"].createUnit(
                id = UnitId("wolverine-1"),
                owner = PlayerId.PLAYER_2,
                pilotingSkill = 4,
                position = HexCoordinates(7, 3)
            ),
            MechModels["WVR-6R"].createUnit(
                id = UnitId("wolverine-2"),
                owner = PlayerId.PLAYER_2,
                position = HexCoordinates(8, 5)
            ),
        )

        return GameState(units, GameMap(hexes))
    }
}
