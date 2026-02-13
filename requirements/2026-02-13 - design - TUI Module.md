# TUI Module Design

## Summary

A new `tui/` module providing a rich terminal user interface for the BattleTech Rules Engine. The TUI renders an ASCII hex board with terrain, elevation, and units, and supports full game interaction through both keyboard and mouse input. Built on top of Mordant for terminal rendering.

## Library Choice: Mordant

**Library:** [ajalt/mordant](https://github.com/ajalt/mordant) v3.x
**Artifacts:** `mordant`, `mordant-coroutines`

Chosen over alternatives because:
- Kotlin-native API with coroutines
- Absolute cursor positioning via `setCursorPosition(x, y)`
- Mouse event support via `receiveEventsFlow()` (Kotlin Flow)
- Proper Unicode width handling
- JDK 22+ FFM backend (native terminal access on JVM 25)
- Actively maintained (v3.0.2)

Rejected alternatives:
- **Lanterna**: Java API, known Unicode wide-character alignment issues
- **Kotter**: No absolute cursor positioning, no mouse support
- **Zircon**: Not a TUI (opens Swing window)
- **Raw ANSI**: Massive reimplementation effort for no benefit

## Module Structure

```
battletech/
├── bt/                  # Existing - stays as-is
├── strategic/           # Existing
├── tactical/            # Existing
├── tui/                 # NEW - TUI application module
│   ├── build.gradle.kts
│   └── src/main/kotlin/battletech/tui/
└── settings.gradle.kts  # Add "tui" to includes
```

**`tui/build.gradle.kts`** uses the `battletech.kotlin-application` convention plugin with dependencies:
- `project(":tactical")` -- game state, units, hex coordinates, actions
- `project(":strategic")` -- if needed later
- `com.github.ajalt.mordant:mordant` -- terminal rendering, cursor, colors
- `com.github.ajalt.mordant:mordant-coroutines` -- mouse/keyboard event flows

Entry point: `battletech.tui.MainKt`

## Screen Layout

```
┌──────────────────────────────────┬──────────────────┐
│                                  │  UNIT INFO       │
│                                  │  Atlas AS7-D     │
│         HEX BOARD                │  Pilot: 4/5      │
│                                  │  Heat: 3/30      │
│    (scrollable viewport          │  ────────────    │
│     into the full map)           │  WEAPONS         │
│                                  │  ML  [R] ■■■     │
│                                  │  AC20[R] ■■■■■   │
│                                  │  SRM6[R] ■■■     │
│                                  │  ────────────    │
│                                  │  ARMOR           │
│                                  │  HD: ■■■■■■■■■   │
│                                  │  CT: ■■■■■■□□□   │
│                                  │  ...             │
├──────────────────────────────────┴──────────────────┤
│ [Movement Phase] Select destination for Atlas AS7-D │
│ Arrow keys: move cursor | Enter: confirm | Esc: back│
└─────────────────────────────────────────────────────┘
```

Three regions:
- **Board viewport** (top-left): Scrollable window into the hex map with terrain, elevation, and units
- **Sidebar** (top-right): Fixed-width panel showing selected unit details (weapons, armor, heat)
- **Status bar** (bottom): 2-3 lines showing current phase, action prompts, and keybindings

## Rendering Architecture

### Screen Buffer

A character-cell screen buffer with diff-based rendering to avoid flicker:

- `Cell` = character + foreground color + background color
- `ScreenBuffer` = 2D `Array<Array<Cell>>` representing the full terminal
- `ScreenRenderer` diffs current buffer against previous frame, uses Mordant's `setCursorPosition` + styled print to update only changed cells

### View Hierarchy

```
Screen (full terminal)
├── BoardView (viewport into hex grid)
│   ├── HexRenderer (converts Hex → character cells with terrain/elevation)
│   ├── UnitRenderer (overlays unit letter + facing on hex cells)
│   └── CursorRenderer (highlights selected hex)
├── SidebarView (unit info panel)
└── StatusBarView (phase + prompts)
```

Each view implements a `View` interface: `render(buffer, x, y, width, height)`.

## Hex Grid Rendering

### Hex Cell Geometry

Each hex occupies a fixed character area (7 chars wide, 4 rows tall):

```
  _____
 /     \
/       \
\       /
 \_____/
```

Hexes are offset by half-height for the staggered grid. The content area inside each hex has room for ~5 characters on the middle rows.

### Content Inside Hexes

- **Terrain**: emoji in top-left of content area (🌊🌿🌳) or colored fallback chars
- **Elevation**: number in top-right of content area
- **Unit**: letter (centered) + facing arrow when a unit occupies the hex (e.g., `B↖️`)
- **Cursor highlight**: hex border characters change color (yellow) or background fills

### Color Scheme

Using Mordant's ANSI 256/truecolor:
- Terrain drives background: blue (water), green (light woods), dark green (heavy woods), brown (rough), gray (pavement)
- Unit letters use player color (player 1 = cyan, player 2 = red)
- Cursor highlight: bright yellow border or inverted colors
- Movement/attack range overlay: dim highlight on reachable hexes

### Coordinate Mapping

`HexCoordinates(col, row)` → screen position `(charX, charY)`:
- `charX = col * 8` (7 chars + 1 gap, adjusted for offset)
- `charY = row * 4` with half-height offset for odd columns

The board viewport tracks a scroll offset and only renders hexes visible within the terminal dimensions.

## Input Handling

### Raw Mode

Using Mordant's raw mode with event flow:

```kotlin
terminal.enterRawMode {
    receiveEventsFlow().collect { event ->
        when (event) {
            is KeyboardEvent -> handleKeyboard(event)
            is MouseEvent -> handleMouse(event)
        }
    }
}
```

### Keyboard Mappings

- Arrow keys: move cursor between hexes
- Enter: confirm selection / execute action
- Escape: cancel / go back
- Tab: cycle through units
- Number keys: quick-select weapons or actions
- `q`: quit

### Mouse Mappings

- Click on hex: move cursor there / select unit
- Click on sidebar items: select weapon, toggle
- Right-click: context action (e.g., attack target)

### Screen-to-Hex Hit Testing

Mouse clicks convert `(screenX, screenY)` → `HexCoordinates` by reversing the coordinate mapping (accounting for scroll offset). Positions between hexes resolve to the nearest hex center.

## Game Loop

### Main Loop

```
1. Render current state
2. Wait for input event
3. Map input → game action (using ActionQueryService)
4. Apply action → new GameState
5. Advance phase if needed
6. Go to 1
```

### Phase Progression

```
Initiative → Movement → Weapon Attack → Physical Attack → Heat → End
    ↑_______________________________________________|
```

Each phase: select unit → show valid actions → player picks → apply → next unit or next phase.

### Integration with Tactical Domain

The TUI acts as a thin presentation layer:
- `ActionQueryService.getMovementActions(unit, gameState)` provides available movement options
- `ActionQueryService.getAttackActions(unit, phase, gameState)` provides available attack options
- The TUI highlights valid destinations/targets and lets the player choose
- The chosen action produces a new `GameState`, and the loop continues

## Package Structure

```
tui/src/main/kotlin/battletech/tui/
├── Main.kt                          # Entry point, terminal setup, main loop
├── screen/
│   ├── Cell.kt                      # Character + foreground + background color
│   ├── ScreenBuffer.kt              # 2D array of Cells, diff-based rendering
│   └── ScreenRenderer.kt            # Writes ScreenBuffer to terminal via Mordant
├── view/
│   ├── View.kt                      # Interface: render(buffer, x, y, width, height)
│   ├── BoardView.kt                 # Hex grid viewport, scrolling, cursor
│   ├── SidebarView.kt               # Unit info panel
│   └── StatusBarView.kt             # Phase, prompts, keybindings
├── hex/
│   ├── HexRenderer.kt               # HexCoordinates → screen chars with terrain colors
│   ├── UnitRenderer.kt              # Overlay unit letter + facing onto hex
│   └── HexHitTest.kt                # Screen coords → HexCoordinates (for mouse)
├── input/
│   ├── InputHandler.kt              # Routes keyboard/mouse events to actions
│   └── CursorState.kt               # Current cursor position, selection state
└── game/
    ├── GameLoop.kt                   # Phase progression, action execution
    └── PhaseController.kt            # Per-phase UI logic (what to show, what inputs valid)
```

Separation of concerns:
- **screen/** -- generic terminal rendering (reusable, no game knowledge)
- **view/** -- layout regions (know about screen, not about game rules)
- **hex/** -- hex-specific rendering and hit testing
- **input/** -- input event routing
- **game/** -- bridges TUI with tactical domain
