package battletech.tactical.unit

/**
 * Thrown when a [UnitId] does not name any unit in the [battletech.tactical.model.GameState]
 * (or [battletech.tactical.query.PlayerGameState] projection) it was looked up against.
 *
 * A [UnitId] never legitimately fails to resolve: units are never removed from
 * [battletech.tactical.model.GameState.units] (destruction only flips
 * [CombatUnit.isDestroyed]), so every id ever handed out — including one embedded in a
 * client-submitted command — still names a real unit. An unknown id can therefore only come
 * from a bug or a tampered/malicious client, never from a correctly-behaving one; it is a
 * violated precondition of the lookup, not a gameplay outcome, so it is modeled as a thrown
 * exception rather than a [battletech.tactical.session.CommandRejection] value.
 */
public class UnknownUnitException(public val id: UnitId) : IllegalArgumentException("No unit with id $id")
