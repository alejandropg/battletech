# Build and packaging

buildSrc convention plugins, module dependency edges, and TUI shadow-jar packaging. Read this
when touching `buildSrc/`, a `build.gradle.kts`, module dependencies, or how root content
resources and the shadow jar get packaged.

## buildSrc convention plugins

Applied via `id("battletech.<name>")`:

- **`battletech.kotlin-common`** — base for every module: applies `kotlin("jvm")`, sets the JVM toolchain (JVM 21 when `CLAUDE_CODE` env var is set — Claude Cloud constraint — otherwise the catalog version), enables `explicitApi()`, configures JUnit Platform + test logging, wires standard test deps (JUnit BOM/bundle, MockK, AssertJ).
- **`battletech.kotlin-library`** — applies `kotlin-common`; used by `strategic`, `tactical`, and `network`.
- **`battletech.kotlin-application`** — applies `kotlin-common` + the `application` plugin; used by `bt` and `tui`.
- **`battletech.kotlin-serialization`** — applies the Kotlin serialization plugin; used by `tactical` and `network` (both need kotlinx-serialization for `GameState`/wire types).

## Module dependency edges

- `network → tactical`: `api(project(":tactical"))` in `network/build.gradle.kts` — deliberately transitive (not `implementation`). `network` re-exports `tactical` types (`GameCommand`, `GameEvent`, `PlayerGameState`, `LogEntry`, `TurnState`) directly as wire DTOs instead of redefining them, so consumers of `network` need `tactical`'s types on their compile classpath too.
- `bt → strategic`, `bt → tactical`: both `implementation(project(...))` in `bt/build.gradle.kts`.
- `tui → tactical`, `tui → network`: `implementation(project(...))` in `tui/build.gradle.kts`.
- `strategic` and `tactical` declare no `project(...)` dependencies on other modules.

## TUI packaging

`tui/build.gradle.kts` applies `alias(libs.plugins.shadow)` — `com.gradleup.shadow`, version `9.4.2` per `gradle/libs.versions.toml`.

Tactical's `processResources` copies the repository's root `map/`, `unit/`, and `mech/` directories into the tactical runtime resources under the same names. Because `tui` depends on `tactical`, Shadow JAR assembly includes the packaged maps, unit collections, and mech collections transitively; none is configured separately on `shadowJar`.

Themes are packaged the same way but one module closer to the jar: `tui/build.gradle.kts` configures its OWN `processResources` (tactical's block cannot reach it) to copy the repository's root `theme/` directory into `tui`'s runtime resources under `theme/`, so the six built-in `theme/*.json` files and `theme/index.json` land in the shadow jar directly rather than transitively. See `docs/color-themes.md` for the file format, and `docs/architecture.md`'s resource-loading section for the shared loader design.

`tasks.shadowJar` is configured with `archiveBaseName = "tui"`, `archiveClassifier = ""`, `archiveVersion = ""`, so the fat jar lands at exactly `tui/build/libs/tui.jar` (no `-all`/version suffix). `mergeServiceFiles()` is set to correctly merge `META-INF/services` entries from bundled dependencies.

The `createExecutable` task (group `distribution`) depends on `shadowJar` and prepends a POSIX shell stub — `#!/bin/sh\nexec java -jar "$0" "$@"\n` — to the shadow jar's bytes, writing the result to `build/tui` and marking it executable. A shell script prepended to a zip/jar is still a valid jar (the JVM's zip reader scans from the end of the file), so `build/tui` is simultaneously a runnable shell script and a runnable jar.
