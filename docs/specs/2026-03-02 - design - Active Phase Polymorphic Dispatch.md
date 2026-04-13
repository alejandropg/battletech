# Active Phase Polymorphic Dispatch

## Overview

Replace the `when(phase)` dispatch in TuiApp with polymorphic `ActivePhase` objects that bundle phase state with event processing behavior. Extract idle handling, unit selection, and attack commitment out of TuiApp into focused phase handler classes. Eliminate the mutable `pendingFlash` side-channel by making handlers return flash messages as data.

## Problem

TuiApp is ~500 lines with multiple intertwined responsibilities:

1. **Big `when(phase)` dispatch** — 4 branches, each with event mapping + cursor logic + controller delegation. Adding a phase requires modifying TuiApp.
2. **Idle handling lives in TuiApp** — `handleIdle`, `trySelectUnit`, `commitAttackImpulse` (~200 lines) are private methods on TuiApp, not in a dedicated controller.
3. **`pendingFlash` mutable field** — used as a side-channel between `handleIdle`/`trySelectUnit` and the render loop. Flash messages like "Not your unit" or "Already moved" are set imperatively, not returned as data.
4. **Ad-hoc post-processing** — the attack branch has special logic to refresh the idle prompt after returning from attack. This is a symptom of the dispatch being in TuiApp rather than encapsulated per-phase.

## Design

### Core Types

Two new abstractions drive the design:

```kotlin
// Replaces pendingFlash — handlers return flash as data
data class HandleResult(
    val appState: AppState,
    val flash: FlashMessage? = null,
)

// Bundles phase state + event processing behavior
interface ActivePhase {
    val state: PhaseState          // pure data, used by rendering
    fun processEvent(
        event: InputEvent,         // raw keyboard/mouse from Mordant
        appState: AppState,
    ): HandleResult?               // null = event not applicable (skip)
}
```

Each `ActivePhase` implementation:
1. Maps raw event to its state-specific action type (calls InputMapper internally)
2. Manages cursor updates for its state
3. Calls the appropriate controller
4. Returns `HandleResult` with new `AppState` + optional flash message

`PhaseState` stays unchanged as pure data — rendering code accesses it via `phase.state`.

### PhaseManager

`PhaseManager` is the bridge between controllers (which return `PhaseOutcome`) and the `ActivePhase` system. Two responsibilities:

**Factory** — creates `ActivePhase` objects:

```kotlin
class PhaseManager(
    val movementController: MovementController,
    val attackController: AttackController,
) {
    fun idle(prompt: String = "Move cursor to select a unit"): ActivePhase =
        IdlePhase(this, PhaseState.Idle(prompt))

    fun browsing(state: PhaseState.Movement.Browsing): ActivePhase =
        BrowsingPhase(this, state)

    fun facing(state: PhaseState.Movement.SelectingFacing): ActivePhase =
        FacingPhase(this, state)

    fun attack(state: PhaseState.Attack): ActivePhase =
        AttackPhase(this, state)

    // Single centralized mapping from PhaseState → ActivePhase
    fun wrap(phaseState: PhaseState): ActivePhase = when (phaseState) {
        is PhaseState.Idle -> idle(phaseState.prompt)
        is PhaseState.Movement.Browsing -> browsing(phaseState)
        is PhaseState.Movement.SelectingFacing -> facing(phaseState)
        is PhaseState.Attack -> attack(phaseState)
    }
}
```

**Outcome conversion** — translates `PhaseOutcome` (from controllers) into `HandleResult`:

```kotlin
// Inside PhaseManager:
fun fromOutcome(outcome: PhaseOutcome, appState: AppState): HandleResult =
    when (outcome) {
        is PhaseOutcome.Continue ->
            HandleResult(appState.copy(phase = wrap(outcome.phaseState)))

        is PhaseOutcome.Complete ->
            handleComplete(outcome, appState)

        is PhaseOutcome.Cancelled -> {
            val prompt = contextualIdlePrompt(appState)
            HandleResult(appState.copy(phase = idle(prompt)))
        }
    }
```

This absorbs the current free `handlePhaseOutcome()` function. The `handleComplete()` logic (turn state advancement, movement completion) and `contextualIdlePrompt()` (movement/attack prompts) also move into PhaseManager.

Note: `wrap()` contains the only `when(phaseState)` in the codebase. This is unavoidable because controllers return `PhaseState` in `PhaseOutcome.Continue`, not `ActivePhase`. Controllers stay pure — they don't know about `ActivePhase` or `PhaseManager`.

### Phase Implementations

#### IdlePhase

The biggest extraction — absorbs `handleIdle`, `trySelectUnit`, and `commitAttackImpulse` from TuiApp:

