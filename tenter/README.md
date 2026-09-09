# Tenter

Tenter is a JVM terminal-UI toolkit built on Mordant. Callers describe prepared content, dispatch
input intent, and inspect completed-frame observations; Tenter owns glyph integrity, layout size,
scroll following, panel geometry, and terminal-scope cleanup.

## Small example

```kotlin
import com.github.ajalt.mordant.terminal.Terminal
import tenter.panel.Panel
import tenter.panel.PanelId
import tenter.panel.PanelSet
import tenter.screen.Canvas
import tenter.screen.DefaultRolePalette
import tenter.screen.ScreenBuffer
import tenter.view.contentView
import tenter.terminal.withScreen

enum class Id : PanelId { MAIN }

val panel = Panel<Id, Unit>(
    id = Id.MAIN,
    title = "ITEMS",
    normal = {
        Panel.Presentation(
            content = contentView { cursor -> cursor.writeLine("Hello from Tenter") },
            width = 0,
        )
    },
)
val panels = PanelSet.uniform(listOf(panel))
val buffer = ScreenBuffer(40, 8)
panels.render(Canvas.of(buffer), Unit, setOf(Id.MAIN), reservedTop = 0)

Terminal().withScreen(DefaultRolePalette) { renderer ->
    renderer.render(buffer)
}
```

The [independent consumer demo](../tenter-example) is runnable with
`./gradlew :tenter-example:run` from a real TTY. Its headless packaged check is
`./gradlew :tenter-example:packagedSmoke`.

## Content and layout

`View.draw(Canvas)` is the painting seam for content whose destination is already known.
`ContentView` adds `layout(availableWidth): ContentLayout`, so intrinsic content can report its
exact occupied dimensions without being painted into a guessed measurement canvas. Use
`contentView { ... }` for flowing text, `Stack`/`Columns` for prepared composition, and
`fixedContent(width, height, view)` when a raw `View` has explicit dimensions. Prepared layouts
retain their full logical content for scrolling, so very long content has a proportional memory
cost; there is no hidden row ceiling.

`ContentLayout` carries reveal requests through nested decorators. A content view requests
visibility; the owning viewport resolves it during the one actual paint and publishes the settled
`ScrollState`. Callers do not copy a provisional reveal or feed an offset back into the next frame.

`Panel` owns one private viewport and its panel state for one screen lifetime. `PanelSet.uniform`
and `PanelSet.mainAndSides` attach each panel exclusively; a panel instance cannot be reused in a
second set. Use a factory when two independent sets need the same declaration shape. Mutate focus,
panel state, and scrolling through `PanelSet`. The returned `PanelLayout` and `PanelHit` are
immutable observations of the completed frame, including outer rectangles, viewport rectangles,
settled offsets, and optional content coordinates. Border, padding, and scroll offsets are not
caller arithmetic. Normal clicks are interpreted by the host application from `hitTest`; wheel
events can scroll the hit panel through `MouseInput.scrollDelta`.

Uniform layouts may have proportional and fixed columns, and small screens clip allocations in
declaration order without publishing negative geometry. Empty visibility is a valid uniform frame;
focus is then `null` while hidden panel state is retained.

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

`DefaultRolePalette` works without a domain theme file. `RolePalette.withOverrides` and
`MapRolePalette` support validated custom palettes. Every toolkit `ChromeRole` must be supplied by
a map palette, and palette colors must match the terminal's declared `AnsiLevel`. Fixed colors are
resolved by the renderer in both foreground and background channels. At `AnsiLevel.NONE`, all SGR
output is suppressed while glyphs and layout remain usable.

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
