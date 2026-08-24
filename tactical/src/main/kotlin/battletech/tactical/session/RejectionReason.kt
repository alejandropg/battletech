package battletech.tactical.session

import kotlinx.serialization.Serializable

/**
 * Umbrella marker for a player intent rejected at the session level: [CommandRejection] —
 * the command itself is not legal in the current session/turn state (wrong phase, not your
 * turn, unit already acted, ...), or wraps the underlying rule that refused via
 * [CommandRejection.RuleViolation].
 *
 * [battletech.tactical.rules.RuleRejection] deliberately does NOT implement this interface:
 * a rule rejection is only ever surfaced to a client already wrapped in
 * [CommandRejection.RuleViolation] (see [battletech.tactical.rules.RuleResult.Unsatisfied],
 * which is consumed by rule-evaluating handlers before they build that wrapper) — so there is
 * no call site that needs the two treated polymorphically, and requiring it would force
 * [battletech.tactical.rules.RuleRejection] into this package (Kotlin requires all direct
 * subclasses of a sealed type to share its package).
 *
 * Sealed so deliveries can exhaustively pattern-match and decide their own
 * presentation (TUI flash, web 4xx, remote protocol code).
 */
@Serializable
public sealed interface RejectionReason
