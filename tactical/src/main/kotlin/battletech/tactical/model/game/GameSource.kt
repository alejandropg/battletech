package battletech.tactical.model.game

import battletech.tactical.model.GameState
import battletech.tactical.model.map.GameMapCatalog
import battletech.tactical.model.mech.MechModelCatalog

/** Built-in game used when a launcher does not specify a game definition. */
public const val DEFAULT_GAME_NAME: String = "default"

/** Resolves a built-in game name or existing game-file path into a validated starting state. */
public fun resolveGame(
    spec: String,
    mapCatalog: GameMapCatalog = GameMapCatalog.load(),
    mechCatalog: MechModelCatalog = MechModelCatalog.load(),
): GameState = GameStateLoader(mapCatalog, mechCatalog).resolve(spec)
