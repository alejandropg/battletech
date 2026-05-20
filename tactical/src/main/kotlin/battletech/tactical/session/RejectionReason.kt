package battletech.tactical.session

/**
 * Umbrella marker for anything that can cause a player intent to be rejected
 * by the domain. Two branches:
 *  - [CommandRejection]: the command itself is not legal in the current
 *    session/turn state (wrong phase, not your turn, unit already acted, ...)
 *  - [RuleRejection]: the underlying rule that backs the command refuses
 *    (out of range, no ammo, weapon destroyed, ...)
 *
 * Sealed so deliveries can exhaustively pattern-match and decide their own
 * presentation (TUI flash, web 4xx, remote protocol code).
 */
public sealed interface RejectionReason
