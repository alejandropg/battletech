# Persistent Torso Facing Arrow

## Problem

During the attack phase, when a user twists a unit's torso, a small arrow is rendered next to the leg-facing arrow to indicate the torso direction. Currently, this arrow is only visible while the user is actively editing that unit's attack declaration (`PhaseState.Attack`). Once the user confirms and moves to another unit, the torso arrow disappears.

The torso facing arrow should remain visible on the board for all units that have a declared torso twist (torso facing != leg facing), regardless of which unit is currently being edited. It should persist through all attack sub-phases (weapon attack + physical attack) and disappear when the next turn begins.

## Scope

- All units with a declared torso twist show the persistent arrow, regardless of owning player.
- The arrow persists through weapon attack and physical attack phases.
- The arrow disappears when declarations are cleared (turn transition).

## Design

### AttackController Changes

**New field:**

```kotlin
private val committedTorsoFacings: MutableMap<UnitId, HexDirection> = mutableMapOf()
```

Accumulates torso facings from committed impulses.

**Lifecycle:**

- **Populated** in `commitImpulse()`: after existing logic, copy each declaration's `torsoFacing` into the map.
- **Cleared** in `clearDeclarations()`: clear alongside `allDeclarations`.

**New public method:**

```kotlin
fun declaredTorsoFacings(gameState: GameState): Map<HexCoordinates, HexDirection>
```

Merges committed + current impulse torso facings:

1. Iterate `committedTorsoFacings` — for each unit where `torsoFacing != unit.facing`, add to result.
2. Iterate `currentImpulse?.declarations` — overwrites committed entries for same unit. If torso equals leg facing, removes entry (torso was reset).
3. Returns `Map<HexCoordinates, HexDirection>` for rendering.

### Main.kt Integration

At the render data extraction point (currently line ~340):

```kotlin
val renderData = extractRenderData(appState.phaseState, appState.gameState)
val persistentTorso = attackController.declaredTorsoFacings(appState.gameState)
val mergedRenderData = renderData.copy(
    torsoFacings = persistentTorso + renderData.torsoFacings
)
```

**Precedence:** `renderData.torsoFacings` (from active `PhaseState.Attack`) overwrites `persistentTorso` for the currently-edited unit, so live torso twist always takes precedence.

### Rendering

No changes needed. The existing rendering pipeline (`BoardView` -> `UnitRenderer`) already handles `torsoFacings: Map<HexCoordinates, HexDirection>` and draws the small torso arrow when `torsoFacing != unit.facing`.

### Files Changed

| File | Change |
|------|--------|
| `AttackController.kt` | Add `committedTorsoFacings` field, populate in `commitImpulse()`, clear in `clearDeclarations()`, add `declaredTorsoFacings()` method |
| `Main.kt` | Merge persistent torso facings into render data before passing to board view |

### Testing

- Unit test for `declaredTorsoFacings()`: verify correct map from committed + current impulse, filtering where torso equals leg facing, and empty after `clearDeclarations()`.
