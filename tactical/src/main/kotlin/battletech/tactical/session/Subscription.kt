package battletech.tactical.session

/**
 * Handle returned from [BattleSession.subscribe]. Call [unsubscribe] to
 * detach the listener; the call is idempotent — subsequent invocations
 * are no-ops.
 *
 * Subscriptions exist so deliveries other than the command submitter — another seat's
 * client, a spectator socket, a logger — can receive [GameEvent]s as the session
 * produces them.
 */
public interface Subscription {
    public fun unsubscribe()
}
