# Tenter architecture

Tenter is a JVM terminal UI toolkit over Mordant. Its public contract is described in the root README and tracked by `tenter/api/tenter.api`.

## Package layering

`palette`, `text`, and `input` are leaves. `screen` uses `palette` and `text`; `terminal` uses `input`, `palette`, and `screen`; `view` uses `input`, `palette`, `screen`, and `text`; `animation` uses `screen`, `view`, and `text`; `widget` and `panel` sit above `view` and `screen`. `tenter/src/test/kotlin/tenter/LayeringTest.kt` enforces the full allowed-dependency matrix.

`tenter/src/test/kotlin/tenter/ArchitectureTest.kt` prevents BattleTech imports and limits main-source imports to Tenter, Kotlin, Java, kotlinx, and Mordant. The same test keeps managed `Panel` mutations behind `PanelSet`.

The packages have distinct jobs: `palette` owns semantic colors; `text` owns display-cell metrics; `screen` owns buffers, canvas painting, and diff rendering; `view` owns prepared content and layout; `animation` owns finite frame descriptions and elapsed-time playback; `widget` owns reusable fragments; `panel` owns stateful panel composition; `input` owns key maps and pointer helpers; `terminal` owns terminal event flows and screen lifecycle.

## Public seams

Tenter adds primitives on top of Mordant instead of wrapping it. `TerminalEvent.Input`, `MouseInput.scrollDelta`, `terminalEvents`, and `KeyBinding` expose Mordant types directly. `KeyMap` matches `KeyboardEvent` values exactly as reported. Applications choose which platform spellings to bind and their context precedence.

`KeyMap` copies its context, layer, and group collections. At construction it rejects duplicate exact chords within a layer, invalid group references, ambiguous sectionless credits, and orphaned groups unless explicitly `bindingless`. Applications keep action identity and `InputAction.id` stable after constructing a map.

Prepared composition accepts `ContentView` children. `Padded.prepared` and `Bordered.prepared` provide intrinsic content, while their raw constructors remain allocated-canvas `View` decorators. Viewports re-engage following when either destination dimension changes.

`Panel` belongs to one screen lifetime. `PanelSet` alone mutates its state and publishes immutable completed-frame observations: settled offsets, geometry, and hit tests. It publishes new observations only after all panels paint successfully. Canvas writes and application callback side effects are not rolled back. Undeclared panel ids fail, and a border hit has no content coordinate.

Each panel's `Presentation` pairs prepared content with a width preference so one builder result drives both layout and painting. A `PanelLayout.Slot` exposes panel identity, outer and content rectangles, and a settled viewport observation. `PanelSet.panelAt` and `hitTest` read the completed frame; callers never reproduce padding or scroll arithmetic. Small screens clip slots to the canvas without publishing negative geometry. A hidden panel retains focus and queued scroll or recenter intent until its next drawable frame. `ViewportState.settled` is the single settled viewport read path.

`PanelSet.uniform` copies fixed-width panel ids and can reserve proportional slots for hidden panels. Visible minimized proportional panels are subtracted from that reservation; otherwise, visible proportional panels divide the available width. The common render operation has no mode-specific options.

`MouseInput.scrollDelta` recognizes only Mordant's explicit wheel flags and requires a positive caller step. Applications decide whether to support any compatibility fallback.
