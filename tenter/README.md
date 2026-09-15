# Tenter

Tenter is a JVM terminal-UI toolkit built on Mordant. Callers describe prepared content, dispatch
input intent, and inspect completed-frame observations; Tenter owns glyph integrity, layout size,
scroll following, panel geometry, and terminal-scope cleanup.

## First run

This complete entry point paints a greeting and waits for `q`. It is the compiled
[HelloMain.kt](../tenter-example/src/main/kotlin/tenterexample/hello/HelloMain.kt) example.

```kotlin
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import tenter.screen.Canvas
import tenter.palette.DefaultRolePalette
import tenter.screen.ScreenBuffer
import tenter.terminal.TerminalEvent
import tenter.terminal.inputEvents
import tenter.terminal.withScreen
import tenter.view.contentView

public fun main() {
    val terminal = Terminal()
    runHello(terminal, terminal.inputEvents(MouseTracking.Normal) { it.key == "q" })
}

/** The application owns the loop; tests can supply input without acquiring a real terminal. */
public fun runHello(terminal: Terminal, events: Flow<TerminalEvent>) {
    val greeting = contentView { cursor -> cursor.writeLine("Hello from Tenter — press q to quit") }
    terminal.withScreen(DefaultRolePalette) { renderer ->
        val size = terminal.updateSize()
        val buffer = ScreenBuffer(size.width, size.height)
        greeting.draw(Canvas.of(buffer))
        renderer.render(buffer)
        runBlocking {
            events.takeWhile { it !is TerminalEvent.Quit }.collect { }
        }
    }
}
```

Build and launch the [independent consumer demo](../tenter-example) from a real terminal:

```sh
./gradlew :tenter-example:installDist
tenter-example/build/install/tenter-example/bin/tenter-example
# Minimal greeting instead of the full demo:
tenter-example/build/install/tenter-example/bin/tenter-example --hello
```

Gradle's `run` task does not supply an interactive terminal. The headless packaged check is
`./gradlew :tenter-example:packagedSmoke`. For a terminal smoke check, use arrows to move, space
to toggle, `m` to cycle HELP's size, resize the terminal, and press `q` to quit; the original
screen and cursor should be restored.

## Content and layout

`View.draw(Canvas)` is the painting seam for content whose destination is already known.
`ContentView` adds `layout(availableWidth): ContentLayout`, so intrinsic content can report its
exact occupied dimensions without being painted into a guessed measurement canvas. Use
`contentView { ... }` for flowing text, `Stack`/`Columns` for prepared composition, and
`fixedContent(width, height, view)` when a raw `View` has explicit dimensions. Prepared layouts
retain their full logical content for scrolling, so very long content has a proportional memory
cost; there is no hidden row ceiling.

Recorded text and placements are snapshots, but raw views and role objects are retained, not
deep-copied. `fixedContent` never paints to measure; it invokes its original view on each paint.
Capture stable frame data in raw views when repeatable output is required.

`Stack` and `Columns.Child` require `ContentView` children. Use `Padded.prepared(insets, content)`
and `Bordered.prepared(content, ...)` for intrinsic padding and borders. The `Padded` and
`Bordered` constructors accept raw `View` painting into an already allocated canvas; those raw
decorators do not promise intrinsic dimensions. `Columns.gutter` separates columns within a band;
wrapped bands keep one blank row between them.

`ContentLayout` carries reveal requests through nested decorators. A content view requests
visibility; the owning viewport resolves it during the one actual paint and publishes the settled
`ScrollState`. Callers do not copy a provisional reveal or feed an offset back into the next frame.

For scrolling without panels, keep one `ViewportState` and draw `Viewport(content, state)` each
frame. Send scroll commands to the state, and read `state.settled` — the one place a settled frame
is published — as an observation; the views themselves expose no second read path. To handle
resize, merge `terminal.resizeEvents()` into your input flow, update the size, and repaint from the
same application execution context. The full demo shows this progression.

## Stateful panels

`Panel` owns one private viewport and its panel state for one screen lifetime. `PanelSet.uniform`
and `PanelSet.mainAndSides` attach each panel exclusively; a panel instance cannot be reused in a
second set. Use a factory when two independent sets need the same declaration shape. Mutate focus,
panel state, and scrolling through `PanelSet`. The returned `PanelLayout` and `PanelHit` are
immutable observations of the completed frame, including outer rectangles, viewport rectangles,
settled offsets, and optional content coordinates. Border, padding, and scroll offsets are not
caller arithmetic. Normal clicks are interpreted by the host application from `hitTest`; wheel
events can scroll the hit panel through `MouseInput.scrollDelta`.

A set's panel list is fixed at construction, so every id-addressed operation — `focus`,
`focusOrCycle`, `scroll`, `requestRecenter`, `stateOf` — rejects an id the set does not
declare with `IllegalArgumentException` rather than failing silently. Their observations are total:
`focused` always names a panel. Read offsets from `PanelLayout.Slot.scroll` in the returned frame,
not through a second query on the set. `panelAt` and `hitTest` still return null, because a
*coordinate* can genuinely land on nothing; `PanelLayout.Slot.scroll` is null for a slot that
produced no drawable frame, even if its panel drew an earlier frame successfully.

