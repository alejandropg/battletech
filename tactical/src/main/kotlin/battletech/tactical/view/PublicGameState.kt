package battletech.tactical.view

import battletech.tactical.model.GameState

/**
 * A view of [GameState] safe to expose to a specific player. For now this is
 * just an alias for the full state; PR8 (per-player subscription channel)
 * will turn this into a real projection that redacts hidden information
 * (internal damage detail, unrevealed ammo per ton, etc.) per [PlayerView].
 *
 * Treating it as a separate type now means call sites don't have to change
 * later when redaction lands.
 */
public typealias PublicGameState = GameState
