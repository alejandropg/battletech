package battletech.tactical.model.game

import battletech.tactical.model.GameState
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.map.GameMapCatalog
import battletech.tactical.model.map.MapLoadException
import battletech.tactical.unit.MechModels
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.UnitRoster
import battletech.tactical.unit.createUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** On-disk starting-game definition. Coordinates are one-based, like map source files. */
@Serializable
internal data class GameFile(
    internal val map: String,
    internal val units: List<UnitSpec>,
) {

    internal fun toGameState(catalog: GameMapCatalog): GameState {
        if (map.isBlank()) throw GameLoadException("Game map name must not be blank")

        val gameMap = try {
            catalog.resolve(map)
        } catch (e: MapLoadException) {
            throw GameLoadException("Could not resolve game map '$map': ${e.message}", e)
        }

        val ids = mutableSetOf<String>()
        val positions = mutableSetOf<HexCoordinates>()
        val players = mutableSetOf<PlayerId>()
        val combatUnits = units.map { spec ->
            if (spec.id.isBlank()) throw GameLoadException("Unit id must not be blank")
            if (!ids.add(spec.id)) throw GameLoadException("Duplicate unit id: ${spec.id}")

            val player = PlayerId.entries.getOrNull(spec.player - 1)
                ?: throw GameLoadException("Unit '${spec.id}' has invalid player ${spec.player}; expected 1 or 2")
            players += player

            if (spec.gunnerySkill !in SKILL_RANGE) {
                throw GameLoadException("Unit '${spec.id}' gunnerySkill must be in 0..8, was ${spec.gunnerySkill}")
            }
            if (spec.pilotingSkill !in SKILL_RANGE) {
                throw GameLoadException("Unit '${spec.id}' pilotingSkill must be in 0..8, was ${spec.pilotingSkill}")
            }

            val model = MechModels.find(spec.variant)
                ?: throw GameLoadException(
                    "Unit '${spec.id}' has unknown mech variant '${spec.variant}'. " +
                        "Known variants: ${MechModels.variants.sorted().joinToString(", ")}"
                )
            val position = spec.position.toCoordinates()
            if (position !in gameMap.hexes) {
                throw GameLoadException(
                    "Unit '${spec.id}' position (${spec.position.col}, ${spec.position.row}) is outside map '$map'"
                )
            }
            if (!positions.add(position)) {
                throw GameLoadException(
                    "More than one unit occupies position (${spec.position.col}, ${spec.position.row})"
                )
            }

            model.createUnit(
                id = UnitId(spec.id),
                owner = player,
                gunnerySkill = spec.gunnerySkill,
                pilotingSkill = spec.pilotingSkill,
                position = position,
                facing = spec.facing,
            )
        }

        val missingPlayers = PlayerId.entries.filterNot { it in players }
        if (missingPlayers.isNotEmpty()) {
            val numbers = missingPlayers.joinToString { (it.ordinal + 1).toString() }
            throw GameLoadException("Game must contain at least one unit for each player; missing player $numbers")
        }

        return GameState(UnitRoster(combatUnits), gameMap)
    }

    @Serializable
    internal data class UnitSpec(
        internal val id: String,
        internal val player: Int,
        internal val variant: String,
        internal val gunnerySkill: Int,
        internal val pilotingSkill: Int,
        internal val position: PositionSpec,
        internal val facing: HexDirection,
    )

    @Serializable
    internal data class PositionSpec(
        internal val col: Int,
        internal val row: Int,
    ) {
        internal fun toCoordinates(): HexCoordinates = HexCoordinates(col - 1, row - 1)
    }

    internal companion object {
        private val SKILL_RANGE: IntRange = 0..8

        internal fun decode(json: Json, text: String): GameFile = json.decodeFromString(text)
    }
}
