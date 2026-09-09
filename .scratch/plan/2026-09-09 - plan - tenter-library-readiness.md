# Tenter library readiness: implementation plan

Date: 2026-09-09. Status: stage 02 complete; stage 03 is next.

## Objective and authorization

Prepare Tenter's interface for independent, reusable use before extracting it into a separate repository. The user approved the design review and subsequent plan corrections. This document records the resulting implementation decisions; the current work updates the plan only. Ease of use is judged through the independent consumer, with the toolkit owning the bookkeeping needed to use its features correctly.

When subsequently instructed to implement this plan, implement the stages below in order. Interface changes and migration of the existing TUI are part of the work. Publishing artifacts, creating a new repository, changing the license, committing, and deploying are not part of this plan.

## Execution protocol for Luna (high)

Use **twelve sequential stages** (00–11), normally one stage per implementation session. Resume a large stage across additional sessions when necessary. Execute stages in numerical order; every stage requires all preceding stages complete. Do not load every stage into one model context. Do not use subagents unless separately authorized. Each session reads:

1. Root `CLAUDE.md` (root `AGENTS.md` is a one-line pointer to it; reading `CLAUDE.md` covers both).
2. This master document, including the progress ledger.
3. Its own linked stage document.
4. Stage 00's findings and the most recent completed stage's handoff below, plus earlier handoffs explicitly referenced by its stage document; then only the source files needed for its task.
5. The `codebase-design` and `kotlin-style` skills; `kotlin-junit` for tests; `kotlin-gradle` if changing build configuration. Read `docs/architecture.md`; read `docs/build.md` when the stage touches build configuration. Read other repository docs only at their documented triggers.

Use LSP for symbols/references as requested by `CLAUDE.md`. If unavailable, state that limitation and inspect files directly; do not pretend reference coverage was exhaustive. Check `git status --short` and preserve unrelated changes. Source locations in the stage documents are starting points, not an exhaustive allowlist of necessary consumer migrations.

Finish each stage with a buildable project and relevant passing checks. Every stage that changes a public interface must migrate both `tui` and `tenter-example` and pass the example build and smoke test; it cannot defer fixing that consumer to stage 11. Add focused regression tests for the behavior being changed, not tests that duplicate private implementation. Do not delete useful behavioral tests because classes moved. Replace obsolete implementation-coupled tests once equivalent behavior is tested through the new interface.

If a stage is larger than expected, finish a coherent buildable slice, record the remaining substeps in its handoff, and resume that same stage in another session. Do not mark it complete based on partial compilation or skipped failing tests. Do not silently change an agreed behavioral decision to fit an implementation shortcut. Stages 03–05 form one refactor (prepared content → composition → consumer migration) with three explicit completion gates and may require extra sessions. Keep their temporary bridges until the assigned migration completes; stage 05 owns removal of `ContentExtent.Measured`, legacy `TextCursor.draw(View)`, and dual measurement overloads. Every additional bridge gets a named removal stage and exit test when introduced. Session boundaries never transfer or erase that obligation.

**Re-planning criterion.** Stop and report evidence if completion requires an unapproved behavior change, contradicts a fixed contract, or reveals that the chosen seam cannot satisfy the independent consumer within this plan. The explicitly listed Unicode, clipping, scrolling, and small-screen fixes are approved behavior changes. Expected stage 00 shortcomings (manual lifecycle wiring, missing defaults, current measurement limits) become findings with assigned stages and verification cases, not automatic blockers. Routine fixes and migrations within the specified contracts proceed normally. Never mark a stage complete by weakening its acceptance criteria.

## Design decisions shared by every stage

