# Per-State Input Actions

## Overview

Replace the global `InputAction` sealed interface with per-state action types. Each `PhaseState` variant gets its own sealed action interface with semantically named actions. Key mappings are defined per-state, eliminating the semantic mismatch where the same action (e.g., `MoveCursor`) means different things in different states.

## Problem

The current `InputAction` is a universal action vocabulary, but different states give the same action completely different semantics:

- `MoveCursor(SE)` means "move map cursor" in Idle but "twist torso clockwise" in Attack
- `CycleUnit` means "next selectable unit" in Idle but "next target" in Attack
- This forces workarounds like `shouldMoveCursorOnArrow` in `TuiApp`
- Handler `when` blocks need `else ->` fallthrough for actions that don't apply

## Design

### Action Types

Four sealed interfaces replace `InputAction`, one per `PhaseState` variant:

```kotlin
sealed interface IdleAction {
    data class MoveCursor(val direction: HexDirection) : IdleAction
    data class ClickHex(val coords: HexCoordinates) : IdleAction
    data object SelectUnit : IdleAction
    data object CycleUnit : IdleAction
    data object CommitDeclarations : IdleAction
}

sealed interface BrowsingAction {
    data class MoveCursor(val direction: HexDirection) : BrowsingAction
    data class ClickHex(val coords: HexCoordinates) : BrowsingAction
    data object ConfirmPath : BrowsingAction
    data class SelectFacing(val index: Int) : BrowsingAction
    data object CycleMode : BrowsingAction
    data object Cancel : BrowsingAction
}

sealed interface FacingAction {
    data class SelectFacing(val index: Int) : FacingAction
    data object Cancel : FacingAction
}

sealed interface AttackAction {
    data class TwistTorso(val clockwise: Boolean) : AttackAction
    data class NavigateWeapons(val delta: Int) : AttackAction
    data object ToggleWeapon : AttackAction
    data object NextTarget : AttackAction
    data object Confirm : AttackAction
    data object Cancel : AttackAction
    data class ClickTarget(val coords: HexCoordinates) : AttackAction
}
```

### Key Mappings

`InputMapper` is rewritten with per-state mapping functions:

```kotlin
object InputMapper {
    fun isQuit(event: KeyboardEvent): Boolean =
        event.key == "q" || (event.ctrl && event.key == "c")

    fun mapIdleEvent(event: KeyboardEvent): IdleAction? = when (event.key) {
        "ArrowUp"    -> IdleAction.MoveCursor(HexDirection.N)
        "ArrowDown"  -> IdleAction.MoveCursor(HexDirection.S)
        "ArrowRight" -> IdleAction.MoveCursor(HexDirection.SE)
        "ArrowLeft"  -> IdleAction.MoveCursor(HexDirection.NW)
        "Enter"      -> IdleAction.SelectUnit
        "Tab"        -> IdleAction.CycleUnit
        "c"          -> IdleAction.CommitDeclarations
        else         -> null
    }

    fun mapBrowsingEvent(event: KeyboardEvent): BrowsingAction? = when (event.key) {
        "ArrowUp"    -> BrowsingAction.MoveCursor(HexDirection.N)
        "ArrowDown"  -> BrowsingAction.MoveCursor(HexDirection.S)
        "ArrowRight" -> BrowsingAction.MoveCursor(HexDirection.SE)
        "ArrowLeft"  -> BrowsingAction.MoveCursor(HexDirection.NW)
        "Enter"      -> BrowsingAction.ConfirmPath
        "Escape"     -> BrowsingAction.Cancel
        "Tab"        -> BrowsingAction.CycleMode
        in "1".."6"  -> BrowsingAction.SelectFacing(event.key.toInt())
        else         -> null
    }

    fun mapFacingEvent(event: KeyboardEvent): FacingAction? = when (event.key) {
        in "1".."6"  -> FacingAction.SelectFacing(event.key.toInt())
        "Escape"     -> FacingAction.Cancel
        else         -> null
    }

    fun mapAttackEvent(event: KeyboardEvent): AttackAction? = when (event.key) {
        "ArrowRight" -> AttackAction.TwistTorso(clockwise = true)
        "ArrowLeft"  -> AttackAction.TwistTorso(clockwise = false)
        "ArrowUp"    -> AttackAction.NavigateWeapons(delta = -1)
        "ArrowDown"  -> AttackAction.NavigateWeapons(delta = 1)
        " "          -> AttackAction.ToggleWeapon
        "Tab"        -> AttackAction.NextTarget
        "Enter"      -> AttackAction.Confirm
        "Escape"     -> AttackAction.Cancel
        else         -> null
    }

    fun mapMouseToHex(event: MouseEvent, boardX: Int, boardY: Int): HexCoordinates? {
        if (!event.left) return null
        val x = event.x - boardX
        val y = event.y - boardY
        if (x < 0 || y < 0) return null
        return HexLayout.screenToHex(x, y, scrollX = 0, scrollY = 0)
    }
}
```

