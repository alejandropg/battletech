package battletech.tactical.query

import battletech.tactical.attack.physical.PhysicalAttackKind
import battletech.tactical.dice.twoD6AtLeastProbability
import battletech.tactical.session.RuleRejection
import battletech.tactical.unit.UnitId

/**
 * A single offerable physical attack against an adjacent enemy: the concrete
 * [kind] to submit, a display [label], whether it is currently [available],
 * its success chance and expected damage, and — when unavailable — the rule
 * reasons why.
 */
public data class PhysicalAttackOption(
    public val targetId: UnitId,
    public val targetName: String,
    public val kind: PhysicalAttackKind,
    public val label: String,
    public val available: Boolean,
    public val targetDiceRoll: Int,
    public val expectedDamage: Int,
    public val unavailableReasons: List<RuleRejection>,
) {
    public val successChance: Int = twoD6AtLeastProbability(targetDiceRoll)
}
