package battletech.tactical.query

import battletech.tactical.model.GameState

/**
 * A view of [GameState] safe to expose to a specific player. For now this is
 * just an alias for the full state; PR8 (per-player subscription channel)
 * will turn this into a real projection that redacts hidden information
 * (internal damage detail, unrevealed ammo per ton, etc.) per [PlayerView].
 *
 * Treating it as a separate type now means call sites don't have to change
 * later when redaction lands.
 *
 * **Deliberately still deferred as of the Stage 7 TUI-layer pass** (see
 * `try-to-improve-the-snug-toast.md`, D6): [DefaultPlayerView] already takes
 * this type and every [PlayerView] call site already reads through it, so
 * the seam this typealias exists to provide is in place. What's still
 * missing is the redaction logic itself, plus the matching tightening of
 * [battletech.tactical.session.EventVisibility.filterFor] (currently the
 * identity function — every event reaches every subscriber unredacted).
 * Building that now would be one hidden-info rule with no second example to
 * generalize from — a speculative, single-adapter "seam" is YAGNI. Do it
 * when a second redaction need (or the real hidden-info requirement) shows
 * up, using this typealias as the boundary where the projection plugs in.
 */
public typealias PublicGameState = GameState
