# Simplified Attack Phase Interaction

## Overview

Merge the two-step attack flow (torso facing selection, then weapon assignment) into a single step where the player can twist the torso and assign weapons simultaneously. This eliminates the Enter press that previously locked the torso before weapon selection.

## Current Flow (Before)

1. Select unit + Enter → `TorsoFacing` state
2. Left/Right arrows twist torso (up/down ignored)
3. Enter → locks torso, transitions to `WeaponSelection` state
4. Up/Down arrows navigate weapons, Enter toggles assignment
5. Esc → save and back to Idle

## New Flow (After)

1. Select unit + Enter → `Declaring` state
2. Left/Right arrows twist torso, Up/Down arrows navigate weapons (simultaneously)
3. Space toggles weapon assignment at cursor
4. Enter → save assignments, back to Idle
5. Esc → discard assignments, back to Idle

## State Model

Replace `TorsoFacing` and `WeaponSelection` with a single `Declaring` state:

```kotlin
PhaseState.Attack
  └── Declaring(
        unitId, attackPhase, torsoFacing, arc, validTargetIds,
        targets: List<TargetInfo>,
        cursorTargetIndex: Int,
        cursorWeaponIndex: Int,
        weaponAssignments: Map<UnitId, Set<Int>>,
        primaryTargetId: UnitId?
      )
```

`enter()` returns `Declaring` directly with cursor on the first available weapon and empty assignments (or restored assignments if re-entering a unit with the same torso angle).

## Input Handling

All inputs handled in a single `handleDeclaring()` method:

| Input | Action |
|---|---|
| **Left/Right arrows** | Twist torso (same 1-hex-side clamping). Recompute arc, valid targets, and target info. Clear weapon assignments for targets that left the arc; reset `primaryTargetId` if the primary left. Clamp cursor if targets shrunk. |
| **Up/Down arrows** | Navigate the flat weapon list (same `navigateWeapons()` wrapping logic, minus the "No Attack" sentinel). |
| **Space** | Toggle weapon assignment at cursor. First assignment sets `primaryTargetId`. |
| **Enter** | Save assignments to `currentImpulse` and return `Cancelled` (back to Idle). If no weapons assigned, record as `NO_ATTACK`. |
| **Tab** | Jump cursor to first weapon of next target (round-robin). |
| **Esc** | Discard assignments for this unit, return `Cancelled` (back to Idle). |
| **ClickHex** | Jump cursor to clicked target's first weapon. |

## Torso Twist with Assignment Cleanup

When the torso is twisted:

1. Compute new torso facing (clamped to 1-hex-side from leg facing)
2. Recompute forward arc via `FiringArc.forwardArc()`
3. Recompute valid targets and `TargetInfo` list
4. For any target that left the arc: remove its weapon assignments from `weaponAssignments`
5. If `primaryTargetId` is no longer valid: reset to `null` (next Space assignment sets a new primary)
6. Clamp `cursorTargetIndex` if the targets list shrunk

Targets that remain in the arc keep their assignments intact.

## Rendering

- **Targets panel**: Always shown with full interactivity (cursor, toggles, `[P]`/`[S]` tags). No read-only preview mode.
- **"No Attack" sentinel**: Removed from the weapon list. Pressing Enter with no assignments is an implicit "No Attack".
- **Board**: No change — arc highlights, torso indicator, and red target markers are already computed from the common `Attack` properties in `extractRenderData()`.

## PhaseOutcome

No changes to `PhaseOutcome`. Both Enter and Esc return `Cancelled` (back to Idle). The distinction is:
- Enter saves to `currentImpulse` before returning
- Esc does not save

## File Changes

### TUI Module — Modified Files

| File | Change |
|------|--------|
| `PhaseState.kt` | Remove `TorsoFacing` and `WeaponSelection`. Add `Declaring` with all combined fields. |
| `AttackController.kt` | Merge `handleTorsoFacing` + `handleWeaponSelection` into `handleDeclaring`. `enter()` returns `Declaring`. Add Space input handling, change Enter/Esc semantics. |
| `TargetsView.kt` | Remove `showNoAttack` parameter and "No Attack" rendering. Remove read-only mode (`cursorTargetIndex = -1` path). |
| `Main.kt` | Update rendering to always pass interactive targets panel for `Declaring`. Remove `TorsoFacing`/`WeaponSelection` branching. |
| `RenderData.kt` | No change needed — already handles `is PhaseState.Attack` uniformly. |

### Test Files — Modified

| File | Change |
|------|--------|
| `AttackControllerTest.kt` | Update for single `Declaring` state, Space for toggle, Enter for confirm, Esc for discard. Remove TorsoFacing→WeaponSelection transition tests. |
| `TargetsViewTest.kt` | Remove "No Attack" sentinel and read-only rendering test cases. |
