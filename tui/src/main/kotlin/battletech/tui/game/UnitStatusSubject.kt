package battletech.tui.game

import battletech.tactical.query.PublicUnit
import battletech.tactical.unit.CombatUnit

/**
 * The subject rendered by the UNIT STATUS panel: either the full private
 * [CombatUnit] (the viewer owns it) or the redacted [PublicUnit] projection
 * (an enemy unit, whose private stats a player must not see).
 */
public sealed interface UnitStatusSubject {
    public data class Owned(val unit: CombatUnit) : UnitStatusSubject
    public data class Public(val unit: PublicUnit) : UnitStatusSubject
}
