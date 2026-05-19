package battletech.tactical.session

/**
 * Handle returned from [BattleSession.subscribe]. Call [unsubscribe] to
 * detach the listener; the call is idempotent — subsequent invocations
 * are no-ops.
 *
 * Subscriptions exist so deliveries other than the command submitter
 * (the inactive player in hot-seat play, a spectator socket, a logger)
 * can receive [battletech.tactical.event.GameEvent]s as the session
 * produces them. Each subscriber is associated with a
 * [battletech.tactical.action.PlayerId], and events are run through
 * [EventVisibility.filterFor] before delivery so hidden-info redaction
 * can later kick in without subscriber-side changes.
 */
public interface Subscription {
    public fun unsubscribe()
}