```kotlin
class IdlePhase(
    private val manager: PhaseManager,
    override val state: PhaseState.Idle,
) : ActivePhase {

    override fun processEvent(event: InputEvent, appState: AppState): HandleResult? {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapIdleEvent(event)
            is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                ?.let { IdleAction.ClickHex(it) }
        } ?: return null

        return when (action) {
            is IdleAction.MoveCursor -> {
                val newCursor = moveCursor(appState.cursor, action.direction, appState.gameState.map)
                HandleResult(appState.copy(cursor = newCursor))
            }
            is IdleAction.ClickHex -> {
                val updated = appState.copy(cursor = action.coords)
                trySelectUnit(updated)
            }
            is IdleAction.SelectUnit -> trySelectUnit(appState)
            is IdleAction.CycleUnit -> cycleUnit(appState)
            is IdleAction.CommitDeclarations -> commitDeclarations(appState)
        }
    }

    private fun trySelectUnit(appState: AppState): HandleResult {
        // Validates unit selection, returns HandleResult with flash on error
        // On success: enters movement/attack phase via manager
    }

    private fun commitDeclarations(appState: AppState): HandleResult {
        // Was commitAttackImpulse in TuiApp
        // Commits attack impulse, resolves attacks, advances phase
    }

    private fun cycleUnit(appState: AppState): HandleResult {
        // Cycles to next selectable unit
    }
}
```

Key change: `trySelectUnit` returns `HandleResult(appState, flash = FlashMessage("Not your unit"))` instead of setting `pendingFlash` imperatively.

#### BrowsingPhase

Thin adapter around `MovementController.handle(BrowsingAction, Browsing)`:

```kotlin
class BrowsingPhase(
    private val manager: PhaseManager,
    override val state: PhaseState.Movement.Browsing,
) : ActivePhase {

    override fun processEvent(event: InputEvent, appState: AppState): HandleResult? {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapBrowsingEvent(event)
            is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                ?.let { BrowsingAction.ClickHex(it) }
        } ?: return null

        val newCursor = when (action) {
            is BrowsingAction.MoveCursor -> moveCursor(appState.cursor, action.direction, appState.gameState.map)
            is BrowsingAction.ClickHex -> action.coords
            else -> appState.cursor
        }
        val updated = appState.copy(cursor = newCursor)
        val outcome = manager.movementController.handle(action, state, newCursor, updated.gameState)
        return manager.fromOutcome(outcome, updated)
    }
}
```

#### FacingPhase

Keyboard-only, no cursor movement:

```kotlin
class FacingPhase(
    private val manager: PhaseManager,
    override val state: PhaseState.Movement.SelectingFacing,
) : ActivePhase {

    override fun processEvent(event: InputEvent, appState: AppState): HandleResult? {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapFacingEvent(event)
            is MouseEvent -> return null
        } ?: return null

        val outcome = manager.movementController.handle(action, state, appState.cursor, appState.gameState)
        return manager.fromOutcome(outcome, appState)
    }
}
```

#### AttackPhase

Thin adapter around `AttackController.handle(AttackAction, Attack)`:

```kotlin
class AttackPhase(
    private val manager: PhaseManager,
    override val state: PhaseState.Attack,
) : ActivePhase {

    override fun processEvent(event: InputEvent, appState: AppState): HandleResult? {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapAttackEvent(event)
            is MouseEvent -> InputMapper.mapMouseToHex(event, boardX = 2, boardY = 2)
                ?.let { AttackAction.ClickTarget(it) }
        } ?: return null

        val outcome = manager.attackController.handle(action, state, appState.cursor, appState.gameState)
        return manager.fromOutcome(outcome, appState)
    }
}
```

