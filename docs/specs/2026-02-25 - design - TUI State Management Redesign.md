# TUI State Management Redesign

## Problem

The current TUI state management has several issues:

1. **PhaseState is a "god state"** — a single flat data class with 10 fields (most nullable) serving all phases. Movement sub-states (browsing vs. facing selection) are encoded as nullable sentinels (`facingSelectionHex != null`).
2. **Dual ownership of "selected unit"** — tracked in `CursorState.selectedUnitId`, `PhaseState.selectedUnitId`, and via implicit cursor-position lookup in `Main.kt`.
3. **GameLoop is hollow** — two public `var` fields mutated from `Main.kt`. The real loop logic lives in `Main.kt`.
4. **PhaseController interface lies** — `enter(unit)` forces every phase to accept a unit, even Initiative/Heat/End which are global phases.
5. **MovementPhaseController escapes its interface** — `cycleMode` and `updatePathForCursor` called via type casts in `Main.kt`, breaking polymorphism.
6. **Rendering logic in state** — `hexHighlights()` and `facingsByPosition` are view concerns baked into `PhaseState`.
7. **Dead code** — `GameLoopResult.PhaseComplete`, `InputAction.ScrollBoard`, unused `MovementResolver`.

## Design

### PhaseState Sealed Hierarchy

Replace the flat nullable `PhaseState` with a sealed hierarchy where each phase has its own typed state. Illegal state combinations become unrepresentable.

```kotlin
sealed interface PhaseState {
    val prompt: String

    // No unit selected, just browsing the map
    data class Idle(
        override val prompt: String = "Move cursor to select a unit",
    ) : PhaseState

    // Movement phase — two distinct sub-states
    sealed interface Movement : PhaseState {
        val unitId: UnitId
        val modes: List<ReachabilityMap>
        val currentModeIndex: Int
        val reachability: ReachabilityMap get() = modes[currentModeIndex]

        // Browsing reachable hexes, cursor shows path preview
        data class Browsing(
            override val unitId: UnitId,
            override val modes: List<ReachabilityMap>,
            override val currentModeIndex: Int,
            val hoveredPath: List<HexCoordinates>?,
            val hoveredDestination: ReachableHex?,
            override val prompt: String,
        ) : Movement

        // Hex chosen, picking which facing
        data class SelectingFacing(
            override val unitId: UnitId,
            override val modes: List<ReachabilityMap>,
            override val currentModeIndex: Int,
            val hex: HexCoordinates,
            val options: List<ReachableHex>,
            val path: List<HexCoordinates>,
            override val prompt: String,
        ) : Movement
    }

    // Attack phase (weapon or physical)
    data class Attack(
        val unitId: UnitId,
        val attackPhase: TurnPhase,
        override val prompt: String,
    ) : PhaseState
}
```

**What this fixes:**

- `unitId` is non-null in every state that has a unit — no more `UnitId?`
- Movement browsing vs. facing selection are distinct types — no nullable sentinels
- `reachability` is always derived from `modes[currentModeIndex]` — no divergence possible
- Attack state doesn't carry movement fields
- Idle state carries nothing unnecessary

### Controller Interfaces

Typed controllers per phase — no shared interface. Each controller has the signature its phase needs.

```kotlin
sealed interface PhaseOutcome {
    data class Continue(val phaseState: PhaseState) : PhaseOutcome
    data class Complete(val gameState: GameState) : PhaseOutcome
    data object Cancelled : PhaseOutcome
}

interface MovementController {
    fun enter(unit: Unit, gameState: GameState): PhaseState.Movement.Browsing
    fun handle(
        action: InputAction,
        state: PhaseState.Movement,
        cursor: HexCoordinates,
        gameState: GameState,
    ): PhaseOutcome
}

interface AttackController {
    fun enter(unit: Unit, phase: TurnPhase, gameState: GameState): PhaseState.Attack
    fun handle(
        action: InputAction,
        state: PhaseState.Attack,
        gameState: GameState,
    ): PhaseOutcome
}
```

**Key decisions:**

- **No shared `PhaseController` interface** — Movement needs cursor position, Attack doesn't. Different signatures for different needs.
- **`enter` returns the concrete type** — `MovementController.enter` returns `Browsing`, not a generic `PhaseState`.
- **Cursor is a parameter, not owned state** — the controller receives cursor position to compute paths but doesn't own cursor movement.
- **No global-phase controllers** — Initiative/Heat/End are auto-advance functions (see below).

