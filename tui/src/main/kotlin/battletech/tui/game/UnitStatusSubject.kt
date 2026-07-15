package battletech.tui.game

import battletech.tactical.query.PublicUnit
import battletech.tactical.unit.CombatUnit

/**
 * The subject rendered by the UNIT STATUS panel: either the full
 * [CombatUnit] (the viewer owns it) or the condensed [PublicUnit] summary
 * (a unit the viewer doesn't own).
 *
 * The distinction is presentational, not access control: the game is
 * open-information and the full [CombatUnit] stays reachable for any unit
 * via `GameSession.gameState`. See [PublicUnit]'s KDoc.
 */
public sealed interface UnitStatusSubject {
    public data class Owned(val unit: CombatUnit) : UnitStatusSubject
    public data class Public(val unit: PublicUnit) : UnitStatusSubject
}
