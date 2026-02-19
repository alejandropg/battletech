# BattleTech Rules Engine

A multi-module Gradle project for BattleTech game rules implementation.

## Project Structure

```
battletech/
├── buildSrc/                       # Common build configuration
│   └── src/main/kotlin/
│       ├── battletech.kotlin-common.gradle.kts       # Shared Kotlin config
│       ├── battletech.kotlin-library.gradle.kts      # Library convention plugin
│       └── battletech.kotlin-application.gradle.kts  # Application convention plugin
├── gradle/
│   └── libs.versions.toml          # Version catalog (all versions defined here)
├── strategic/                      # Strategic rules library
│   └── src/
│       ├── main/kotlin/battletech/strategic/
│       └── test/kotlin/battletech/strategic/
├── tactical/                       # Tactical rules library
│   └── src/
│       ├── main/kotlin/battletech/tactical/
│       └── test/kotlin/battletech/tactical/
├── bt/                            # Main application (depends on strategic & tactical)
│   └── src/
│       ├── main/kotlin/battletech/
│       └── test/kotlin/battletech/
└── tui/                           # Terminal UI application
    └── src/
        └── main/kotlin/battletech/tui/
```

## TUI Application

The `tui/` module is a terminal UI for the game. It produces a single-file distributable via the [Shadow plugin](https://gradleup.com/shadow/).

```bash
# Run during development (requires Gradle)
./gradlew :tui:run

# Build a fat JAR (~7 MB, requires Java to run)
./gradlew :tui:shadowJar
java -jar tui/build/libs/tui.jar

# Build a self-executing binary (Unix/macOS)
./gradlew :tui:createExecutable
./tui/build/tui
```
