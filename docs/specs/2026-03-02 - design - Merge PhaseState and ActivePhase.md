# Merge PhaseState and ActivePhase Hierarchies

## Goal

Eliminate the parallel `ActivePhase` / `PhaseState` class hierarchies by adding `processEvent()` directly to `PhaseState` data classes. This removes five files, the `PhaseManager` wrapping ceremony, and the redundant `appState.phase.state` indirection.

## Current State

Two parallel hierarchies with a 1:1 mapping:

| `PhaseState` (data)           | `ActivePhase` (behavior)  |
|-------------------------------|--------------------------|
| `Idle`                        | `IdlePhase`              |
| `Movement.Browsing`           | `BrowsingPhase`          |
| `Movement.SelectingFacing`    | `FacingPhase`            |
| `Attack`                      | `AttackPhase`            |

`PhaseManager.wrap()` bridges them — every state transition creates a new `ActivePhase` wrapper around a `PhaseState`. The main loop calls `appState.phase.processEvent(event, appState)`, and rendering reads `appState.phase.state`.

## Design

### 1. Merged `PhaseState` type

`PhaseState` gains `processEvent()` as an abstract method. Each data class variant implements it with the logic currently in its corresponding `*Phase` class.

```kotlin
public sealed interface PhaseState {
    public val prompt: String

    public fun processEvent(
        event: InputEvent,
        appState: AppState,
        phaseManager: PhaseManager,
    ): HandleResult?

    public data class Idle(
        override val prompt: String = "Move cursor to select a unit",
    ) : PhaseState {
        override fun processEvent(event, appState, phaseManager): HandleResult? {
            // Logic from IdlePhase.processEvent() + private helpers
        }
    }

    public sealed interface Movement : PhaseState {
        // Shared movement properties (unitId, modes, currentModeIndex, reachability)

        public data class Browsing(...) : Movement {
            override fun processEvent(...) { /* from BrowsingPhase */ }
        }

        public data class SelectingFacing(...) : Movement {
            override fun processEvent(...) { /* from FacingPhase */ }
        }
    }

    public data class Attack(...) : PhaseState {
        override fun processEvent(...) { /* from AttackPhase */ }
    }
}
```

**Dependencies**: `PhaseManager` is passed as a parameter to `processEvent()` instead of held as a field. It carries `movementController`, `attackController`, and `random`.

**File organization**: All variants and their `processEvent()` implementations live in `PhaseState.kt` (~350 lines total).

### 2. `AppState` changes

```kotlin
public data class AppState(
    val gameState: GameState,
    val currentPhase: TurnPhase,
    val cursor: HexCoordinates,
    val phase: PhaseState,          // was: ActivePhase
    val turnState: TurnState? = null,
)
```

All `appState.phase.state` references simplify to `appState.phase`.

Structural equality improves: `PhaseState` data classes provide correct `equals()`/`hashCode()` (currently `ActivePhase` uses reference equality).

### 3. Main loop changes (TuiApp.kt)

```kotlin
// Before:
val result = appState.phase.processEvent(event, appState) ?: continue

// After:
val result = appState.phase.processEvent(event, appState, phaseManager) ?: continue
```

Rendering code simplifies:
- `appState.phase.state as? PhaseState.Attack` → `appState.phase as? PhaseState.Attack`
- `appState.phase.state.prompt` → `appState.phase.prompt`
- `extractRenderData(appState.phase.state, ...)` → `extractRenderData(appState.phase, ...)`

### 4. `PhaseManager` simplification

Delete all factory/wrapping methods: `idle()`, `browsing()`, `facing()`, `attack()`, `wrap()`.

Add `random: Random` (moved from `IdlePhase` constructor).

Keep: `fromOutcome()`, `handleComplete()`, `contextualIdlePrompt()`.

`fromOutcome()` simplifies — no `wrap()` call:
```kotlin
is PhaseOutcome.Continue ->
    HandleResult(appState.copy(phase = outcome.phaseState))

is PhaseOutcome.Cancelled ->
    HandleResult(appState.copy(phase = PhaseState.Idle(contextualIdlePrompt(appState))))
```

`PhaseManager` shrinks from ~104 lines to ~60 lines.

### 5. Files deleted

- `ActivePhase.kt`
- `IdlePhase.kt`
- `BrowsingPhase.kt`
- `FacingPhase.kt`
- `AttackPhase.kt`

### 6. `autoAdvanceGlobalPhases` changes

Currently calls `phaseManager.idle(prompt)` to create `ActivePhase` instances. After the merge, these become `PhaseState.Idle(prompt)` directly.

### 7. Test impact

- `PhaseManagerTest`, `IdlePhaseTest`, `AppStateTest` — update `ActivePhase` references to `PhaseState`, remove `.state` indirection
- `MovementControllerTest`, `AttackControllerTest` — unaffected (test controllers that return `PhaseOutcome`)
- `RenderDataTest` — unaffected (operates on `PhaseState` directly)

## Summary of changes

| Area | Before | After |
|------|--------|-------|
| Hierarchies | 2 parallel (`PhaseState` + `ActivePhase`) | 1 (`PhaseState`) |
| Files | 7 (`PhaseState` + `ActivePhase` + 4 wrappers + `PhaseManager`) | 2 (`PhaseState` + `PhaseManager`) |
| State access | `appState.phase.state` | `appState.phase` |
| Phase creation | `PhaseManager.idle()`, `.wrap()` | `PhaseState.Idle()` directly |
| Equality | Reference (broken) | Structural (correct) |