Configure uniform layout once with `PanelSet.uniform(panels, reservedColumns, fixedWidthPanels)`.
By default, visible proportional panels share remaining space. A nonnull `reservedColumns` keeps
slots for hidden proportional panels; visible minimized proportional panels reclaim their slot.
Fixed panels form a trailing group. Small screens clip allocations without negative geometry.
Use `Panel.Presentation.allocated(content)` for main, proportional, and maximized states;
use `Panel.Presentation.fixedWidth(content, width)` for side, minimized, and configured fixed
states. Fixed widths must be positive and include chrome. Their preference is ignored when a
declaration is reused in an allocated slot; allocated presentations in fixed slots fail clearly.
Empty visibility is a valid uniform frame; hidden panels retain their state, and the set keeps the
focus it had so it returns where the user left it once panels become visible again.
Focused commands still target that hidden panel; without a displayed viewport, paging queues one
row per direction. Recenter requests are queued independently per panel until its next drawable
frame, surviving hidden and zero-sized frames as well as failed paints.
A set does not publish its own panel declarations back — a panel is addressed by id, and every
frame observation is a rectangle, not a `Panel`. Published layout lists are unmodifiable.
Managed viewport state and hit geometry publish
together after all panels paint successfully. Failure retains previous offsets and geometry plus
queued scrolling/recenter intent for retry. Canvas writes and callback side effects are not rolled
back: discard a failed frame's canvas. A change to either viewport dimension
re-engages reveal following; unchanged dimensions and targets preserve manual scrolling.

## Text and widgets

Text metrics use complete grapheme clusters and the shared display-width policy. Combining marks,
variation selectors, ZWJ sequences, flags, keycaps, decomposed accents, CJK, supplementary
characters, truncation, wrapping, styled spans, and widgets use the same segmentation rules.
Painting clips a wide glyph atomically: a partial glyph never leaves a continuation cell behind.
Invalid standalone controls or isolated surrogates are rejected or sanitized according to the text
operation; dimensions and glyph inputs with invalid or negative values fail early.

`CheckboxGlyphs.DEFAULT` is a plain Unicode set, `ASCII` is the most conservative choice, and
`NERD_FONT` is opt-in. `SelectableRow`, `Checkbox`, and `Gauge` accept their styling/glyph choices;
Tenter does not require Nerd Fonts.

`DefaultRolePalette` works without a domain theme file. `MapRolePalette` supports validated custom
palettes. Every toolkit `ChromeRole` must be supplied by a map palette. Every toolkit role
is validated eagerly. Palette colors
must match the palette's declared `AnsiLevel`, which is an authoring tier: `AnsiLevel.NONE` is not
one and `MapRolePalette` rejects it. Fixed colors are
resolved by the renderer in both foreground and background channels. When the *terminal* reports
`AnsiLevel.NONE`, the renderer suppresses all SGR output on its own, whatever the palette, while
glyphs and layout remain usable.

## Input, lifecycle, and animation

`KeyMap` matches Mordant `KeyboardEvent` values exactly as reported; it performs no chord folding.
Construction rejects duplicate exact chords, blank or duplicate hint ids, invalid references,
ambiguous sectionless credits, and orphaned groups unless they are explicitly `bindingless`.
Applications retain their own context precedence and platform coexistence policy.

`Terminal.withScreen` is a synchronous scope for one `ScreenRenderer`. It enters the alternate
screen and hides the cursor only for interactive terminals, then restores both on normal return,
entry failure, block failure, and cleanup failure (cleanup is suppressed onto an original failure).
`Terminal.inputEvents` and `resizeEvents` are cold flows. Input collection owns raw-mode acquisition
and release, only one input reader should be active per terminal, and cancellation is observed by
bounded polling. Rendering, panel state, and size observation must be confined by the caller to one
execution context; Tenter does not create an event-loop abstraction.

The optional `tenter.animation` package provides finite `Animation` descriptions,
`AnimationPlayback`, `AnimationSize`, and `GlyphGrid`. Playback is pure elapsed-time sampling.
Applications own the clock, placement, rendering, and cancellation policy.

## Runtime and testing contract

Tenter targets JVM 25 with Kotlin 2.4.10. Its public surface exposes Mordant 3.0.2 types and
`kotlinx-coroutines-core` 1.11.0 types where they are part of the interface; it is not
multiplatform. Tests should exercise public views, buffers, `PanelSet` observations, and
Mordant's `TerminalRecorder`. `ViewTestSupport` is repository-only test support used by the TUI;
it is not an independently supported or published fixture artifact.

The supported contract is the public API tracked in `api/tenter.api`: content/painting, managed
panels and observations, text/widgets, palettes, input, terminal scopes, and finite animations.
Internal chrome spacing is deliberately not public; use completed-frame hit tests and rectangles.
No public transaction manager, application event loop, theme hot-swap, or deep-copy mechanism is
promised. This is a pre-1.0 API; downstream consumers should pin the version and review API changes
when upgrading.
