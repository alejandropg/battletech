package battletech.tactical.model

import battletech.tactical.model.map.MapCatalog
import battletech.tactical.unit.MechModels
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.UnitRoster
import battletech.tactical.unit.createUnit

public class GameStateFactory {

    public fun sampleGameState(map: GameMap = MapCatalog.defaultMap()): GameState {
        val units = listOf(
            MechModels["AS7-D"].createUnit(
                id = UnitId("A1"),
                owner = PlayerId.PLAYER_1,
                position = HexCoordinates(1, 1),
                facing = HexDirection.SE
            ),
            MechModels["HBK-4G"].createUnit(
                id = UnitId("H1"),
                owner = PlayerId.PLAYER_1,
                position = HexCoordinates(2, 3)
            ),
            MechModels["WVR-6R"].createUnit(
                id = UnitId("W1"),
                owner = PlayerId.PLAYER_2,
                pilotingSkill = 4,
                position = HexCoordinates(7, 3)
            ),
            MechModels["WVR-6R"].createUnit(
                id = UnitId("W2"),
                owner = PlayerId.PLAYER_2,
                position = HexCoordinates(8, 5)
            ),
        )

        return GameState(UnitRoster(units), map)
    }
}