- **Module depth:** callers describe content, dispatch intent, and inspect results. The module owns glyph integrity, occupied size, scroll following, frame snapshots, panel state, and geometry conversion.
- **Seam:** retain `View.draw(Canvas)` for painting. Add one prepared-content seam for intrinsic layout, described in stage 03. Arbitrary painting cannot be measured reliably from its nonblank pixels.
- **Locality:** use the same geometry for drawing and hit-testing, the same glyph metrics for measuring and painting, and the same validation for every keymap consumer.
- **State ownership:** a stateful `Panel` owns its private `ViewportState`, current state, and restore state. `PanelSet` coordinates transitions and stores immutable observations of the completed frame. Multiple readers do not require shared mutable ownership or a second scroll offset. A panel instance belongs to one set.
- **Reveal contract:** content requests visibility; the toolkit selects and translates requests through composition. Pre-paint candidates stay internal. The settled viewport snapshot is the public authoritative result after rendering; callers never reconcile provisional and final targets.
- **Adapters:** direct Mordant types remain public. Do not introduce a parallel keyboard, mouse, terminal, coroutine, or renderer abstraction. Use Mordant's existing recorder for terminal tests.
- **SOLID/KISS:** enforce owned invariants; preserve behavioral substitution under composition; avoid universal layout strategies, application frameworks, or interfaces with only a hypothetical variation.
- **DRY:** share real rules, not merely similarly shaped loops. BattleTech's setup and game loops remain application-owned.
- **Simple design:** pass meaningful behavioral checks, express intent, remove duplicated knowledge, and keep the smallest set of useful concepts. A new type needs a stated caller responsibility it removes.
- No `battletech.*` dependency in Tenter. Keep `text` a leaf, `screen -> text`, `view -> screen/text/input`, and `panel -> view/screen`. One intentional addition is `terminal -> screen` for scoped screen lifecycle; document and test only that edge.
- Preserve existing game rules, hidden-information projections, network behavior, keyboard chords, animation timing, and ordinary-size screen layout. Changes to tiny-screen overflow, Unicode correctness, previously lost reveals, and previously truncated long content are intentional.
- Treat the current interface as pre-release. Temporary bridges may keep staged migration buildable, but do not maintain a permanent second interface for compatibility with this repository alone.

## Stage sequence

| Stage | Session responsibility | Completion gate |
|---|---|---|
| [00](<2026-09-09 - plan - tenter-library-readiness/00-consumer-spike.md>) | Minimal external consumer, built and run against the packaged jar | A second, out-of-repo-shaped caller compiles and runs against tenter as it stands today; findings recorded |
| [01](<2026-09-09 - plan - tenter-library-readiness/01-text-contract.md>) | Glyph segmentation and shared display-width rules | Text behavior consistent across truncation, wrapping, styling, and widgets |
| [02](<2026-09-09 - plan - tenter-library-readiness/02-canvas-and-frames.md>) | Atomic glyph clipping and renderer frame ownership | Valid buffers; resubmitting a mutated buffer repaints correctly |
| [03](<2026-09-09 - plan - tenter-library-readiness/03-prepared-content.md>) | Prepared content and recording text cursor | Exact occupied size without measurement painting; reveal resolved during one actual paint |
| [04](<2026-09-09 - plan - tenter-library-readiness/04-composition-and-scroll.md>) | Composition and prepared-content scrolling | Size/reveal preserved through decorators; no ceiling on new path |
| [05](<2026-09-09 - plan - tenter-library-readiness/05-consumer-migration.md>) | Migrate TUI content and remove measurement bridges | No production render-to-guess-height or 512-row measurement limit |
| [06](<2026-09-09 - plan - tenter-library-readiness/06-panel-ownership.md>) | Stateful panel encapsulation, presentation grouping, exclusive set attachment | One owner per mutable value; no public path to bypass set coordination |
| [07](<2026-09-09 - plan - tenter-library-readiness/07-geometry-and-mouse.md>) | Overflow, hit-testing, coordinate mapping, mouse policy | Geometry matches paint; normal clicks do not become toolkit scrolls |
| [08](<2026-09-09 - plan - tenter-library-readiness/08-keymap-validation.md>) | Reusable keymap validation | Structural errors fail at construction; app coexistence policy stays local |
| [09](<2026-09-09 - plan - tenter-library-readiness/09-terminal-lifecycle.md>) | Scoped screen lifecycle and flow/thread contracts | Cleanup on normal/exception paths; cancellation semantics tested |
| [10](<2026-09-09 - plan - tenter-library-readiness/10-palettes-and-glyphs.md>) | Default palette and configurable widget glyphs | New consumers need neither BattleTech themes nor Nerd Fonts |
| [11](<2026-09-09 - plan - tenter-library-readiness/11-external-consumer-and-baseline.md>) | Grow the stage 00 example into the full demo, docs, surface audit, compatibility baseline | Example compiles and runs as separate consumer; final checks pass |

Execute `00 → 01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09 → 10 → 11`. This is the session schedule, not a claim that every neighboring pair has a direct code dependency. In particular, stage 06 consumes prepared content and viewport state from 03–05; stage 10 consumes stage 01's glyph contract; and renderer/widget work overlaps across several stages. A single schedule keeps handoffs unambiguous. Resume or re-plan a blocked stage rather than skipping ahead. Stage 05 may use separate game-content and setup-content sessions if necessary; its document defines the split.