The post-attack prompt refresh (currently ad-hoc in TuiApp's attack branch) is absorbed by `PhaseManager.fromOutcome` when handling `Cancelled` — `contextualIdlePrompt` knows about attack phases.

### AppState Changes

```kotlin
data class AppState(
    val gameState: GameState,
    val currentPhase: TurnPhase,
    val cursor: HexCoordinates,
    val phase: ActivePhase,        // was phaseState: PhaseState
    val turnState: TurnState? = null,
)
```

All call sites that accessed `appState.phaseState` must change to `appState.phase.state`.

**Impact on equality**: `data class` equality for the `phase` field falls back to reference equality (since `ActivePhase` is an interface). This is fine — `AppState` isn't compared for equality anywhere in the codebase.

### autoAdvanceGlobalPhases

Currently a free function that creates `PhaseState.Idle`. Updated to accept `PhaseManager` and create `ActivePhase`:

```kotlin
fun autoAdvanceGlobalPhases(
    appState: AppState,
    phaseManager: PhaseManager,
    random: Random = Random,
): Pair<AppState, FlashMessage?>
```

Internally, all `PhaseState.Idle(prompt)` calls become `phaseManager.idle(prompt)` and all `appState.copy(phaseState = ...)` become `appState.copy(phase = ...)`.

The attack impulse initialization (currently in TuiApp's auto-advance block) also moves into `autoAdvanceGlobalPhases`, keeping TuiApp's main loop even thinner.

### Main Loop

TuiApp shrinks from ~500 lines to ~150:

```kotlin
class TuiApp {
    fun run() {
        val terminal = Terminal()
        val renderer = ScreenRenderer(terminal)
        val actionQueryService = ActionQueryService(...)
        val phaseManager = PhaseManager(
            movementController = MovementController(actionQueryService),
            attackController = AttackController(),
        )

        var appState = AppState(
            gameState = sampleGameState(),
            currentPhase = TurnPhase.INITIATIVE,
            cursor = HexCoordinates(0, 0),
            phase = phaseManager.idle(),
        )

        renderer.clear()

        try {
            terminal.enterRawMode(mouseTracking = MouseTracking.Normal).use { rawMode ->
                while (true) {
                    val currentSize = currentSize(terminal)

                    // Auto-advance global phases
                    val (advancedState, flash) = autoAdvanceGlobalPhases(appState, phaseManager)
                    if (flash != null) {
                        appState = advancedState
                        renderFrame(currentSize, renderer, appState, flash)
                        continue
                    }

                    renderFrame(currentSize, renderer, appState)

                    val event = rawMode.readEvent()
                    if (event is KeyboardEvent && InputMapper.isQuit(event)) break

                    // Polymorphic dispatch — no when by phase type
                    val result = appState.phase.processEvent(event, appState)
                        ?: continue

                    appState = result.appState
                    if (result.flash != null) {
                        renderFrame(currentSize, renderer, appState, result.flash)
                        continue
                    }
                }
            }
        } finally {
            renderer.cleanup()
        }
    }
}
```

**What moves out of TuiApp:**

| Current location | New location |
|---|---|
| `handleIdle()` | `IdlePhase.processEvent()` |
| `trySelectUnit()` | `IdlePhase.trySelectUnit()` |
| `commitAttackImpulse()` | `IdlePhase.commitDeclarations()` |
| `pendingFlash` field | Eliminated — returned in `HandleResult.flash` |
| `isAttackPhase()` | Utility function (or method on PhaseManager) |
| `buildAttackPrompt()` | Utility function (or method on PhaseManager) |
| Per-state cursor logic | Each `ActivePhase` implementation |
| Post-attack prompt refresh | `PhaseManager.fromOutcome()` |
| Attack impulse init in auto-advance block | `autoAdvanceGlobalPhases()` |

**What stays in TuiApp:**

- Main loop (render + read event + dispatch)
- `renderFrame()` (rendering is a separate concern)
- `sampleGameState()` (data setup)
- `currentSize()` (terminal helper)

## Files Changed

| File | Change |
|---|---|
| `game/PhaseManager.kt` | **New** — factory + outcome conversion |
| `game/ActivePhase.kt` | **New** — interface |
| `game/HandleResult.kt` | **New** — result type |
| `game/IdlePhase.kt` | **New** — idle event handling |
| `game/BrowsingPhase.kt` | **New** — movement browsing adapter |
| `game/FacingPhase.kt` | **New** — facing selection adapter |
| `game/AttackPhase.kt` | **New** — attack declaration adapter |
| `game/AppState.kt` | **Modified** — `phaseState: PhaseState` → `phase: ActivePhase`, `handlePhaseOutcome` removed, `autoAdvanceGlobalPhases` updated |
| `TuiApp.kt` | **Modified** — shrinks to ~150 lines, removes handleIdle/trySelectUnit/commitAttackImpulse/pendingFlash |
| `game/PhaseState.kt` | **Unchanged** — stays as pure data |
| `game/PhaseOutcome.kt` | **Unchanged** — stays as controller return type |
| `game/MovementController.kt` | **Unchanged** |
| `game/AttackController.kt` | **Unchanged** |
| `input/*.kt` | **Unchanged** |
| All rendering code | **Modified** — `appState.phaseState` → `appState.phase.state` |

## Files Unchanged

- `PhaseState.kt`, `PhaseOutcome.kt` — pure data types stay as-is
- `MovementController.kt`, `AttackController.kt` — controllers stay pure, unaware of `ActivePhase`
- `InputMapper.kt`, `IdleAction.kt`, `BrowsingAction.kt`, `FacingAction.kt`, `AttackAction.kt`
- `TurnState.kt`
- All tactical module code
