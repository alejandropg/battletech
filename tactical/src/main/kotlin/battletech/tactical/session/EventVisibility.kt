package battletech.tactical.session

import battletech.tactical.action.PlayerId
import battletech.tactical.event.GameEvent

/**
 * Per-player event redaction seam. [filterFor] decides what (if anything)
 * a given player should observe about an emitted [GameEvent]:
 *
 *  - returning the event unchanged delivers it verbatim,
 *  - returning a different [GameEvent] delivers a redacted variant,
 *  - returning `null` suppresses the event for that player entirely.
 *
 * Current implementation is permissive: every player observes every
 * event as-is. Hidden-info rules (fog of war, undisclosed enemy heat,
 * unrevealed unit identities) will land here without subscriber-side
 * changes.
 */
public object EventVisibility {
    public fun filterFor(playerId: PlayerId, event: GameEvent): GameEvent? = event
}