## Baseline evidence

The review reran `./gradlew :tenter:test --rerun`: 261 tests, zero failures/errors/skips. The worktree was clean before planning. Recheck when implementation starts; these are historical observations, not permission to skip verification.

Executable probes found:

- A child requesting reveal at row 10 retains it when drawn directly, but loses it in `Stack` and `Columns`.
- 600 rows in a 10-row `ContentExtent.Measured(512)` viewport produce max scroll 502 instead of 590 — zero-based rows 512–599 are unreachable by any offset the viewport will produce. This is the strongest single justification for stages 03–05.
- A text cursor writing one row and two blank rows measures as one row through painted-cell scanning.
- A two-column glyph can occupy the final column of a clipped canvas without a continuation slot.
- Two publicly mutable panels in one set can both become maximized.
- Screen width 10 with side width 20 produces main width -10 and side x -10.

The renderer's caller-mutation hazard, mouse click-to-scroll policy, and app-only keymap validation were also verified in source. Do not characterize existing intentional behavior as a newly discovered regression; replace it with the contracts in these stages.

## Shared validation and finish criteria

For each changed toolkit seam, test observable behavior through it. Composition tests must include real nested decorators, not only direct leaf rendering. Renderer tests use `TerminalRecorder`; a buffer assertion alone cannot detect terminal-column corruption. Application tests must verify input intent/state, not only unchanged screenshots.

At the end of each stage run its specified checks. After stage 00 the integration gate is `./gradlew :tenter:test :tui:test :tenter-example:build`; the example build must include its headless smoke test. This augments shorter commands in stage documents. Use focused tests during iteration. Reuse stage 00's isolated packaged-consumer check after stages 05, 09, and 11, and whenever packaging/dependency changes create a new concern. Do not repeatedly rerun unrelated suites without a change or unresolved failure. Stage 11 runs the broader build and compatibility checks.

For each changed external seam, keep one concise example-based usability check: can the consumer exercise it without copying toolkit validation, guessing dimensions, calculating border offsets, sharing mutable state, or round-tripping reveal bookkeeping? Treat newly required caller bookkeeping as a design finding before declaring the stage complete.

The final deliverable includes a user-facing Tenter README, supported text/thread/lifecycle/error contracts, a small separate consumer, a reviewed public surface baseline, and updated repository architecture/build documentation. Extraction and publication remain a subsequent task. Record the current JVM target and dependency versions from the catalog; do not lower the JVM requirement or upgrade Mordant as incidental cleanup.

## Progress ledger and handoff format

| Stage | Status | Handoff |
|---|---|---|
| 00 | Complete | Minimal packaged external consumer added; findings and validation below. |
| 01 | Complete | Shared grapheme segmentation/display-width contract implemented; handoff below. |
| 02 | Complete | Canvas glyph integrity and renderer-owned frames implemented; handoff below. |
| 03 | Pending | — |
| 04 | Pending | — |
| 05 | Pending | — |
| 06 | Pending | — |
| 07 | Pending | — |
| 08 | Pending | — |
| 09 | Pending | — |
| 10 | Pending | — |
| 11 | Pending | — |

### Stage 00 handoff — minimal external consumer

