package battletech.tactical.attack

import battletech.tactical.dice.DiceRoll
import battletech.tactical.unit.UnitId
import kotlinx.serialization.Serializable

/**
 * The read surface every attack outcome exposes regardless of whether it hit — who shot what
 * with which weapon, and the to-hit check that decided it. Kept separate from [AttackResult]
 * so its leaves can satisfy it by delegation instead of restating each property.
 *
 * Deliberately NOT `@Serializable` and NOT sealed — same role as [ResolvedAttack]: a plain
 * read interface, so it never becomes a second polymorphic parent for the result hierarchy.
 */
public interface ToHitAttempted {
    public val attackerId: UnitId
    public val targetId: UnitId
    public val weaponName: String
    public val gunnery: Int
    public val modifiers: List<ToHitModifier>
    public val targetNumber: Int
    public val toHitRoll: DiceRoll
}

/** The resolved to-hit check for one weapon attack, shared by every [AttackResult] leaf. */
@Serializable
public data class ToHitAttempt(
    override val attackerId: UnitId,
    override val targetId: UnitId,
    override val weaponName: String,
    override val gunnery: Int,
    override val targetNumber: Int,
    override val toHitRoll: DiceRoll,
    override val modifiers: List<ToHitModifier> = emptyList(),
) : ToHitAttempted
