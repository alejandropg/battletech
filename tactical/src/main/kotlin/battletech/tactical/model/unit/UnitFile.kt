package battletech.tactical.model.unit

import battletech.tactical.model.GameMap
import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.model.mech.MechModelCatalog
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.UnitId
import battletech.tactical.unit.UnitRoster
import battletech.tactical.unit.createUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * On-disk unit collection: a starting roster, independent of any board. Coordinates are
 * one-based, like map source files, and are checked against whichever map a launcher pairs this
 * collection with — see [toRoster]. Intrinsic per-unit fields are validated at [decode] time (see
 * [validateIntrinsics]) so a catalog can list a collection's unit ids without a map or mech
 * catalog on hand; [toRoster] then assumes that validation already ran.
 */
@Serializable
internal data class UnitFile(
    internal val units: List<UnitSpec>,
) {

    /** Every unit id in this file, in file order — the shape `--list-units` renders. */
    internal fun unitIds(): List<String> = units.map { it.id }

    /**
     * Assembles this collection onto [map] into a validated [UnitRoster]: every unit's variant
     * must exist in [mechCatalog], its position must fall inside [map], no two units may share a
     * position, and both players must be represented. [mapName] names [map] in error messages
     * only — resolving it is the caller's job (see [battletech.tactical.model.content.ContentCatalog]).
     */
    internal fun toRoster(map: GameMap, mapName: String, mechCatalog: MechModelCatalog): UnitRoster<CombatUnit> {
        val positions = mutableSetOf<HexCoordinates>()
        val players = mutableSetOf<PlayerId>()
        val combatUnits = units.map { spec ->
            // spec.player is already known to be 1 or 2 — validateIntrinsics ran at decode time.
            val player = PlayerId.entries[spec.player - 1]
            players += player

            val model = mechCatalog.find(spec.variant)
                ?: throw UnitLoadException(
                    "Unit '${spec.id}' has unknown mech variant '${spec.variant}'. " +
                        "Known variants: ${mechCatalog.variants.sorted().joinToString(", ")}",
                )
            val position = spec.position.toCoordinates()
            if (position !in map.hexes) {
                throw UnitLoadException(
                    "Unit '${spec.id}' position (${spec.position.col}, ${spec.position.row}) is outside map '$mapName'",
                )
            }
            if (!positions.add(position)) {
                throw UnitLoadException(
                    "More than one unit occupies position (${spec.position.col}, ${spec.position.row})",
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
            throw UnitLoadException("Unit collection must contain at least one unit for each player; missing player $numbers")
        }

        return UnitRoster(combatUnits)
    }

    /**
     * Validates every field intrinsic to a unit spec, independent of any map or mech catalog: id
     * nonblank and unique within the file, player one or two, and both skills in `0..8`.
     */
    private fun validateIntrinsics(): UnitFile {
        val ids = mutableSetOf<String>()
        units.forEach { spec ->
            if (spec.id.isBlank()) throw UnitLoadException("Unit id must not be blank")
            if (!ids.add(spec.id)) throw UnitLoadException("Duplicate unit id: ${spec.id}")

            if (PlayerId.entries.getOrNull(spec.player - 1) == null) {
                throw UnitLoadException("Unit '${spec.id}' has invalid player ${spec.player}; expected 1 or 2")
            }
            if (spec.gunnerySkill !in SKILL_RANGE) {
                throw UnitLoadException("Unit '${spec.id}' gunnerySkill must be in 0..8, was ${spec.gunnerySkill}")
            }
            if (spec.pilotingSkill !in SKILL_RANGE) {
                throw UnitLoadException("Unit '${spec.id}' pilotingSkill must be in 0..8, was ${spec.pilotingSkill}")
            }
        }
        return this
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

        internal fun decode(json: Json, text: String): UnitFile = json.decodeFromString<UnitFile>(text).validateIntrinsics()
    }
}