The movement controller's `handle` receives `PhaseState.Movement` (the sealed parent), so it can pattern-match internally on `Browsing` vs `SelectingFacing`. All movement sub-state logic stays inside the controller — no type-casting in `Main.kt`.

### AppState and Main Loop

Single top-level state, thin dispatch loop.

```kotlin
data class AppState(
    val gameState: GameState,
    val currentPhase: TurnPhase,
    val cursor: HexCoordinates,
    val phaseState: PhaseState,
)
```

The main loop:

```kotlin
while (running) {
    // Auto-advance global phases (Initiative, Heat, End)
    val (advancedState, flash) = autoAdvanceGlobalPhases(appState)
    if (flash != null) {
        appState = advancedState
        renderWithFlash(appState, flash)
        continue
    }

    render(appState)
    val action = readInput() ?: continue
    if (action is InputAction.Quit) break

    // Cursor movement — always handled here regardless of phase
    if (action is InputAction.MoveCursor) {
        val newCursor = moveCursor(appState.cursor, action.direction, appState.gameState.map)
        appState = appState.copy(cursor = newCursor)
        // If in movement phase, update path preview for new cursor
        val phase = appState.phaseState
        if (phase is PhaseState.Movement) {
            val outcome = movementController.handle(action, phase, newCursor, appState.gameState)
            if (outcome is PhaseOutcome.Continue) {
                appState = appState.copy(phaseState = outcome.phaseState)
            }
        }
        continue
    }

    // Phase-specific dispatch
    appState = when (val phase = appState.phaseState) {
        is PhaseState.Idle -> handleIdle(action, appState)
        is PhaseState.Movement -> handlePhaseOutcome(
            movementController.handle(action, phase, appState.cursor, appState.gameState),
            appState,
        )
        is PhaseState.Attack -> handlePhaseOutcome(
            attackController.handle(action, phase, appState.gameState),
            appState,
        )
    }
}

fun handlePhaseOutcome(outcome: PhaseOutcome, appState: AppState): AppState = when (outcome) {
    is PhaseOutcome.Continue -> appState.copy(phaseState = outcome.phaseState)
    is PhaseOutcome.Complete -> appState.copy(
        gameState = outcome.gameState,
        currentPhase = nextPhase(appState.currentPhase),
        phaseState = PhaseState.Idle(),
    )
    is PhaseOutcome.Cancelled -> appState.copy(phaseState = PhaseState.Idle())
}
```

**What this fixes:**

- No more `var phaseState: PhaseState? = null` — always non-null (`Idle` replaces null)
- No more separate `CursorState` — cursor is in `AppState`
- No type-casting controllers (`if (controller is MovementPhaseController)`)
- No duplicated `when (result)` blocks — `handlePhaseOutcome` handles all three cases once
- `GameLoop` class is gone — the loop is just the `while` block with `AppState` updates
- `CursorState` class is gone — just a `HexCoordinates`

### Global Phase Auto-Advance

Initiative, Heat, and End phases auto-advance with a brief flash message. No controllers needed.

```kotlin
data class FlashMessage(val text: String)

fun autoAdvanceGlobalPhases(appState: AppState): Pair<AppState, FlashMessage?> {
    return when (appState.currentPhase) {
        TurnPhase.INITIATIVE -> {
            val state = appState.copy(currentPhase = nextPhase(TurnPhase.INITIATIVE))
            state to FlashMessage("Initiative resolved")
        }
        TurnPhase.HEAT -> {
            val oldUnits = appState.gameState.units
            val newGameState = applyHeatDissipation(appState.gameState)
            val details = oldUnits.zip(newGameState.units)
                .filter { (old, _) -> old.currentHeat > 0 }
                .joinToString(", ") { (old, new) ->
                    "${old.name}: ${old.currentHeat}→${new.currentHeat}"
                }
                .ifEmpty { "No heat to dissipate" }
            val state = appState.copy(
                gameState = newGameState,
                currentPhase = nextPhase(TurnPhase.HEAT),
            )
            state to FlashMessage("Heat: $details")
        }
        TurnPhase.END -> {
            val state = appState.copy(currentPhase = nextPhase(TurnPhase.END))
            state to FlashMessage("Turn complete")
        }
        else -> appState to null
    }
}

fun applyHeatDissipation(gameState: GameState): GameState {
    val updatedUnits = gameState.units.map { unit ->
        val newHeat = maxOf(0, unit.currentHeat - unit.heatSinkCapacity)
        unit.copy(currentHeat = newHeat)
    }
    return gameState.copy(units = updatedUnits)
}
```

