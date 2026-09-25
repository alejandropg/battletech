# Tenter agent instructions

This directory is a standalone Kotlin/JVM Gradle project. Work from this directory as the project root; do not depend on the enclosing BattleTech build or its files. The `tenter` module is the library and `tenter-example` is its external-consumer demonstration.

## Build and release

- Use `./gradlew build` for tests, ABI validation, and the example's negative compilation check.
- Use `./gradlew :tenter-example:packagedSmoke` to compile and run the example against the packaged library jar and cached external dependencies.
- Use `./gradlew :tenter:publishToMavenLocal` to install `io.archinaut:tenter:0.1.0-SNAPSHOT` for local consumers. Update the version in `gradle/libs.versions.toml` when releasing a new version.
- Review public API changes against `tenter/api/tenter.api`. Run `./gradlew :tenter:updateKotlinAbi` only after intentionally accepting a change.

## Architecture

- The library is independent. Its main sources may depend only on Kotlin, kotlinx coroutines, and Mordant. Keep application policies in consumers.
- Preserve the package layering and external dependency checks in `tenter`'s architecture tests; see `docs/architecture.md`.
- Mordant types deliberately appear in the public API. Keep `mordant` and `kotlinx-coroutines-core` as `api` dependencies.
- Keep the example buildable as an ordinary module and as an isolated packaged-jar consumer.

## Kotlin and tests

- Kotlin explicit API is enabled. Declare visibility on public and internal declarations.
- Use focused tests for behavior changes; keep rendering helpers in Tenter's test sources, not its published artifact.