### Handler Signatures

Each handler receives its own action type:

- `handleIdle(action: IdleAction, appState: AppState, ...): AppState`
- `MovementController.handle(action: BrowsingAction, state: Browsing, ...): PhaseOutcome`
- `MovementController.handle(action: FacingAction, state: SelectingFacing, ...): PhaseOutcome`
- `AttackController.handle(action: AttackAction, state: Attack, ...): PhaseOutcome`

Handlers use exhaustive `when` — no `else` branch needed, compiler catches missing cases.

### Main Loop Dispatch

The main loop dispatches based on current `PhaseState`, maps the raw event to the state-specific action, and delegates to the handler:

```kotlin
val event = rawMode.readEvent()
if (event is KeyboardEvent && InputMapper.isQuit(event)) break

appState = when (val phase = appState.phaseState) {
    is PhaseState.Idle -> {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapIdleEvent(event)
            is MouseEvent -> InputMapper.mapMouseToHex(event, boardX, boardY)
                ?.let { IdleAction.ClickHex(it) }
        } ?: continue
        handleIdle(action, appState, movementController, attackController)
    }

    is PhaseState.Movement.Browsing -> {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapBrowsingEvent(event)
            is MouseEvent -> InputMapper.mapMouseToHex(event, boardX, boardY)
                ?.let { BrowsingAction.ClickHex(it) }
        } ?: continue
        val newCursor = when (action) {
            is BrowsingAction.MoveCursor -> moveCursor(appState.cursor, action.direction, appState.gameState.map)
            is BrowsingAction.ClickHex -> action.coords
            else -> appState.cursor
        }
        val updated = appState.copy(cursor = newCursor)
        handlePhaseOutcome(
            movementController.handle(action, phase, newCursor, updated.gameState),
            updated,
        )
    }

    is PhaseState.Movement.SelectingFacing -> {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapFacingEvent(event)
            is MouseEvent -> null
        } ?: continue
        handlePhaseOutcome(
            movementController.handle(action, phase, appState.cursor, appState.gameState),
            appState,
        )
    }

    is PhaseState.Attack -> {
        val action = when (event) {
            is KeyboardEvent -> InputMapper.mapAttackEvent(event)
            is MouseEvent -> InputMapper.mapMouseToHex(event, boardX, boardY)
                ?.let { AttackAction.ClickTarget(it) }
        } ?: continue
        val outcome = attackController.handle(action, phase, appState.cursor, appState.gameState)
        handlePhaseOutcome(outcome, appState)
    }
}
```

### Cursor Ownership

Each state manages cursor updates explicitly:

- **Idle**: `handleIdle` returns `AppState` with updated cursor directly
- **Browsing**: main loop updates cursor for `MoveCursor`/`ClickHex` before calling handler (explicit per-state, not a global hack)
- **SelectingFacing**: no cursor movement
- **Attack**: no cursor movement (arrows twist torso / navigate weapons)

## Files Changed

| File | Change |
|------|--------|
| `input/InputAction.kt` | **Deleted** |
| `input/IdleAction.kt` | **New** |
| `input/BrowsingAction.kt` | **New** |
| `input/FacingAction.kt` | **New** |
| `input/AttackAction.kt` | **New** |
| `input/InputMapper.kt` | **Rewritten** |
| `game/MovementController.kt` | **Modified** — split `handle()` into two overloads |
| `game/AttackController.kt` | **Modified** — new action type, remove direction translation |
| `TuiApp.kt` | **Modified** — rewrite dispatch, remove cursor hack |

## Files Unchanged

- `PhaseState.kt`, `PhaseOutcome.kt`, `AppState.kt`, `TurnState.kt`
- `handlePhaseOutcome()`, `autoAdvanceGlobalPhases()`
- All rendering code
- All tactical module code
