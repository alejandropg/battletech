# Unit Movement System

## Overview

Movement system for the tactical layer that computes, for each unit, all hexagons reachable by walking, running, or jumping, along with the path to reach each destination. Integrates with the existing action system as one action per movement mode, with a reachability map attached to each action's preview.

## Movement Modes

Three movement modes, each returned as a separate action in the movement phase:

| Mode | MP Source | Terrain Cost | Facing Cost | Notes |
|------|-----------|-------------|-------------|-------|
| WALK | `walkingMP` | Full terrain + elevation | 1 MP per 60° turn | Standard movement |
| RUN | `runningMP` | Full terrain + elevation | 1 MP per 60° turn | Extended range |
| JUMP | `jumpMP` | 1 JP per hex (flat) | Free at landing | Ignores intervening terrain |

## New Model Types

### HexDirection

Enum with 6 facing directions: `N`, `NE`, `SE`, `S`, `SW`, `NW`.

Methods:
- `rotateClockwise(): HexDirection` — next direction CW
- `rotateCounterClockwise(): HexDirection` — next direction CCW
- `turnCostTo(other: HexDirection): Int` — minimum number of 60° turns to reach target facing

### MovementMode

Enum: `WALK`, `RUN`, `JUMP`.

### HexCoordinates additions

- `neighbors(): List<HexCoordinates>` — all 6 adjacent hex coordinates
- `neighbor(direction: HexDirection): HexCoordinates` — adjacent hex in given direction

### Unit additions

New fields on `Unit`:
- `walkingMP: Int` — walking movement points
- `runningMP: Int` — running movement points
- `jumpMP: Int = 0` — jump movement points (0 means cannot jump)

## Terrain Movement Cost

Cost to **enter** a hex, calculated from terrain type and elevation change:

### Terrain base costs

| Terrain | MP Cost |
|---------|---------|
| CLEAR | 1 |
| LIGHT_WOODS | 2 |
| HEAVY_WOODS | 3 |
| WATER | 2 |

### Elevation cost

Added on top of terrain cost:
- Going **up**: +1 MP per level climbed
- Going **down**: free (0 extra cost)

Example: entering a light woods hex at elevation 2 from elevation 0 = 2 (terrain) + 2 (elevation) = 4 MP.

### Jump cost

Jumping ignores terrain and elevation costs. Each hex of distance costs exactly 1 JP.

## Pathfinding Algorithm

### Walk/Run: Dijkstra over (hex, facing) state space

**State**: `MovementState(position: HexCoordinates, facing: HexDirection)`

**Transitions from a state** (each consumes MP):
1. **Turn clockwise** — same position, facing rotated 60° CW — costs 1 MP
2. **Turn counter-clockwise** — same position, facing rotated 60° CCW — costs 1 MP
3. **Move forward** — neighbor hex in current facing direction, same facing — costs `movementCost(from, to)` MP

**Constraints**:
- Cannot enter a hex occupied by an enemy unit
- Can move through a hex occupied by a friendly unit, but cannot stop there
- Total MP spent cannot exceed the mode's MP budget

**Output**: all `(hex, facing)` endpoints reachable within MP budget, each with cost and path. Hexes occupied by friendly units are excluded from destinations.

### Jump: Distance check

Any hex within `jumpMP` hex distance (using `HexCoordinates.distanceTo()`) that is not occupied by any unit is a valid destination. The unit can choose any facing at the landing hex. No path is computed (direct jump).

## Result Data Structures

Located in `tactical/movement/` package:

```kotlin
data class MovementState(
    val position: HexCoordinates,
    val facing: HexDirection
)

data class MovementStep(
    val position: HexCoordinates,
    val facing: HexDirection
)

data class ReachableHex(
    val position: HexCoordinates,
    val facing: HexDirection,
    val mpSpent: Int,
    val path: List<MovementStep>
)

data class ReachabilityMap(
    val mode: MovementMode,
    val maxMP: Int,
    val destinations: List<ReachableHex>
)
```

### ReachabilityCalculator

Core service in `tactical/movement/`. Takes `GameMap` and unit list as inputs, produces `ReachabilityMap`.

Methods:
- Walk/run reachability — Dijkstra algorithm
- Jump reachability — distance-based check

## Action System Integration

### ActionContext change

Add optional field:
```kotlin
val movementMode: MovementMode? = null
```

### ActionPreview change

Add optional field:
```kotlin
val reachability: ReachabilityMap? = null
```

### MoveActionDefinition rework

- **Phase**: `MOVEMENT`
- **expand()**: returns up to 3 `ActionContext` entries — one per available movement mode:
  - WALK if `walkingMP > 0`
  - RUN if `runningMP > 0`
  - JUMP if `jumpMP > 0`
- **rules**: list of rules that can make a mode unavailable (extensible for future rules like "legs destroyed")
- **preview()**: calls `ReachabilityCalculator` to compute the reachability map for the given mode

### Flow

1. `ActionQueryService.getActions(unit, MOVEMENT, gameState)`
2. `MoveActionDefinition.expand()` produces up to 3 contexts (walk/run/jump)
3. Rules evaluate each context
4. For available actions, `preview()` computes the reachability map
5. Result: `PhaseActionReport` with up to 3 `AvailableAction` entries, each containing a `ReachabilityMap` in its preview

## Unit Blocking Rules

- **Enemy units**: completely block the hex — cannot enter or pass through
- **Friendly units**: can pass through but cannot end movement on their hex
- **Empty hexes**: valid for both traversal and destination

## Package Structure

```
tactical/src/main/kotlin/battletech/tactical/
  model/
    HexDirection.kt          (new)
    HexCoordinates.kt        (modified — add neighbors)
    MovementMode.kt           (new)
  movement/                    (new package)
    MovementState.kt
    MovementStep.kt
    ReachableHex.kt
    ReachabilityMap.kt
    ReachabilityCalculator.kt
  action/
    ActionContext.kt           (modified — add movementMode)
    ActionPreview.kt           (modified — add reachability)
    Unit.kt                    (modified — add MP fields)
    definition/
      MoveActionDefinition.kt  (reworked)
```

## Testing Strategy

### Model tests
- `HexDirection`: rotation, turn cost calculations (e.g., N to SE = 2)
- `HexCoordinates.neighbors()`: verify 6 neighbors for even/odd columns
- Movement cost: terrain and elevation combinations

### ReachabilityCalculator tests
- Walk on open flat map — verify expected reachable hex ring
- Terrain costs — woods reduce reachable area
- Elevation — climbing reduces range, descending is free
- Facing/turning — turning costs MP, reflected in paths
- Enemy blocking — paths cannot enter enemy hexes
- Friendly traversal — paths can cross friendly hexes, but friendlies are not destinations
- Jump reachability — all hexes within jump distance reachable, any facing valid

### Action integration tests
- `MoveActionDefinition.expand()` returns correct modes based on unit MP
- `preview()` contains populated `ReachabilityMap`
- `ActionQueryService` full flow for movement phase

### Test fixture updates
- `aUnit()` gets default MP values
- Helpers for building small test maps with specific terrain layouts