- Completed stage 00. Added `:tenter-example`, with public `tenterexample.main`, `runHeadlessSmoke`, and `SmokeResult`; no Tenter production declarations changed. The example uses public `Panel`/`PanelSet`, `View`/`HelpView`, `KeyMap`, `ScreenBuffer`/`Canvas`/`ScreenRenderer`, palette types, and `Animation`/`AnimationPlayback`/`GlyphGrid` only. The packaged smoke task compiles the example in a disposable separate Gradle process against `tenter.jar` plus the resolved runtime closure, rejecting repository-module paths.
- Files/layer edge: `settings.gradle.kts`, `tenter-example/build.gradle.kts`, `tenter-example/packaged-smoke/{build,settings}.gradle.kts`, and the example source/test files changed. The only new module edge is `tenter-example -> tenter`; Tenter's package layering and BattleTech-free allowlist are unchanged. No source imports or assets under `battletech.*`, `theme/`, `map/`, `unit/`, or `mech/` are used; no Nerd-Font glyph leaked into the consumer.
- Validation: `./gradlew :tenter-example:test --rerun` passed; `./gradlew :tenter:test :tui:test :tenter-example:build` passed; `./gradlew :tenter-example:packagedSmoke` passed in the separate wrapper process and printed `SmokeResult(renderedRows=40, helpContainsMovement=true, animationCompleted=true)`; `./gradlew :tenter-example:dependencies --configuration runtimeClasspath` passed; `git diff --check` passed. The packaged closure was Kotlin stdlib 2.4.10 (with annotations 23.0.0), Mordant 3.0.2 and its Mordant core/colormath/JNA/FFM/Graal-FFI transitives, kotlinx-coroutines-core 1.11.0 and its JVM/BOM transitives, plus JNA 5.14.0; no repository resource was required. The outer packaged task is configuration-cache clean; its one-shot standalone inner build disables its own cache while compiling/running in the disposable `build/packaged-smoke` directory.
- Findings assigned forward: the current manual `ScreenRenderer.clear()`/`cleanup()` `try/finally` lifecycle is usable but awkward and has no scoped `Terminal.withScreen`; stage 09 owns it and its cleanup regression. `ContentExtent.Measured` still has the known 512-row ceiling; stage 03 introduces prepared content, stage 04 composes/scrolls it, and stage 05 removes the bridge and verifies long content. Manual palette completeness is workable but expected to be replaced by stage 10's default palette. The animation package compiled and ran cleanly with no coupling or missing public declaration; no dedicated animation stage is needed.
- Test-fixture decision: `tenter/src/testFixtures/kotlin/tenter/view/ViewTestSupport.kt` remains repository-only support. The external consumer needed no fixture declarations, so no fixture publication is justified; preserve `tui`'s existing `testFixtures(project(":tenter"))` dependency.
- Temporary bridges: none introduced by stage 00. The expected current measurement/lifecycle limitations remain assigned above; the example does not add a second adapter or caller-managed reveal feedback loop.
- Next exact task: stage 01, shared glyph segmentation/display-width rules. Start with this handoff and `01-text-contract.md`, then inspect `tenter/text/{CellWidth,TextTruncation,TextWrap}.kt`, `screen/StyledText.kt`, `view/{TextCursor,Bordered}.kt`, `panel/VerticalTitleView.kt`, `widget/{ValueRow,PipTrack,Gauge,Checkbox}.kt`, and their tests. Preserve the packaged example while changing the public text seam.

After each implementation session append a concise handoff here and update the row. Include:

1. Completed stage/substeps and exact public declarations added/changed/removed.
2. Files changed and any changed layer edges.
3. Commands run, outcomes, and any remaining failures with evidence.
4. Temporary bridges still present, each assigned removal stage, and its removal verification case.
5. Decisions clarified from source evidence, including any departure from this plan and why.
6. The next exact task and its required starting files; stage 00 findings addressed or still assigned, including example build/packaged-check outcomes.

Keep handoffs compact. The source and stage documents own the detailed contracts; do not paste implementations or entire logs into the ledger.

### Stage 01 handoff — shared glyph and display-width contract

- Completed stage 01. Added the internal JDK `Pattern` `\\X` grapheme iterator with UTF-16 source offsets, sanitized drawable text, spacing-base selection, and a deterministic `0..2` effective width. Routed string measurement, truncation, and wrapping through complete cluster boundaries; preserved source text in returned slices. The existing codepoint width table remains the only width table; combining-spacing marks are zero width, standalone marks/terminal controls are non-drawable, and isolated surrogates become U+FFFD for measurement/painting. ZWJ, flags, keycaps, variation selectors, decomposed accents, CJK, and supplementary PUA are covered by regressions. `StyledText` now normalizes a cross-span cluster to its first spacing base's style without mutating supplied/built values.
- Updated `TextCursor` fixed-field clipping/alignment and `writeRow` placement, `Bordered` title/badge arithmetic, `VerticalTitleView` cluster rows, `ValueRow`, `Gauge`/`PipTrack` validation and display arithmetic, plus the affected TUI layout arithmetic in board/status/game-over/unit-status/record-sheet views. No new package-layer edge or build/dependency change; `tenter.panel` reaches the shared helper through its existing `tenter.view` edge because the layering matrix deliberately keeps `panel` from importing `text`.
- Validation passed: `./gradlew :tenter:test :tui:test :tenter-example:build --rerun`; `./gradlew :tenter-example:packagedSmoke --rerun` (separate packaged consumer, `SmokeResult(renderedRows=40, helpContainsMovement=true, animationCompleted=true)`); `git diff --check`. The JDK check was performed on the declared runtime, OpenJDK 25.0.2. The initial stale combining-mark characterization was retained and remains green; the new vertical-title path keeps complete clusters intact.
- Existing bridges remain assigned forward: general `Canvas.writeString` still has the pre-stage-02 combining-mark behavior (stage 02 owns atomic cluster lead/continuation paint and its end-to-end buffer/terminal-recorder regressions); `ContentExtent.Measured`'s 512-row ceiling remains assigned to stages 03–05, with stage 05 removing it and the legacy measurement bridges. No temporary bridge was introduced in stage 01.
- Decision clarified from source evidence: the agreed best-effort policy is maximum constituent codepoint width after control filtering and surrogate sanitization, clamped to `0..2`; therefore a flag pair is model width 1 and `👩‍💻` is model width 2. This is intentionally not a universal terminal emoji-width claim. The configured session had no LSP tool, so symbol/reference coverage used targeted source inspection and compiler/test feedback.
- Next exact task: stage 02, atomic glyph clipping and renderer frame ownership. Start with `02-canvas-and-frames.md`, then `tenter/screen/Canvas.kt`, `ScreenBuffer.kt`, `ScreenRenderer.kt`, their tests, and the new `text/TextClusters.kt`; preserve the stage-01 iterator/metric and add end-to-end decomposed/ZWJ/invalid-surrogate/control paint checks without reopening the width policy.

