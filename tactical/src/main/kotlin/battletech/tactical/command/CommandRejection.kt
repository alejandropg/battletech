package battletech.tactical.command

/**
 * Reasons the session refuses a command before the underlying rules are
 * even consulted (phase/turn-ownership concerns).
 *
 * Concrete cases are added in PR5 when [GameCommand] arrives. For now this
 * sealed interface only fixes the type boundary so other code can refer to
 * `CommandRejection` and `RejectionReason` consistently.
 */
public sealed interface CommandRejection : RejectionReason
