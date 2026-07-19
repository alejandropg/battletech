package battletech.tactical.unit

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.PlayerId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A collection of units and the questions asked of it: spatial probe, authoritative lookup,
 * ownership/activity filters, "enemies of" — plus a memoised id index so [byId] is O(1)
 * regardless of how many times it is called in a render or a rules pass.
 *
 * `out T` keeps `UnitRoster<CombatUnit>` assignable where `UnitRoster<VisibleUnit>` is
 * expected (e.g. [battletech.tactical.query.PlayerGameState] holding the projected form of
 * [battletech.tactical.model.GameState]'s roster).
 *
 * Value semantics: [equals]/[hashCode]/[toString] delegate to [all], so this reads like a
 * data class from the outside despite not being one — a regular class is required here
 * because a `@JvmInline value class` can only hold one field, and [index] is a second,
 * deliberately unserialized one.
 */
@Serializable(with = UnitRosterSerializer::class)
public class UnitRoster<out T : VisibleUnit>(public val all: List<T>) : Iterable<T> {

    // Not a constructor property, so it is not serialized. Built on first by-id lookup.
    private val index: Map<UnitId, T> by lazy { all.associateBy { it.id } }

    public val size: Int get() = all.size

    public fun isEmpty(): Boolean = all.isEmpty()

    public fun isNotEmpty(): Boolean = all.isNotEmpty()

    override fun iterator(): Iterator<T> = all.iterator()

    /**
     * Spatial probe: the unit occupying [position], or `null` if the hex is empty.
     * Multiple units never share a position, so at most one match exists.
     */
    public fun at(position: HexCoordinates): T? = all.find { it.position == position }

    /**
     * Authoritative lookup by [id]. Throws [UnknownUnitException] if [id] does not name a
     * unit in this roster. A [UnitId] never legitimately fails to resolve — units are never
     * removed from the roster, destruction only flips [VisibleUnit.isDestroyed] — so an
     * unknown id can only mean a bug or a tampered client, never a correctly-behaving one.
     * See [UnknownUnitException] for the full rationale.
     */
    public fun byId(id: UnitId): T = index[id] ?: throw UnknownUnitException(id)

    public fun of(player: PlayerId): UnitRoster<T> = UnitRoster(all.filter { it.owner == player })

    /**
     * Units [player] can still activate this turn — excludes shutdown, destroyed, and
     * unconscious-pilot units. Every field it tests ([VisibleUnit.isShutdown],
     * [VisibleUnit.isDestroyed], [VisibleUnit.isPilotConscious]) is observable and so is
     * present on both the authoritative and the per-viewer projected roster.
     */
    public fun activeOf(player: PlayerId): UnitRoster<T> =
        UnitRoster(of(player).all.filter { !it.isShutdown && !it.isDestroyed && it.isPilotConscious })

    /** Units belonging to a different owner than [unit], excluding destroyed ones. */
    public fun enemiesOf(unit: VisibleUnit): UnitRoster<T> =
        UnitRoster(all.filter { it.owner != unit.owner && !it.isDestroyed })

    /** Replaces the unit sharing [unit]'s id, leaving every other unit untouched. */
    public fun withUnit(unit: @UnsafeVariance T): UnitRoster<T> =
        UnitRoster(all.map { if (it.id == unit.id) unit else it })

    /** Bulk replace: every unit in [units] overwrites the roster entry sharing its id. */
    public fun withUnits(units: Collection<@UnsafeVariance T>): UnitRoster<T> {
        val byId = units.associateBy { it.id }
        return UnitRoster(all.map { byId[it.id] ?: it })
    }

    public fun mapUnits(transform: (T) -> @UnsafeVariance T): UnitRoster<T> = UnitRoster(all.map(transform))

    override fun equals(other: Any?): Boolean = other is UnitRoster<*> && all == other.all

    override fun hashCode(): Int = all.hashCode()

    override fun toString(): String = "UnitRoster($all)"
}

/**
 * Delegates to [ListSerializer] so the wire format stays a bare JSON array — [UnitRoster] is
 * transparent on the wire, the memoised id index is never part of it.
 */
public class UnitRosterSerializer<T : VisibleUnit>(elementSerializer: KSerializer<T>) : KSerializer<UnitRoster<T>> {
    private val delegate: KSerializer<List<T>> = ListSerializer(elementSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: UnitRoster<T>) {
        encoder.encodeSerializableValue(delegate, value.all)
    }

    override fun deserialize(decoder: Decoder): UnitRoster<T> = UnitRoster(decoder.decodeSerializableValue(delegate))
}
