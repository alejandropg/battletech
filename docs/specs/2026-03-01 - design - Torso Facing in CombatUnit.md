# Torso Facing in CombatUnit

## Problem

Torso facing is currently stored in the TUI layer (`AttackController.committedTorsoFacings` mutable map and `RenderData.torsoFacings`). This means torso facing is not available to other phases or to the tactical engine.

## Solution

Move torso facing into `CombatUnit` as a domain property, making it part of `GameState`.

## Core Change

Add `torsoFacing` to `CombatUnit`:

```kotlin
public data class CombatUnit(
    // ... existing fields ...
    public val facing: HexDirection = HexDirection.N,
    public val torsoFacing: HexDirection = facing,
    // ...
)
```

Default value is `facing` (leg facing), so existing unit construction is unaffected.

## Changes by Layer

### Tactical layer — `CombatUnit`

- Add `torsoFacing: HexDirection = facing` property.

### TUI layer — `AttackController`

- Remove `committedTorsoFacings` mutable map.
- Remove `clearTorsoFacings()` method.
- Remove `declaredTorsoFacings()` method.
- `enter()`: change fallback from `unit.facing` to `unit.torsoFacing`.
- `commitImpulse()`: return the declared torso facings so the caller can update `GameState`.

### TUI layer — `RenderData`

- Remove `torsoFacings` field from `RenderData`.
- `extractRenderData()` for `PhaseState.Attack`: derive torso facings from `GameState.units` plus the in-progress twist from `PhaseState.Attack.torsoFacing`.

### TUI layer — `TuiApp`

- Remove `attackController.clearTorsoFacings()` call at `TurnPhase.END`.
- At `TurnPhase.END`, reset all units' torso facing: `unit.copy(torsoFacing = unit.facing)`.
- When committing an impulse, update `GameState` units with their declared torso facings.

### TUI layer — `BoardView` / `UnitRenderer`

- Derive torso facing from `GameState` units directly instead of `RenderData.torsoFacings`.

## Data Flow

### During attack declaration (unchanged)

`PhaseState.Attack.torsoFacing` holds the in-progress twist — same as today. This is ephemeral editing state, consistent with how movement browsing works.

### On impulse commit

```
commitImpulse() returns Map<UnitId, HexDirection>
    -> caller updates GameState: unit.copy(torsoFacing = declaredTorsoFacing)
```

### On rendering

- Units not currently being edited: read `unit.torsoFacing` from `GameState`.
- Unit currently being edited: use `PhaseState.Attack.torsoFacing` (override).

### On TurnPhase.END

```
gameState.units.map { unit -> unit.copy(torsoFacing = unit.facing) }
```

## Design Rationale

- **In-progress twist stays in `PhaseState.Attack`**: cancellation discards the twist without rollback logic. Same pattern as movement (browse paths in PhaseState, commit to GameState).
- **Eliminates mutable state**: the `committedTorsoFacings` mutable map is replaced by immutable `GameState` updates.
- **Cross-phase availability**: torso facing is now accessible to physical attack, heat, and any future phase.