Note: heat dissipation now applies to ALL units (correct BattleTech rules), not just the selected one as in the current implementation.

### Rendering Data Extraction

Rendering logic moves out of state classes into a pure function that pattern-matches on `PhaseState`.

```kotlin
fun extractRenderData(phaseState: PhaseState): RenderData {
    return when (phaseState) {
        is PhaseState.Idle -> RenderData.EMPTY
        is PhaseState.Movement.Browsing -> RenderData(
            hexHighlights = reachabilityHighlights(phaseState.reachability)
                + pathHighlights(phaseState.hoveredPath),
            reachableFacings = phaseState.reachability.facingsByPosition(),
        )
        is PhaseState.Movement.SelectingFacing -> RenderData(
            hexHighlights = reachabilityHighlights(phaseState.modes[phaseState.currentModeIndex])
                + pathHighlights(phaseState.path),
            facingSelection = FacingSelection(
                phaseState.hex,
                phaseState.options.map { it.facing }.toSet(),
            ),
            reachableFacings = phaseState.modes[phaseState.currentModeIndex].facingsByPosition(),
        )
        is PhaseState.Attack -> RenderData.EMPTY
    }
}

data class RenderData(
    val hexHighlights: Map<HexCoordinates, HexHighlight> = emptyMap(),
    val reachableFacings: Map<HexCoordinates, Set<HexDirection>> = emptyMap(),
    val facingSelection: FacingSelection? = null,
) {
    companion object {
        val EMPTY = RenderData()
    }
}

data class FacingSelection(
    val hex: HexCoordinates,
    val facings: Set<HexDirection>,
)
```

Usage in main loop:

```kotlin
val renderData = extractRenderData(appState.phaseState)
val boardView = BoardView(
    appState.gameState, viewport,
    cursorPosition = appState.cursor,
    hexHighlights = renderData.hexHighlights,
    reachableFacings = renderData.reachableFacings,
    facingSelection = renderData.facingSelection,
    movementMode = (appState.phaseState as? PhaseState.Movement)?.reachability?.mode,
)
```

**What this fixes:**

- `PhaseState` is pure data — no rendering logic
- `BoardView` receives a clean `RenderData` instead of reaching into nullable state fields
- Easy to extend when attack phase adds its own highlights

## Cleanup

### Classes to Remove

| Class | Replacement |
|---|---|
| `GameLoop` | Inline `while` loop in `Main.kt` with `AppState` |
| `GameLoopResult` | Gone — quit is a `break`, phase outcomes are `PhaseOutcome` |
| `CursorState` | `HexCoordinates` field in `AppState` |
| `InitiativePhaseController` | `autoAdvanceGlobalPhases` function |
| `HeatPhaseController` | `autoAdvanceGlobalPhases` + `applyHeatDissipation` function |
| `EndPhaseController` | `autoAdvanceGlobalPhases` function |
| Old `PhaseState` (flat) | New sealed `PhaseState` hierarchy |
| Old `PhaseController` interface | `MovementController` and `AttackController` interfaces |
| Old `PhaseControllerResult` | `PhaseOutcome` sealed interface |

### Dead Code to Remove

| Item | Reason |
|---|---|
| `GameLoopResult.PhaseComplete` | Never produced anywhere |
| `InputAction.ScrollBoard` | Never produced by `InputMapper` |
| `MovementResolver` | Unused — `applyMovement` is inline in controller |

## Testing

### What to Test

- **PhaseState construction**: each variant can only be built with valid fields
- **MovementController**: `enter` returns `Browsing`, `handle` transitions between `Browsing`/`SelectingFacing`/`Complete`/`Cancelled` correctly
- **AttackController**: `enter` returns `Attack`, `handle` processes actions
- **`autoAdvanceGlobalPhases`**: produces correct flash messages and game state for each global phase
- **`applyHeatDissipation`**: reduces heat for all units correctly
- **`extractRenderData`**: each `PhaseState` variant produces correct highlights and facings
- **`handlePhaseOutcome`**: maps each outcome to correct `AppState` transition
- **Main loop integration**: verify dispatch routes actions to correct controller
