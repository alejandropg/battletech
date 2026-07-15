package battletech.tactical.query

import battletech.tactical.model.HexCoordinates
import battletech.tactical.model.HexDirection
import battletech.tactical.model.PlayerId
import battletech.tactical.unit.ArmorLayout
import battletech.tactical.unit.MovementThisTurn
import battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

/**
 * A per-viewer projection of a [battletech.tactical.unit.CombatUnit]: exposes only the
 * fields a BattleTech player can observe about ANY unit on the table, whether or not
 * they own it. [OwnUnit] additionally carries the full
 * [battletech.tactical.unit.CombatUnit] for units the viewer owns; [ForeignUnit] holds
 * nothing beyond what is declared here — there is no field to leak because the field
 * does not exist on the type.
 *
 * Deliberately public, despite looking sensitive at a glance:
 * - [movementThisTurn] — you watch the enemy move across the table.
 * - [isProne], [isShutdown], [isDestroyed] — visibly true of the miniature itself.
 * - [armor] — damage is applied openly in BattleTech; armor diagrams are not hidden.
 * - [isPilotConscious] — an unconscious pilot leaves the 'Mech visibly inert, and
 *   [battletech.tactical.session.PilotKnockedUnconscious] carries no private data, so it
 *   reaches every player's log unredacted. Hiding the field the log announces would be
 *   theater. The pilot *hit count* behind it stays private (record-sheet data).
 * - [tonnage] is public **deliberately**: [name] already reveals the chassis (e.g.
 *   "Atlas AS7-D"), and tonnage is looked up from that chassis name in the Technical
 *   Readouts. Hiding a field that's derivable from a field you already show is theater,
 *   not redaction. (The older `PublicUnit` omitted tonnage; that was an inconsistency
 *   this projection does not repeat.)
 *
 * The test for "is this public?" is **observability**, not sensitivity: could an
 * opponent learn it by watching the table or hearing the roll announced? If yes,
 * withholding it buys nothing and costs consistency.
 *
 * Absent by design — never appears on [ForeignUnit], and therefore never leaks:
 * gunnery skill, piloting skill, current heat, heat sink, internal structure, critical
 * hit layout/locations, heat generated this turn, pilot hits.
 */
@Serializable
public sealed interface VisibleUnit {
    public val id: UnitId
    public val owner: PlayerId
    public val name: String
    public val tonnage: Int
    public val position: HexCoordinates
    public val facing: HexDirection
    public val torsoFacing: HexDirection
    public val armor: ArmorLayout
    public val walkingMP: Int
    public val runningMP: Int
    public val jumpMP: Int
    public val weapons: List<PublicWeapon>
    public val isProne: Boolean
    public val isShutdown: Boolean
    public val isDestroyed: Boolean
    public val isPilotConscious: Boolean
    public val movementThisTurn: MovementThisTurn
}
