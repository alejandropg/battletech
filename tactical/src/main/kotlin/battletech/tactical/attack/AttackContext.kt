package battletech.tactical.attack

import battletech.tactical.model.GameMap
import battletech.tactical.query.VisibleUnit
import battletech.tactical.unit.CombatUnit
import battletech.tactical.unit.Weapon

/**
 * The inputs every [AttackRule] evaluates against.
 *
 * The asymmetry between [actor] and [target] is load-bearing, and deliberate:
 *
 *  - [actor] is a full [CombatUnit] because attack legality genuinely depends on the
 *    attacker's record sheet — ammo bins, weapon destruction, heat, sensor criticals. In
 *    every context that matters (the authoritative handlers, and the per-viewer query path)
 *    the actor is a unit the caller owns, so that data is legitimately in hand. The query
 *    path resolves it via [battletech.tactical.query.PlayerGameState.ownUnitById], which
 *    fails loudly rather than silently degrade if that ownership assumption is ever wrong.
 *  - [target] is only a [VisibleUnit] because no rule needs more: every field the rules read
 *    off the target (position, elevation/water depth via position, prone, shutdown,
 *    movement-this-turn) is public information any player could read off the table. Typing
 *    it this way is what lets a remote client run the *same* rules against its own projected
 *    snapshot, and makes "a rule reads a foreign unit's private field" a compile error
 *    rather than a leak.
 *
 * [map] rather than a whole [battletech.tactical.model.GameState] for the same reason: the
 * map is all the rules actually consult, and a per-viewer
 * [battletech.tactical.query.PlayerGameState] carries one while raw game state is not
 * available client-side at all.
 */
public sealed interface AttackContext {
    public val actor: CombatUnit
    public val map: GameMap
    public val target: VisibleUnit
}

public data class WeaponAttackContext(
    override val actor: CombatUnit,
    override val map: GameMap,
    override val target: VisibleUnit,
    public val weapon: Weapon,
) : AttackContext

public data class PhysicalAttackContext(
    override val actor: CombatUnit,
    override val map: GameMap,
    override val target: VisibleUnit,
) : AttackContext