### Stage 02 handoff — atomic canvas glyphs and renderer-owned frames

- Completed stage 02. `ScreenBuffer` now owns private glyph lead/continuation storage and one replacement path; public `get` retains `char == ""` only as a continuation observation, while `set`, `Canvas.set`, `setFg`, `writeString`, and `blit` cannot create orphan continuations. Invalid multi-grapheme/control cells are rejected; empty continuation observations are treated as blank writes. Negative buffer dimensions are rejected and zero dimensions remain valid. `Canvas.region` and `markReveal` now use overflow-safe rectangle intersection, and wide clusters are atomically blanked when only a partial slot is writable. `Canvas.blit` snapshots source cells, preserves complete glyphs, blanks cropped halves, and handles overlapping self-blits from original source data.
- `ScreenRenderer` snapshots the submitted frame after successful output, so callers may reuse and mutate one `ScreenBuffer`; diff spans expand across old/new wide glyph footprints, and shrinking a frame clears stale terminal content before the repaint. Removed the remaining manual continuation writes from vertical text. The packaged smoke task now passes `--rerun-tasks` to its disposable inner build so a cleared output directory cannot be mistaken for a valid cached compile.
- Files changed: `tenter/screen/{Canvas,ScreenBuffer,ScreenRenderer}.kt`, `tenter/view/TextCursor.kt`, the bounded TUI overlay comment in `tui/view/Workspace.kt`, focused screen tests, the animation frame fixture migration, and `tenter-example/build.gradle.kts`. No new production dependency or package-layer edge; `screen` continues to use the existing `screen → text` edge. The internal `StoredCell` is not part of the public surface.
- Validation passed: `./gradlew :tenter:test :tui:test :tenter-example:build --rerun` (Tenter 292 tests and TUI green); `./gradlew :tenter-example:packagedSmoke --rerun` passed against only `tenter.jar` and its resolved runtime closure with `SmokeResult(renderedRows=40, helpContainsMovement=true, animationCompleted=true)`; `git diff --check`. The stage-specific screen regressions cover wide glyph clipping/replacement/style output through `TerminalRecorder`, invalid cells, grapheme writes, region/reveal intersection, overlap blits, reusable buffers, and resize cleanup.
- Decision clarified from source evidence: every current `Canvas.region` consumer either supplies a non-negative bounded slot, uses a guarded overlay, or deliberately benefits from intersection at a small/partial canvas; no consumer required the old negative-origin shrink behavior. The existing public continuation observation is preserved for compatibility, but it is no longer a valid stored write. No temporary stage-02 bridge remains; `ContentExtent.Measured` and immediate measurement/composition paths remain assigned to stages 03–05.
- Next exact task: stage 03, prepared content with exact occupied size. Start with `03-prepared-content.md`, `tenter/view/Viewport.kt`, `ContentExtent`/`View`/`TextCursor`, the current composition decorators and their tests; introduce `ContentView`/`ContentLayout` beside the immediate path without migrating every consumer yet. Preserve the stage-02 glyph storage, clipping, and renderer snapshot contracts.
