package battletech.tactical.model.game

import battletech.tactical.model.GameState
import battletech.tactical.model.map.GameMapCatalog

/** Built-in game used when a launcher does not specify a game definition. */
public const val DEFAULT_GAME_NAME: String = "default"

/** Resolves a built-in game name or existing game-file path into a validated starting state. */
public fun resolveGame(
    spec: String,
    catalog: GameMapCatalog = GameMapCatalog.load(),
): GameState = GameStateLoader(catalog).resolve(spec)
