package battletech.tactical.model.game

import battletech.tactical.io.ResourceOrFileLoader
import battletech.tactical.model.GameState
import battletech.tactical.model.map.GameMapCatalog
import kotlinx.serialization.json.Json
import java.nio.file.Path

/** Default [Json] configuration for game definitions: strict about unknown keys. */
private val gameJson: Json = Json {
    ignoreUnknownKeys = false
    prettyPrint = true
}

/** Loads a compact JSON starting-game definition into a fully initialized [GameState]. */
internal class GameStateLoader(
    catalog: GameMapCatalog,
    json: Json = gameJson,
) {

    private val loader = ResourceOrFileLoader(
        resourceDir = "game",
        label = "Game",
        json = json,
        build = { text, _ -> GameFile.decode(json, text).toGameState(catalog) },
        exception = ::GameLoadException,
    )

    /** Resolves [spec] as an existing file path or an extensionless packaged game name. */
    internal fun resolve(spec: String): GameState = loader.resolve(spec)

    /** Reads and parses the game file at [path], throwing [GameLoadException] on failure. */
    internal fun load(path: Path): GameState = loader.load(path)

    /** Packaged game names from `game/index.json`. */
    internal fun builtInNames(): List<String> = loader.builtInNames()
}
